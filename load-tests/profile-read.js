import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';

//Intentionally fixed to the separate local application and local mail capture,

const BASE_URL = 'http://127.0.0.1:18080';
const MAILPIT_URL = 'http://127.0.0.1:18025';
const MODE = __ENV.MODE || 'smoke';
if (!['smoke', 'baseline', 'baseline10'].includes(MODE)) {
    throw new Error('MODE must be smoke, baseline or baseline10');
}

const ACCOUNT_COUNT = MODE === 'smoke' ? 1 : MODE === 'baseline10' ? 10 : 5;
const PASSWORD = 'LoadTestPassword123';

const profileCorrect = new Rate('profile_correct');
const throttled = new Rate('profile_throttled');
const samples = new Counter('profile_samples');

export const options = {
    setupTimeout: '4m',
    teardownTimeout: '30s',
    maxRedirects: 9,
    summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
    scenarios: MODE === 'smoke' ? {
        profile: {executor: 'constant-vus', vus: 1, duration: '30s', gracefulStop: '10s'}
    }
    : {
        profile: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 1 },
                { duration: '30s', target: MODE === 'baseline10' ? 5 : 3 },
                { duration: '30s', target: ACCOUNT_COUNT },
                { duration: '2m', target: ACCOUNT_COUNT },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '5s',
            gracefulStop: '10s'
        }
        },
    thresholds: {
        //setup, email polling, cache warm-up, and logout are excluded by this tag.
        'http_req_duration{phase:load}': ['p(95)<500', 'p(99)<100'],
        'http_req_failed{phase:load}': ['rate==0'],
        profile_correct: ['rate==1'],
        profile_throttled: ['rate==0'],
        profile_samples: ['count>=10']
    }
}

function json(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function expectStatus(response, status, operation) {
    if (response.status !== status) {
        const body = json(response);
        throw new Error(`${operation}: expected HTTP ${status}, received ${response.status};` +
        `${body && body.title ? body.title : 'check application logs'}`
        );
    }
    return response;
}

function setupPost(path, body, expectedStatus) {
    const params = {
        headers: { 'Content-Type': 'application/json' },
        tags: { phase: 'setup', name: `POST ${path}` },
        timeout: '10s',
    };
    // A previous run may have used the five-per-minute signup/login allowance.
    let response = http.post(`${BASE_URL}${path}`, JSON.stringify(body), params);
    if (response.status === 429) {
        console.log(`Setup reached the rate limit for ${path}; waiting 61 seconds once.`);
        sleep(61);
        response = http.post(`${BASE_URL}${path}`, JSON.stringify(body), params);
    }
    return expectStatus(response, expectedStatus, `Setup ${path}`);
}

function verificationToken(email) {
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
        const response = http.get(`${MAILPIT_URL}/api/v1/messages?start=0&limit=100`, {
            tags: { phase: 'setup', name: 'Mailpit list' }, timeout: '5s',
        });
        expectStatus(response, 200, 'Read Mailpit inbox');
        const page = json(response);
        if (!page || !Array.isArray(page.messages)) throw new Error('Unexpected Mailpit inbox response');
        const message = page.messages.find((item) =>
            item.Subject === 'Verify your email address' &&
            (item.To || []).some((recipient) => recipient.Address === email));
        if (message) {
            const detailResponse = http.get(`${MAILPIT_URL}/api/v1/message/${message.ID}`, {
                tags: { phase: 'setup', name: 'Mailpit message' }, timeout: '5s',
            });
            expectStatus(detailResponse, 200, 'Read verification email');
            const detail = json(detailResponse);
            const match = detail && typeof detail.Text === 'string'
                ? detail.Text.match(/https:\/\/frontend\.example\/verify\?token=([^\s]+)/)
                : null;
            if (!match) throw new Error('Verification email has no expected frontend.example link');
            return decodeURIComponent(match[1]);
        }
        sleep(0.25);
    }
    throw new Error(`No verification email received for ${email} within 15 seconds`);
}

export function setup() {
    const runId = `${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
    const accounts = [];
    for (let i = 0; i < ACCOUNT_COUNT; i += 1) {
        const username = `load_${runId}_${i}`;
        const email = `${username}@example.com`;
        // Thirteen timestamp digits plus a one-digit account index stays within validation.
        const phoneNumber = `+${runId.split('_')[0]}${i}`;
        const signup = json(setupPost('/api/auth/signup', {
            username, email, phoneNumber, password: PASSWORD,
        }, 201));
        if (!signup || !signup.id) throw new Error('Signup response is missing the user ID');

        setupPost('/api/auth/verify-email', { token: verificationToken(email) }, 204);
        const response = setupPost('/api/auth/login', { identifier: email, password: PASSWORD }, 200);
        const login = json(response);
        const cookie = response.cookies.refreshToken;
        if (!login || !login.accessToken || login.expiresIn !== 900 ||
            !cookie || !cookie.length || !cookie[0].value) {
            throw new Error('Login must provide accessToken, expiresIn=900, and a refreshToken cookie');
        }
        const account = { id: signup.id, username, email,
            access: login.accessToken, refresh: cookie[0].value };
        accounts.push(account);

        const warmup = http.get(`${BASE_URL}/api/users/me`, {
            headers: { Authorization: `Bearer ${account.access}` },
            tags: { phase: 'setup', name: 'Warm profile cache' }, timeout: '5s',
        });
        expectStatus(warmup, 200, 'Warm profile cache');
        if (json(warmup)?.id !== account.id) throw new Error('Warm-up returned the wrong user');
    }
    console.log(`Prepared ${accounts.length} verified accounts; starting ${MODE} measurement.`);
    return { accounts };
}

export default function (data) {
    const account = data.accounts[exec.vu.idInTest - 1];
    if (!account) exec.test.abort('No account for this VU; use the supplied single local scenario');
    const response = http.get(`${BASE_URL}/api/users/me`, {
        headers: { Authorization: `Bearer ${account.access}` },
        tags: { phase: 'load', name: 'GET /api/users/me' }, timeout: '5s',
    });
    const body = json(response);
    const correct = response.status === 200 && body !== null &&
        body.id === account.id && body.username === account.username && body.email === account.email &&
        body.role === 'USER' && !('passwordHash' in body) && !('password' in body);
    check(response, {
        'profile returns 200': (r) => r.status === 200,
        'profile belongs to this user': () => correct,
    });
    profileCorrect.add(correct);
    throttled.add(response.status === 429);
    samples.add(1);
    // At most about one request/second/user: below the 100/minute/user allowance.
    sleep(1);
}

export function teardown(data) {
    for (const account of data.accounts) {
        const response = http.post(`${BASE_URL}/api/auth/logout`, null, {
            cookies: { refreshToken: { value: account.refresh, replace: true } },
            tags: { phase: 'teardown', name: 'POST /api/auth/logout' }, timeout: '5s',
        });
        if (response.status !== 204) console.warn(`Logout returned ${response.status} for a test session`);
    }
    // Retain synthetic users in the disposable database; no destructive cleanup here.
}