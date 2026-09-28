import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';

const BASE = 'http://127.0.0.1:18080';
const INBOX = 'http://127.0.0.1:18025';
const USERS = Number(__ENV.USERS || 10);
const RUN_ID = __ENV.RUN_ID || '';
if (![10, 100].includes(USERS)) throw new Error('USERS must be 10 or 100');
if (!/^[A-Za-z0-9_]{1,32}$/.test(RUN_ID)) throw new Error('Use ip-load.py to supply a unique RUN_ID');
const PASSWORD = 'LocalIpLoadPassword123';
const WARMUP_SECONDS = USERS === 10 ? 60 : 180;
const LOAD_SECONDS = 120;
const PAUSE_SECONDS = USERS / 10;
const MIN_SAMPLES_PER_USER = Math.ceil(LOAD_SECONDS / PAUSE_SECONDS * 0.8);

const ready = new Counter('ip_accounts_ready');
const completed = new Counter('ip_users_completed');
const closed = new Counter('ip_sessions_closed');
const correctProfiles = new Rate('profile_correct');
const throttled = new Rate('profile_throttled');
const samples = new Counter('profile_samples');

export const options = {
    maxRedirects: 0,
    setupTimeout: '10s',
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        separate_clients: {
            executor: 'per-vu-iterations',
            vus: USERS,
            iterations: 1,
            maxDuration: `${WARMUP_SECONDS + LOAD_SECONDS + 20}s`,
            gracefulStop: '10s',
        },
    },
    thresholds: {
        'http_req_failed{phase:load}': ['rate==0'],
        'http_req_duration{phase:load}': ['p(95)<500', 'p(99)<100'],
        profile_correct: ['rate==1'],
        profile_throttled: ['rate==0'],
        profile_samples: [`count>=${USERS * MIN_SAMPLES_PER_USER}`],
        ip_accounts_ready: [`count==${USERS}`],
        ip_users_completed: [`count==${USERS}`],
        ip_sessions_closed: [`count==${USERS}`],
    },
};

function json(response) {
    try { return response.json(); } catch (_) { return null; }
}

function requireStatus(response, expected, label) {
    if (response.status !== expected) {
        // Do not log request/response bodies, passwords, or tokens.
        throw new Error(`${label}: expected HTTP ${expected}, got ${response.status}`);
    }
    return response;
}

function params(phase, name, extraHeaders = {}) {
    return {
        headers: {
            'User-Agent': `ums-ip-load/${RUN_ID}/vu/${exec.vu.idInTest}`,
            ...extraHeaders,
        },
        tags: { phase, name },
        timeout: phase === 'load' ? '5s' : '10s',
    };
}

function post(path, body, expected) {
    return requireStatus(http.post(`${BASE}${path}`, JSON.stringify(body),
        params('prepare', `POST ${path}`, { 'Content-Type': 'application/json' })), expected, path);
}

function waitUntil(timestamp) {
    const delay = (timestamp - Date.now()) / 1000;
    if (delay > 0) sleep(delay);
}

function verificationToken(email) {
    const deadline = Date.now() + 20000;
    while (Date.now() < deadline) {
        const response = requireStatus(http.get(`${INBOX}/api/v1/messages?start=0&limit=200`,
            params('prepare', 'Mailpit list')), 200, 'Mailpit inbox');
        const page = json(response);
        if (!page || !Array.isArray(page.messages)) throw new Error('Unexpected Mailpit inbox response');
        const item = page.messages.find(message => message.Subject === 'Verify your email address' &&
            (message.To || []).some(recipient => recipient.Address === email));
        if (item) {
            const detail = json(requireStatus(http.get(`${INBOX}/api/v1/message/${item.ID}`,
                params('prepare', 'Mailpit message')), 200, 'Verification email'));
            const match = detail && typeof detail.Text === 'string'
                ? detail.Text.match(/https:\/\/frontend\.example\/verify\?token=([^\s]+)/) : null;
            if (!match) throw new Error('Verification link missing from captured email');
            return decodeURIComponent(match[1]);
        }
        sleep(0.25);
    }
    throw new Error('Verification email did not arrive within 20 seconds');
}

