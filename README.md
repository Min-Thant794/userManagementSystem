# User Management System API

A Java Spring Boot REST API for authentication, account management, and administrator operations. It powers a React application with an administrator workspace and a personal account workspace.

The project includes email verification, Google sign-in, JWT authentication with refresh-token rotation, password recovery, profile photos, session management, role-based access, and audit logs.

| Resource            | Link                                                                                         |
| ------------------- | -------------------------------------------------------------------------------------------- |
| Web application     | [Open UMS](https://user-management-system-frontend-mocha.vercel.app/)                        |
| API deployment      | [Render backend](https://usermanagementsystem-api.onrender.com/)                             |
| API documentation   | [Swagger UI](https://usermanagementsystem-api.onrender.com/swagger-ui/index.html)            |
| Frontend repository | [userManagementSystemFrontend](https://github.com/Min-Thant794/userManagementSystemFrontend) |

This is a personal learning and portfolio project. Deployment availability depends on its hosting plan; no public administrator credentials are provided.

## Contents

- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Local development](#local-development)
- [Configuration profiles](#configuration-profiles)
- [API reference](#api-reference)
- [Authentication behavior](#authentication-behavior)
- [Database and caching](#database-and-caching)
- [Testing](#testing)
- [Deploy to Render](#deploy-to-render)
- [Google OAuth configuration](#google-oauth-configuration)
- [Troubleshooting](#troubleshooting)
- [Project background](#project-background)

## Features

| Area             | Implemented capabilities                                                                                        |
| ---------------- | --------------------------------------------------------------------------------------------------------------- |
| Registration     | Username, email, phone, password, email verification, resend verification                                       |
| Authentication   | Username or email login, Google OAuth2/OpenID Connect, access JWTs, refresh-token rotation and reuse detection  |
| Account recovery | Forgot password, token-based password reset, failed-login lockout                                               |
| Profile          | Read/update contact details, complete an OAuth profile, verify a pending email change                           |
| Photos           | Upload, replace, and remove Cloudinary profile images                                                           |
| Passwords        | Change a local password or set the first password for an OAuth account                                          |
| Sessions         | List active refresh sessions, identify the current session, revoke one or other sessions                        |
| Administration   | Search/filter/page users, view/edit profiles, change roles/status, create administrators                        |
| Audit            | Paginated administrator activity with actor, target, and action filters                                         |
| Infrastructure   | Flyway migrations, Redis caching, request validation, structured errors, rate limiting, scheduled token cleanup |

There are no product, shopping-cart, order, payment, user-deletion, or bulk-administration APIs in this repository.

## Technology stack

| Area                     | Technology                                                                                |
| ------------------------ | ----------------------------------------------------------------------------------------- |
| Runtime                  | Java 21, Spring Boot 3.5.4                                                                |
| HTTP and validation      | Spring Web/MVC, Jakarta Bean Validation, Spring `ProblemDetail`                           |
| Security                 | Spring Security, OAuth2 Client, JJWT 0.12.6, Argon2                                       |
| Persistence              | Spring Data JPA/Hibernate, PostgreSQL, Flyway                                             |
| Cache                    | Spring Data Redis; Redis locally and Render Key Value/Valkey in the deployed setup        |
| Email and images         | Spring Mail/Gmail SMTP, Cloudinary, ImageIO/TwelveMonkeys                                 |
| Rate limits and metadata | Bucket4j, UA Parser, IP-based location lookup                                             |
| API documentation        | springdoc-openapi 2.8.13, Swagger UI                                                      |
| Tests                    | JUnit Jupiter, Mockito, AssertJ, Spring Boot Test, Spring Security Test, MockMvc, Mailpit |
| Build and development    | Maven wrapper, Docker Compose, Lombok, spring-dotenv                                      |

Exact dependency versions are defined in [pom.xml](pom.xml). Local and test Compose files use PostgreSQL 18.3.

## Architecture

The backend and frontend live in separate repositories and deploy independently. In the current cloud setup, Vercel forwards API and OAuth requests to Render so browser authentication uses the frontend origin.

```mermaid
flowchart LR
    Browser[Browser] --> Vercel[React on Vercel]
    Vercel -->|API and OAuth rewrites| API[Spring Boot on Render]
    API --> DB[(PostgreSQL)]
    API --> Cache[(Redis / Valkey)]
    API --> Mail[SMTP provider]
    API --> Images[Cloudinary]
```

```text
src/main/java/com/minthanttun/usermanagementsystem/
├── admin/       Administrator services, DTOs, search, and audit endpoints
├── auth/        Signup, login, verification, recovery, and refresh sessions
├── audit/       Audit entities, repository, and service
├── common/      Exception handling and scheduled token cleanup
├── config/      Security, storage, cache, rate limits, and OpenAPI
├── security/
│   ├── jwt/     Tokens, cookies, filters, and session revocation
│   ├── oauth2/  Google identity handling and login success
│   ├── ratelimit/
│   └── session/ Device and approximate-location descriptions
└── user/        Profiles, passwords, photos, and account persistence

src/main/resources/       Properties and Flyway migrations
src/test/                 Unit, integration, HTTP, and E2E tests
load-tests/               Separate local load-test tools
```

Controllers accept validated DTOs, services implement business operations and transactions, and repositories access PostgreSQL. Security filters authenticate bearer requests and apply rate limits before protected handlers run.

## Local development

### Prerequisites

- Java 21; check with `java -version`.
- Docker with Docker Compose.
- The included Maven wrapper; a separate Maven installation is optional.
- Google OAuth, Gmail SMTP, and Cloudinary configuration for their integrations. The default properties reference these settings during startup.

### 1. Clone and configure

```bash
git clone https://github.com/Min-Thant794/userManagementSystem.git
cd userManagementSystem
cp .env.example .env
```

Replace the example values in `.env`:

```dotenv
POSTGRES_DB=user_management_system
POSTGRES_USER=ums_local
POSTGRES_PASSWORD=replace-with-a-local-database-password
DOCKER_REDIS_PASSWORD=replace-with-a-local-redis-password

JWT_SECRET=replace-with-a-random-signing-key
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

GMAIL_USERNAME=your-mailbox@gmail.com
GMAIL_APP_PASSWORD=your-gmail-app-password

CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
```

Generate a signing key, then paste its output into `JWT_SECRET`:

```bash
openssl rand -hex 32
```

The application uses the configured string's bytes as the HMAC key; it does not Base64-decode this property. The short `secret` value in `.env.example` is only a placeholder. Keep populated `.env` files out of Git. Shell environment variables or IDE run settings can supply the same values. The project includes `spring-dotenv`, and Compose also reads the root `.env` for substitution.

### 2. Start PostgreSQL and Redis

```bash
docker compose -f docker-compose.yml up -d --wait postgres redis
```

| Dependency | Application on your computer | Application inside Compose |
| ---------- | ---------------------------- | -------------------------- |
| PostgreSQL | `localhost:5433`             | `postgres:5432`            |
| Redis      | `localhost:6379`             | `redis:6379`               |

Development data uses named volumes. Flyway applies migrations during startup; Hibernate uses `ddl-auto=validate` to check the resulting schema.

### 3. Run Spring Boot

```bash
./mvnw spring-boot:run
```

On Windows PowerShell, use `.\mvnw.cmd spring-boot:run` and `Copy-Item .env.example .env` for the earlier copy step. If the Unix wrapper lacks executable permission, use `sh ./mvnw spring-boot:run`.

The API listens on [localhost:8080](http://localhost:8080/). Open [local Swagger UI](http://localhost:8080/swagger-ui/index.html) or the [OpenAPI document](http://localhost:8080/v3/api-docs).

### 4. Connect the frontend and accounts

Run the companion frontend on `http://localhost:5173`. The default CORS origin, verification/reset links, and final Google-login frontend redirect all use this origin. Use `localhost` consistently for both applications.

Signup creates a `USER` and requires email verification before password login. The migrations include an initial administrator seed and an administrator credential update in V2/V10. Review these installation-specific records when setting up a fresh database; the repository does not document a plaintext seed password. Provision the first administrator through a controlled database operation or recover an administrator account you own through the configured reset flow. Once signed in, an administrator can create additional administrators through the API or frontend.

### Run the complete local stack in Docker

```bash
docker compose -f docker-compose.yml --profile app up --build -d --wait
```

The optional `app` service activates the Spring `docker` profile and publishes port 8080. Stop any other application using that port first. The included [Dockerfile](Dockerfile) builds with Java 21 and runs the JAR as a non-root user. Its image build skips tests; run the test suites separately.

The Compose application health probe expects `/api/users/me` to return `401` without credentials. It checks HTTP/security availability, not database or cache readiness. Stop the development stack with `docker compose --profile app down`; adding `-v` would also delete its named data volumes.

## Configuration profiles

| Profile             | Configuration                                                                     | Purpose                                                                                  |
| ------------------- | --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Default             | [application.properties](src/main/resources/application.properties)               | Local app with PostgreSQL on 5433 and Redis on 6379                                      |
| `docker`            | [application-docker.properties](src/main/resources/application-docker.properties) | Local containers using Compose service names                                             |
| `prod`              | [application-prod.properties](src/main/resources/application-prod.properties)     | Hosted database/cache endpoints, HTTPS cookies, public frontend URLs, host-assigned port |
| Test configurations | [src/test/resources](src/test/resources)                                          | Isolated integration, HTTP, smoke, and E2E settings                                      |

`application-prod.properties` overrides the base configuration; it does not replace it. Google credentials, Gmail settings, Cloudinary settings, JWT lifetimes, and other shared properties still come from `application.properties`. Supply their environment variables in production as well.

## API reference

Paths are relative to the backend URL. Protected endpoints require `Authorization: Bearer <access-token>`; admin endpoints also require the current user's `ADMIN` role. Refresh/session-cookie requirements are separate from bearer authentication.

### Authentication

| Method | Path                            | Purpose                                                             |
| ------ | ------------------------------- | ------------------------------------------------------------------- |
| POST   | `/api/auth/signup`              | Register; returns 201 and a user response                           |
| POST   | `/api/auth/verify-email`        | Consume a verification token; returns 204                           |
| POST   | `/api/auth/resend-verification` | Request verification mail; returns 204                              |
| POST   | `/api/auth/login`               | Issue access token and refresh cookie                               |
| POST   | `/api/auth/refresh`             | Rotate the cookie-backed refresh token                              |
| POST   | `/api/auth/logout`              | Revoke the supplied refresh token and clear its cookie; returns 204 |
| POST   | `/api/auth/forgot-password`     | Request a reset email; returns 204                                  |
| POST   | `/api/auth/reset-password`      | Reset using a token; returns 204                                    |
| GET    | `/oauth2/authorization/google`  | Begin Google sign-in through Spring Security                        |

### Current account and sessions

| Method | Path                             | Purpose                                                  |
| ------ | -------------------------------- | -------------------------------------------------------- |
| GET    | `/api/users/me`                  | Read the current profile                                 |
| PATCH  | `/api/users/me`                  | Update profile/contact details                           |
| POST   | `/api/users/me/complete-profile` | Complete username and phone information                  |
| PUT    | `/api/users/me/password`         | Change an existing password; returns 204                 |
| PUT    | `/api/users/me/password/initial` | Set an OAuth account's first local password; returns 204 |
| PUT    | `/api/users/me/photo`            | Upload multipart field `file`                            |
| DELETE | `/api/users/me/photo`            | Remove the current photo                                 |
| GET    | `/api/auth/sessions`             | List active refresh sessions                             |
| DELETE | `/api/auth/sessions/{sessionId}` | Revoke an owned session; returns 204                     |
| DELETE | `/api/auth/sessions/others`      | Revoke other sessions; returns 204                       |

Send the refresh cookie with session requests. Without it, the backend cannot identify the current session, and `/sessions/others` revokes all refresh sessions belonging to the authenticated user.

### Administration

| Method | Path                           | Purpose                              |
| ------ | ------------------------------ | ------------------------------------ |
| GET    | `/api/admin/users`             | Search, filter, and paginate users   |
| GET    | `/api/admin/users/{id}`        | Read an administrator view of a user |
| PUT    | `/api/admin/users/{id}`        | Update user details                  |
| PATCH  | `/api/admin/users/{id}/role`   | Change role                          |
| PATCH  | `/api/admin/users/{id}/status` | Suspend/reactivate an account        |
| POST   | `/api/admin/users`             | Create an administrator; returns 201 |
| GET    | `/api/admin/audit-logs`        | Filter and paginate audit records    |

User filters: `search`, `role`, `status`. Audit filters: `actorUserId`, `targetUserId`, `action`. Lists accept zero-based `page`, `size`, and Spring-style `sort`, such as `createdAt,desc`.

### Request and response examples

Registration body for `POST /api/auth/signup`:

```json
{
  "username": "demo_user",
  "email": "demo@example.com",
  "phoneNumber": "+6591234567",
  "password": "ExamplePassword123"
}
```

Use an email address you control for an actual test. Submit the received token to `/api/auth/verify-email` as `{"token":"<verification-token>"}` before password login. Registration does not issue login tokens.

Login body for `POST /api/auth/login`:

```json
{
  "identifier": "demo_user",
  "password": "ExamplePassword123"
}
```

`identifier` accepts a username or email. A successful response is:

```json
{
  "accessToken": "<access-token>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

`expiresIn` is in seconds. The refresh token arrives separately through `Set-Cookie`. API clients need a cookie jar; browser requests need `credentials: 'include'`.

Validation errors use `ProblemDetail`, including field errors:

```json
{
  "type": "about:blank",
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more fields failed validation",
  "errors": {
    "email": "Email must be a valid email address"
  }
}
```

Unknown JSON properties are rejected. Profile responses expose `id`, `username`, `email`, `pendingEmail`, `phoneNumber`, `role`, and `profileImageUrl`. Admin responses provide additional account status and verification fields. Swagger documents the request schemas and response contracts.

## Authentication behavior

| Mechanism           | Current behavior                                                                           |
| ------------------- | ------------------------------------------------------------------------------------------ |
| Password hashing    | Argon2                                                                                     |
| Access token        | Signed JWT; 15 minutes by default                                                          |
| Refresh token       | Signed JWT; 7 days by default; hash persisted in PostgreSQL                                |
| Refresh rotation    | Consumes the old token and issues a replacement in the same family                         |
| Token reuse         | Revokes the reused token's family                                                          |
| Refresh cookie      | `HttpOnly`; `SameSite=Lax`; path `/api/auth`; `Secure=true` in `prod`                      |
| Lockout             | Five failed password attempts trigger a 15-minute lockout                                  |
| Email verification  | Single-use hashed token; 24 hours                                                          |
| Password reset      | Single-use hashed token; 30 minutes                                                        |
| Authorization       | JWT filter loads the current account and uses its current authorities/status               |
| Incomplete profiles | Completion and photo endpoints remain available while other protected access is restricted |

Revoking a refresh session prevents future refreshes; it does not immediately invalidate every already-issued access JWT. Password and confirmed email changes revoke refresh sessions. The JWT filter continues to check live account status and permissions.

Email changes stay in `pendingEmail` until verified. Administrator creation produces an active, verified administrator rather than an invitation. The service prevents demoting or suspending the last active administrator.

Spring Security currently uses a stateless API session policy, credentialed CORS, and disabled CSRF protection. The custom refresh cookie's `SameSite` value is set in `CookieUtil`; the servlet-session cookie setting does not change it. A direct cross-site frontend/API deployment requires a deliberate cookie and CSRF design change. The documented Vercel deployment keeps browser API requests on one origin.

### Rate limits

| Route group                          | Capacity and refill                              | Key                      |
| ------------------------------------ | ------------------------------------------------ | ------------------------ |
| Login, signup, forgot/reset password | 5 per route; refill every 60 seconds             | IP                       |
| Refresh                              | 20; continuous refill of 20 per 60 seconds       | User, falling back to IP |
| `/api/users/**`                      | 100; continuous refill of 100 per 60 seconds     | User, falling back to IP |
| `/api/admin/**`                      | 100; continuous refill of 100 per 60 seconds     | User, falling back to IP |
| Global                               | 1,000; continuous refill of 1,000 per 60 seconds | Application instance     |

Limits use in-memory Bucket4j buckets, not Redis counters. Rejection returns 429 with `Retry-After: 60`. Proxy client-IP headers and rate limits need review before scaling across multiple application instances.

## Database and caching

PostgreSQL stores users, OAuth identities, refresh sessions, password-reset tokens, email-verification tokens, and administrator audit records.

The current migration directory contains **V1–V10**:

| Migration | Purpose                                                                                                    |
| --------- | ---------------------------------------------------------------------------------------------------------- |
| V1        | Initial users, OAuth accounts, refresh tokens, and audit logs                                              |
| V2        | First administrator seed                                                                                   |
| V3        | Login-attempt and lockout fields                                                                           |
| V4        | Password-reset tokens                                                                                      |
| V5        | Email-verification fields/tokens and existing-admin verification                                           |
| V6        | Profile-image URL                                                                                          |
| V7        | Pending email                                                                                              |
| V8        | Refresh-token families                                                                                     |
| V9        | Session metadata                                                                                           |
| V10       | Update the first administrator's password/lockout state and invalidate that account's refresh/reset tokens |

See [db/migration](src/main/resources/db/migration). Add new migrations for future changes rather than editing migrations already applied to an existing database. V2/V10 are data migrations with account-specific effects; review them when provisioning another installation.

Redis caches `users` and `adminUsers` profile responses for ten minutes by default. Profile, admin, and verification operations evict relevant entries. Account records and refresh sessions remain in PostgreSQL. Profile-image files are stored in Cloudinary.

Images accept JPEG, PNG, or WebP, up to 5 MiB, with dimensions from 50 to 4000 pixels per side. Cloudinary creates 300 × 300 JPEG avatars. Both multipart file and request limits are `5MB`, so the frontend reserves space for multipart overhead.

## Testing

Run commands from the backend repository root. On Windows, replace `./mvnw` with `.\mvnw.cmd`.

### Unit tests

```bash
./mvnw '-Dtest=*Test' test
```

This selects classes ending in `Test`, excluding the application-context class ending in `Tests` and the integration classes ending in `IT`.

### Database, Redis, HTTP, and application-context tests

```bash
docker compose -f compose.integration.yml up -d --wait
./mvnw '-Dtest=**/integration/*IT,**/httpIntegration/*IT,UsermanagementsystemApplicationTests' test
```

Dedicated services use PostgreSQL port **15432** and Redis port **16379**. HTTP tests use MockMvc; database/cache integration tests exercise the dedicated services.

### End-to-end workflows

```bash
docker compose -f compose.e2e.yml up -d --wait
./mvnw '-Dtest=**/e2e/*IT' test
```

These tests start Spring on a random port and exercise registration, password, profile, administrator, and session workflows. PostgreSQL uses **25432**, Redis **26379**, and Mailpit SMTP **11025**. Open the [Mailpit inbox](http://localhost:18025/) to inspect local test mail. Cloudinary photo operations and geolocation are mocked; this suite does not verify live Google or Cloudinary integrations.

The test databases are disposable and must remain separate from development and production data. Surefire is configured without a separate Failsafe execution, so explicitly select `*IT` classes as shown. Test reports are written under `target/surefire-reports/`.

```bash
docker compose -f compose.integration.yml down
docker compose -f compose.e2e.yml down
```

### Load-test tooling

[load-tests](load-tests) contains a separate application configuration, k6 scripts for profile reads and per-user/IP traffic, and a macOS IP-alias helper. These tools target dedicated local infrastructure and can create test accounts or change local networking. Inspect their configuration before use; they are not part of the normal Maven test command.

## Deploy to Render

### Service setup

1. Push this backend repository to GitHub.
2. Create Render PostgreSQL and Key Value services in the backend's region.
3. Create a **Web Service** connected to this repository. Select **Docker**, repository root as the build context, and `Dockerfile` as the Dockerfile path.
4. Add the production environment variables below. Let the Dockerfile entrypoint start the application.
5. Deploy and check the startup logs for the `prod` profile, successful Flyway migrations, and the listening port.

Render supports building directly from the repository's Dockerfile. See [Docker on Render](https://render.com/docs/docker). This deployment uses separate managed services; it does not launch the local Compose stack.

### Production environment

Enter these values in the Render Web Service's **Environment** settings. Use actual service credentials in the dashboard; the values below are placeholders.

```dotenv
SPRING_PROFILES_ACTIVE=prod

DATABASE_JDBC_URL=jdbc:postgresql://YOUR_INTERNAL_POSTGRES_HOST:5432/YOUR_DATABASE?sslmode=require
DATABASE_USER=YOUR_DATABASE_USER
DATABASE_PASSWORD=YOUR_DATABASE_PASSWORD

REDIS_HOST=YOUR_INTERNAL_KEY_VALUE_HOST
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=YOUR_KEY_VALUE_PASSWORD
REDIS_SSL_ENABLED=false

FRONTEND_ORIGIN=https://user-management-system-frontend-mocha.vercel.app

JWT_SECRET=YOUR_RANDOM_SIGNING_KEY
GOOGLE_CLIENT_ID=YOUR_GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET=YOUR_GOOGLE_CLIENT_SECRET
GMAIL_USERNAME=YOUR_GMAIL_ADDRESS
GMAIL_APP_PASSWORD=YOUR_GMAIL_APP_PASSWORD
CLOUDINARY_CLOUD_NAME=YOUR_CLOUD_NAME
CLOUDINARY_API_KEY=YOUR_API_KEY
CLOUDINARY_API_SECRET=YOUR_API_SECRET
```

- `DATABASE_JDBC_URL` must start with `jdbc:postgresql://`. Keep username/password in their separate fields rather than pasting a `postgresql://user:password@host/...` connection string.
- `REDIS_HOST` is only the hostname, without `redis://`, credentials, or a port. For an internal `redis://` connection, use `REDIS_SSL_ENABLED=false`; a TLS `rediss://` connection requires `true`. Match the connection details supplied by the provider. Leave `REDIS_PASSWORD` empty only when internal authentication is disabled. See [Render Key Value connections](https://render.com/docs/key-value#connecting-to-your-instance).
- `FRONTEND_ORIGIN` has no trailing slash. It supplies CORS, email-link, and OAuth redirect settings.
- Render supplies `PORT`; the profile binds to `0.0.0.0` and `${PORT:8080}`.
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `DOCKER_REDIS_PASSWORD` belong to the local setup. The `prod` profile uses `DATABASE_*` and `REDIS_*` instead.

### Hosting behavior

Render Free web services sleep after 15 minutes without traffic and can take about a minute to wake. The frontend's current request timeout is 20 seconds, so the first visit after inactivity may time out. Also, Render Free blocks outbound SMTP ports 25, 465, and 587; the current Gmail configuration uses 587. Reliable email delivery therefore needs a hosting tier that permits it or a code change to an HTTPS email provider. [Render Free limitations](https://render.com/docs/free)

The Compose-specific 401 health probe is not a successful Render HTTP health-check response. If configuring a Render HTTP health-check path, use an endpoint that returns a successful status, such as the existing `/v3/api-docs`; it is an HTTP availability check, not a dedicated dependency-health endpoint.

## Google OAuth configuration

Use a **Web application** OAuth client in Google Cloud. Register the applicable values under **Authorized redirect URIs**:

| Environment                   | Google callback URI                                                                 |
| ----------------------------- | ----------------------------------------------------------------------------------- |
| Local browser → Spring        | `http://localhost:8080/login/oauth2/code/google`                                    |
| Current Vercel → Render setup | `https://user-management-system-frontend-mocha.vercel.app/login/oauth2/code/google` |

In production, Vercel forwards `/oauth2/authorization/*` and `/login/oauth2/*` to Spring. After Spring processes Google's callback and sets the refresh cookie, it redirects the browser to the React page `/oauth2/callback`. That React page is distinct from Google's registered callback.

The registered URI must exactly match the `redirect_uri` in the authorization request, including scheme, host, path, and trailing-slash behavior. Use the OAuth client corresponding to Render's `GOOGLE_CLIENT_ID`. [Google server-side OAuth documentation](https://developers.google.com/identity/protocols/oauth2/web-server)

## Troubleshooting

| Symptom                                         | Check                                                                                                                                                          |
| ----------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Missing `refreshToken` warning on a first visit | The frontend probes `/api/auth/refresh` before login. An absent cookie can be expected for a signed-out browser; inspect `/login` separately if sign-in fails. |
| First request times out on Render Free          | Allow the service to wake, then retry. Check request timings before changing credentials.                                                                      |
| `/login` succeeds but `/api/users/me` stalls    | Check PostgreSQL and Redis connectivity; profile reads use the cache.                                                                                          |
| Google `redirect_uri_mismatch`                  | Register the exact callback from the table above on the correct Google OAuth client.                                                                           |
| Database authentication fails                   | Verify the JDBC URL format and separate `DATABASE_USER`/`DATABASE_PASSWORD` values.                                                                            |
| IDE reports unresolved database columns         | Refresh its schema and ensure it connects to the same database as Spring. Local PostgreSQL uses 5433; schema changes come from Flyway.                         |
| Verification/reset email does not arrive        | Check SMTP settings, provider errors, and Render's outbound-port restrictions.                                                                                 |
| Browser cannot retain its session               | Confirm credentials are included, HTTPS cookies are enabled in production, and requests use the intended Vercel proxy origin.                                  |
| Startup rejects JWT key length                  | Replace the placeholder with a sufficiently long random signing key.                                                                                           |
| 429 responses during testing                    | Respect `Retry-After`; limits are active in normal runtime configuration.                                                                                      |

## Project background

I started backend development with Node.js and Express alongside React-based full-stack projects, then moved to Java and Spring Boot to focus on backend APIs and application architecture. This project helped me practice account lifecycles, transactions, security, caching, and testing beyond basic CRUD.

I used Claude as a learning assistant while working through backend concepts and implementation.

**Author:** [Min Thant Tun](https://github.com/Min-Thant794), Computer Science graduate focused on backend development with Java and Spring Boot.