function matchesProfile(body, account) {
    return body !== null && typeof body === 'object' &&
        body.id === account.id && body.username === account.username &&
        body.email === account.email && body.role === 'USER' &&
        !('password' in body) && !('passwordHash' in body);
}

export function setup() {
    // Shared setup supplies scheduling information only. Each VU creates its own account.
    const start = Date.now();
    console.log(`RUN_ID=${RUN_ID}; USERS=${USERS}; account enrollment starts one user per second.`);
    console.log(`Profile measurement starts after ${WARMUP_SECONDS}s and lasts ${LOAD_SECONDS}s.`);
    return {
        start,
        loadStart: start + WARMUP_SECONDS * 1000,
        loadEnd: start + (WARMUP_SECONDS + LOAD_SECONDS) * 1000,
        phonePrefix: String(start % 100000000000).padStart(11, '0'),
    };
}

export default function (schedule) {
    const vu = exec.vu.idInTest;
    if (vu < 1 || vu > USERS) exec.test.abort('Use one local process with the supplied runner');
    let account = null;
    try {
        // Avoid starting 100 Argon2 signup/login operations at the exact same instant.
        waitUntil(schedule.start + (vu - 1) * 1000);
        const username = `ipload_${RUN_ID}_${vu}`;
        const email = `${username}@example.com`;
        const signup = json(post('/api/auth/signup', {
            username,
            email,
            phoneNumber: `+${schedule.phonePrefix}${String(vu).padStart(3, '0')}`,
            password: PASSWORD,
        }, 201));
        if (!signup || !signup.id) throw new Error('Signup response is missing the user ID');
        post('/api/auth/verify-email', { token: verificationToken(email) }, 204);
        const loginResponse = post('/api/auth/login', { identifier: email, password: PASSWORD }, 200);
        const login = json(loginResponse);
        const cookie = loginResponse.cookies.refreshToken;
        if (!login || !login.accessToken || login.expiresIn !== 900 || !cookie || !cookie[0]?.value) {
            throw new Error('Login needs accessToken, expiresIn=900, and the refreshToken cookie');
        }
        account = { id: signup.id, username, email, access: login.accessToken, refresh: cookie[0].value };

        const warm = requireStatus(http.get(`${BASE}/api/users/me`,
            params('warmup', 'Warm profile', { Authorization: `Bearer ${account.access}` })), 200, 'Warm profile');
        if (!matchesProfile(json(warm), account)) throw new Error('Warm-up returned an incorrect profile');
        if (Date.now() >= schedule.loadStart) throw new Error('Account preparation missed the measurement start');
        ready.add(1);

        // Spread requests across the pacing interval instead of creating synchronized bursts.
        waitUntil(schedule.loadStart + (vu - 1) / USERS * PAUSE_SECONDS * 1000);
        let count = 0;
        while (Date.now() < schedule.loadEnd) {
            const response = http.get(`${BASE}/api/users/me`,
                params('load', 'GET /api/users/me', { Authorization: `Bearer ${account.access}` }));
            const correct = response.status === 200 && matchesProfile(json(response), account);
            check(response, {
                'profile returns 200': r => r.status === 200,
                'profile belongs to this user': () => correct,
            });
            correctProfiles.add(correct);
            throttled.add(response.status === 429);
            samples.add(1);
            count += 1;
            const remainingSeconds = (schedule.loadEnd - Date.now()) / 1000;
            if (remainingSeconds > 0) sleep(Math.min(PAUSE_SECONDS, remainingSeconds));
        }
        if (count < MIN_SAMPLES_PER_USER) throw new Error('Too few measured requests for this user');
        completed.add(1);
    } catch (error) {
        exec.test.abort(`VU ${vu}: ${error.message}`);
    } finally {
        if (account) {
            const logoutParams = params('logout', 'POST /api/auth/logout');
            logoutParams.cookies = { refreshToken: { value: account.refresh, replace: true } };
            const response = http.post(`${BASE}/api/auth/logout`, null, logoutParams);
            if (response.status === 204) closed.add(1);
            else console.error(`VU ${vu}: logout returned HTTP ${response.status}`);
        }
    }
}
