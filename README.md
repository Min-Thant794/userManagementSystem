# User Management System API

**Java 21 · Spring Boot 3.5.4 · Spring Security · PostgreSQL · Redis**

A REST API for user authentication, account management, session management, and administrator operations, built with **Java and Spring Boot**.

The application includes JWT authentication with refresh-token rotation, Google OAuth2 / OpenID Connect login, email verification, password recovery, profile images, role-based authorization, audit logging, caching, and rate limiting. It also includes OpenAPI documentation, automated test suites, and Docker Compose configurations for development and testing.

**Project status:** Core backend implementation completed as a personal learning and portfolio project.

## Why I Built This

I started backend development with **Node.js and Express**, alongside React-based full-stack projects. That experience helped me discover that my main interest is building backend APIs and understanding how business logic, data, security, and application architecture work together.

I built this project while moving to **Java and Spring Boot**, with the goal of developing the skills needed to build maintainable backend APIs for enterprise applications. It gave me practical experience beyond basic CRUD: managing account lifecycles, rotating tokens, controlling access, working with database transactions, and testing behavior across multiple application layers.

## Contents

- [Implemented Features](#implemented-features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [API Endpoints](#api-endpoints)
- [Request and Response Examples](#request-and-response-examples)
- [Authentication and Security Behavior](#authentication-and-security-behavior)
- [Database and Caching](#database-and-caching)
- [Testing](#testing)
- [What I Learned](#what-i-learned)

## Implemented Features

### Authentication and Account Lifecycle

- Register with a username, email address, phone number, and password.
- Log in with either a username or an email address.
- Verify an email address and request another verification email.
- Request a password-reset email and reset a password using a time-limited token.
- Change an existing password or set an initial password for an OAuth account.
- Issue JWT access and refresh tokens, with refresh tokens delivered through an HTTP-only cookie.
- Rotate refresh tokens and detect reuse of a previously consumed token.
- Temporarily lock accounts after repeated failed login attempts.

### Google Login and Profile Management

- Sign in through Google OAuth2 / OpenID Connect.
- Link a Google identity to an existing account or create a new account.
- Complete the username and phone information required for an OAuth user's profile.
- View and update the current user's profile.
- Hold email changes in `pendingEmail` until the new address is verified.
- Upload, replace, and delete profile photos through Cloudinary.

Profile-image validation supports **JPEG, PNG, and WebP**, with a maximum file size of **5 MiB** and dimensions between **50 × 50** and **4000 × 4000** pixels. Cloudinary transforms uploaded images into **300 × 300 JPEG** avatars. The multipart file and request limits are both configured to `5MB`, so multipart overhead also counts toward the request limit.

### Session Management

- List active refresh-token sessions.
- Display device information, approximate location, creation time, and last-used time.
- Identify the current session using the refresh-token cookie.
- Revoke an individual session or all other sessions.
- Revoke refresh sessions after a password change, password reset, or confirmed email change.

### Administrator Operations

- List, search, paginate, and filter users by role and account status.
- View and update individual users.
- Suspend or reactivate accounts.
- Change user roles and create additional administrators.
- Prevent the last active administrator from being demoted or suspended.
- Retrieve paginated audit logs and filter by actor, target user, and action.

### Supporting Backend Capabilities

- Redis caching for user and admin profile responses.
- Flyway migrations for database schema changes.
- Jakarta Bean Validation for request DTOs.
- Centralized exception handling with Spring `ProblemDetail` responses.
- Bucket4j endpoint limits and an application-instance request limit.
- OpenAPI / Swagger documentation with bearer-token authentication support.
- Scheduled cleanup of expired, used, or revoked token records.
- Unit, database/Redis integration, HTTP integration, end-to-end, and application-context tests.

## Technology Stack

| Area | Technologies |
| --- | --- |
| Language and framework | Java 21, Spring Boot 3.5.4, Spring Web / MVC |
| Security | Spring Security, OAuth2 Client, JJWT 0.12.6, Argon2 |
| Persistence | Spring Data JPA, PostgreSQL, Flyway |
| Caching | Spring Data Redis, Spring Cache |
| Request handling | Jakarta Bean Validation, Spring `ProblemDetail` |
| Rate limiting | Bucket4j 8.19.0 |
| Email | Spring Mail / JavaMailSender, Gmail SMTP |
| Profile images | Cloudinary, ImageIO, TwelveMonkeys WebP support |
| Session metadata | UA Parser, IP-based location lookup |
| API documentation | springdoc-openapi 2.8.13, Swagger UI |
| Testing | JUnit Jupiter, Mockito, AssertJ, Spring Boot Test, Spring Security Test, MockMvc |
| Development | Maven, Lombok, spring-dotenv, Docker Compose, Mailpit |

Dependency declarations are maintained in [`pom.xml`](pom.xml).

## Project Structure

```text
src/
├── main/
│   ├── java/com/minthanttun/usermanagementsystem/
│   │   ├── admin/          # Admin operations, search, and audit-log endpoints
│   │   ├── auth/           # Authentication, verification, recovery, and sessions
│   │   ├── audit/          # Audit records and persistence
│   │   ├── common/         # Exception handling and token cleanup
│   │   ├── config/         # Security, caching, rate limits, storage, and OpenAPI
│   │   ├── security/
│   │   │   ├── jwt/
│   │   │   ├── oauth2/
│   │   │   ├── ratelimit/
│   │   │   └── session/
│   │   └── user/           # Profiles, passwords, and profile images
│   └── resources/
│       ├── db/migration/
│       ├── application.properties
│       └── application-docker.properties
└── test/
    ├── java/com/minthanttun/usermanagementsystem/
    │   ├── ...             # Unit tests grouped alongside feature packages
    │   ├── integration/
    │   ├── httpIntegration/
    │   └── e2e/
    └── resources/          # Separate smoke, integration, HTTP, and E2E settings
```

Controllers handle HTTP requests and validated DTOs, services implement business operations, and repositories provide database access. Security filters process bearer authentication and rate limits before requests reach protected endpoints.

## Getting Started

### 1. Prerequisites

- **Java 21** for running the application locally.
- **Maven**, or the repository's Maven wrapper. The wrapper configuration targets Maven 3.9.16.
- **Docker with Docker Compose** for the provided PostgreSQL and Redis environments.
- Google OAuth credentials, Gmail SMTP credentials, and a Cloudinary account for their corresponding integrations. The default application configuration references these values at startup.

### 2. Clone the Repository

```bash
git clone https://github.com/Min-Thant794/userManagementSystem.git
cd userManagementSystem
```

### 3. Configure Local Environment Values

Create a `.env` file in the project root. The project declares `spring-dotenv`, and Docker Compose also reads the root `.env` file for variable substitution.

```dotenv
POSTGRES_DB=user_management_system
POSTGRES_USER=ums_local
POSTGRES_PASSWORD=replace-with-your-local-database-password
DOCKER_REDIS_PASSWORD=replace-with-your-local-redis-password
JWT_SECRET=replace-with-a-random-signing-secret
GOOGLE_CLIENT_ID=replace-with-your-google-client-id
GOOGLE_CLIENT_SECRET=replace-with-your-google-client-secret
GMAIL_USERNAME=replace-with-your-gmail-address
GMAIL_APP_PASSWORD=replace-with-your-gmail-app-password
CLOUDINARY_CLOUD_NAME=replace-with-your-cloud-name
CLOUDINARY_API_KEY=replace-with-your-cloudinary-api-key
CLOUDINARY_API_SECRET=replace-with-your-cloudinary-api-secret
```

Replace all placeholder values before starting. For `JWT_SECRET`, use a random HMAC signing key of at least 32 bytes; for example, generate a value with `openssl rand -hex 32`. The application uses the configured string's bytes as its signing key.

Keep the populated `.env` file out of version control. Environment variables supplied through your shell or IDE run configuration can also provide these settings.

### 4. Start PostgreSQL and Redis

```bash
docker compose -f docker-compose.yml up -d --wait
```

The default Compose services create the configured PostgreSQL database and start Redis. PostgreSQL and Redis use named volumes to retain development data.

| Dependency | Application running on your computer | Application running inside Compose |
| --- | --- | --- |
| PostgreSQL | `localhost:5433` | `postgres:5432` |
| Redis | `localhost:6379` | `redis:6379` |

The local JDBC URL is `jdbc:postgresql://localhost:5433/${POSTGRES_DB}`. Flyway applies migrations during application startup, and Hibernate validates the resulting schema.

### 5. Run the Application

**macOS / Linux**

```bash
./mvnw spring-boot:run
```

**Windows PowerShell**

```powershell
.\mvnw.cmd spring-boot:run
```

If you use an installed Maven distribution, replace `./mvnw` with `mvn`. The application can also be launched from a Java IDE with the same environment settings.

The default local base URL is **`http://localhost:8080`**.

### Optional: Run the Application in Docker

The development Compose file defines an optional `app` service using the Spring `docker` profile. This build expects a `Dockerfile` at the repository root.

```bash
docker compose -f docker-compose.yml --profile app up --build -d --wait
```

Use this when the root Dockerfile is available in your checkout. Stop any locally running application using port 8080 before starting the application container.

The app's health check expects an unauthenticated request to `/api/users/me` to return **401**. It checks HTTP/security availability; it does not continuously verify database or cache health.

### Frontend and External-Service Settings

The backend currently uses these local frontend URLs:

| Setting | Default |
| --- | --- |
| Allowed CORS origin | `http://localhost:5173` |
| Email-verification page | `http://localhost:5173/verify-email` |
| Password-reset page | `http://localhost:5173/reset-password` |
| Google-login frontend redirect | `http://localhost:5173/oauth2/callback` |

These are client integration targets; the backend does not serve those frontend pages. Update the corresponding `app.*` properties when using another frontend.

For local Google login, configure the Google OAuth client with the callback URL `http://localhost:8080/login/oauth2/code/google`. Browser clients making cookie-based refresh or logout requests must include credentials.

## API Documentation

The application includes controller and DTO OpenAPI annotations and a `bearerAuth` security scheme.

After startup:

- **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Use Swagger UI's **Authorize** control to supply an access token for protected endpoints. Refresh operations also require the `refreshToken` cookie; a bearer token alone does not replace it.

## API Endpoints

Paths below are relative to the application base URL. Protected endpoints require `Authorization: Bearer <access-token>`. Admin endpoints additionally require the `ADMIN` role.

### Authentication and Verification

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/signup` | Register a user; returns `201` and a user response |
| `POST` | `/api/auth/verify-email` | Verify an email token; returns `204` |
| `POST` | `/api/auth/resend-verification` | Request another verification email; returns `204` |
| `POST` | `/api/auth/login` | Log in and receive an access token plus refresh cookie |
| `POST` | `/api/auth/refresh` | Rotate the refresh token and issue a new access token |
| `POST` | `/api/auth/logout` | Revoke the supplied refresh token and clear its cookie; returns `204` |
| `POST` | `/api/auth/forgot-password` | Request a password-reset email; returns `204` |
| `POST` | `/api/auth/reset-password` | Reset a password using a reset token; returns `204` |

These routes do not require a bearer token. `/refresh` requires the refresh-token cookie. Google sign-in starts at `/oauth2/authorization/google`.

### Sessions

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/auth/sessions` | List active sessions and identify the current session when its cookie is supplied |
| `DELETE` | `/api/auth/sessions/{sessionId}` | Revoke an owned session; returns `204` |
| `DELETE` | `/api/auth/sessions/others` | Revoke other sessions; returns `204` |

All session endpoints require bearer authentication. Send the refresh cookie to preserve the current session when revoking other sessions. **Without that cookie, `/sessions/others` revokes all refresh sessions for the authenticated user.**

### Current User

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/users/me` | Read the current user's cached profile |
| `PATCH` | `/api/users/me` | Update profile details |
| `PUT` | `/api/users/me/password` | Change the password; returns `204` |
| `PUT` | `/api/users/me/password/initial` | Set an initial local password for an OAuth account; returns `204` |
| `PUT` | `/api/users/me/photo` | Upload a photo using the multipart field `file` |
| `DELETE` | `/api/users/me/photo` | Delete the profile photo |
| `POST` | `/api/users/me/complete-profile` | Supply the username and phone number for an incomplete profile |

### Administration

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/admin/users` | List/search users with role, status, and pagination filters |
| `GET` | `/api/admin/users/{id}` | Retrieve a cached admin view of a user |
| `PUT` | `/api/admin/users/{id}` | Update user details |
| `PATCH` | `/api/admin/users/{id}/status` | Change account status |
| `PATCH` | `/api/admin/users/{id}/role` | Change a user's role |
| `POST` | `/api/admin/users` | Create another administrator; returns `201` |
| `GET` | `/api/admin/audit-logs` | Retrieve and filter audit logs |

User-list filters are `search`, `role`, and `status`. Audit-log filters are `actorUserId`, `targetUserId`, and `action`. Both list endpoints accept Spring pagination parameters such as `page`, `size`, and `sort`.

## Request and Response Examples

The values below are illustrative. Use an email address you control to complete verification.

### Register

`POST /api/auth/signup`

```json
{
  "username": "demo_user",
  "email": "demo@example.com",
  "phoneNumber": "+6591234567",
  "password": "ExamplePassword123"
}
```

Registration returns `201 Created` with a `UserResponse`. It starts email verification and does not issue login tokens. Submit the received verification token to `/api/auth/verify-email` in a JSON body containing `token` before logging in.

### Log In

`POST /api/auth/login`

```json
{
  "identifier": "demo_user",
  "password": "ExamplePassword123"
}
```

`identifier` accepts either the username or email address. A successful response has this shape:

```json
{
  "accessToken": "<access-token>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

The refresh token is sent separately in a `Set-Cookie` header. `expiresIn` is measured in seconds.

### Access a Protected Endpoint

```http
GET /api/users/me
Authorization: Bearer <access-token>
```

Profile responses contain `id`, `username`, `email`, `pendingEmail`, `phoneNumber`, `role`, and `profileImageUrl`.

### Validation Error

An invalid email address can produce a `400 Bad Request` response with fields such as:

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

Request DTOs validate required fields, email format, phone format, username rules, and password requirements. Unknown JSON properties are rejected. The exception handlers also map duplicate resources, invalid credentials, unavailable users, restricted accounts, and invalid token operations to HTTP error responses.

## Authentication and Security Behavior

### Tokens and Accounts

| Mechanism | Implemented behavior |
| --- | --- |
| Access token | Signed JWT; 15-minute lifetime |
| Refresh token | Signed JWT; 7-day lifetime; hash stored in PostgreSQL |
| Refresh rotation | Consumes the previous token and issues a replacement in the same token family |
| Refresh-token reuse | Revokes the token family and returns an authentication error |
| Refresh cookie | `HttpOnly`, `SameSite=Lax`, path `/api/auth`; `Secure=false` in the local configuration |
| Email-verification token | Random token stored as a hash; 24-hour lifetime; single use |
| Password-reset token | Random token stored as a hash; 30-minute lifetime; single use |
| Passwords | Hashed with Argon2 |
| Failed login attempts | 5 failures trigger a 15-minute lockout |
| Account checks | Bearer authentication rechecks whether the user exists and is enabled/unlocked |
| Profile completion | Protected access is restricted until username and phone are set; completion and photo endpoints remain available |
| Token cleanup | Scheduled daily at 03:00 in the application's scheduler timezone |

Session revocation prevents further refreshes; it does not directly invalidate an already-issued access JWT. Access tokens remain subject to expiry and the live account checks performed by the JWT filter.

Google login sets the refresh cookie and redirects to the configured frontend callback with `profileComplete`. The client can then call `/api/auth/refresh` to obtain an access token.

The API uses Spring Security's stateless session policy, credentialed CORS for configured origins, and disabled CSRF protection in the current configuration. The supplied cookie and URL defaults target local HTTP development; review them when configuring HTTPS hosting.

### Rate Limits

| Route group | Bucket capacity / refill | Identity |
| --- | --- | --- |
| Login, signup, forgot password, reset password | 5 requests; refilled every 60 seconds for each route | IP address |
| Refresh | 20 requests; continuous refill at 20 per 60 seconds | Authenticated user, falling back to IP |
| `/api/users/**` | 100 requests; continuous refill at 100 per 60 seconds | Authenticated user, falling back to IP |
| `/api/admin/**` | 100 requests; continuous refill at 100 per 60 seconds | Authenticated user, falling back to IP |
| Global | 1,000 requests; continuous refill at 1,000 per 60 seconds | Shared within one application instance |

Buckets are held in application memory. Redis is used for caching, not for distributing rate-limit counters. Rejected requests return **429 Too Many Requests** with a `Retry-After: 60` header.

## Database and Caching

PostgreSQL stores six main entity groups: users, linked OAuth accounts, refresh tokens, password-reset tokens, email-verification tokens, and audit logs.

The project includes **nine Flyway migrations**:

| Migration | Purpose |
| --- | --- |
| `V1__init.sql` | Initial users, OAuth accounts, refresh tokens, and audit logs |
| `V2__seed_first_admin.sql` | Initial administrator seed |
| `V3__add_login_lockout.sql` | Failed-attempt and lockout fields |
| `V4__add_password_reset_tokens.sql` | Password-reset tokens |
| `V5__add_email_verification.sql` | Email-verification state and tokens |
| `V6__add_profile_image.sql` | Profile-image URL |
| `V7__add_pending_email.sql` | Pending email changes |
| `V8__add_refresh_token.sql` | Refresh-token family identifier |
| `V9__add_session_metadata.sql` | IP address, user agent, and last-used time |

Review the initial-admin seed when preparing your own installation; its stored password hash does not provide a documented plaintext login password. The normal signup endpoint creates a `USER`, and creating additional administrators through the API requires an existing `ADMIN`.

Redis caches the `users` and `adminUsers` profile responses with a **10-minute default TTL**. Profile changes, admin updates, and email verification evict the relevant cached entries.

## Testing

The source contains the following automated test classes:

| Test layer | Classes | Scope |
| --- | ---: | --- |
| Unit | 37 | Services, JWT/OAuth components, rate limiting, DTOs, exception handling, sessions, and supporting utilities |
| Database / Redis integration | 8 | Schema, repositories, transactions, refresh concurrency, token uniqueness, search, and caching |
| HTTP integration | 7 | Controller behavior, authentication, authorization, cookies, and session routes using MockMvc |
| End-to-end | 6 | Registration, password, profile, session, and admin workflows over HTTP, plus environment prerequisites |
| Application-context smoke | 1 | Spring Boot application startup |

These are **test-class counts**, not executed-case counts or coverage percentages. Parameterized tests can execute multiple cases. Test outcomes should be taken from a fresh run of the selected suite.

### Unit Tests

```bash
./mvnw '-Dtest=*Test' test
```

This selects the unit classes ending in `Test`, excluding the context test ending in `Tests` and integration classes ending in `IT`.

### Database, HTTP Integration, and Context Tests

Start the dedicated PostgreSQL and Redis services:

```bash
docker compose -f compose.integration.yml up -d --wait
```

Run the database/Redis integration classes, HTTP integration classes, and application-context test:

```bash
./mvnw '-Dtest=**/integration/*IT,**/httpIntegration/*IT,UsermanagementsystemApplicationTests' test
```

These test configurations use PostgreSQL on **15432** and Redis on **16379**. They use isolated test configuration rather than the development database settings.

### End-to-End Tests

```bash
docker compose -f compose.e2e.yml up -d --wait
./mvnw '-Dtest=**/e2e/*IT' test
```

The E2E environment uses PostgreSQL on **25432**, Redis on **26379**, and Mailpit SMTP on **11025**. Captured emails can be viewed at [http://localhost:18025](http://localhost:18025).

The E2E application runs on a random local port. Cloudinary image operations and geolocation are mocked in this suite; email delivery is exercised through local Mailpit. It does not establish live Google or Cloudinary integration results.

The integration and E2E configurations use disposable databases. Run them against their dedicated Compose services, not a development or production database.

On Windows PowerShell, replace `./mvnw` with `.\mvnw.cmd`. The commands explicitly select `IT` classes because `pom.xml` uses Surefire and does not define a separate Failsafe execution.

To stop the test environments:

```bash
docker compose -f compose.integration.yml down
docker compose -f compose.e2e.yml down
```

A separate load-test application configuration is present at `load-tests/application-load.properties`. Load-test scripts and measured performance results are not documented here.

## What I Learned

- Structuring a Spring Boot application around controllers, services, repositories, DTOs, and security filters.
- Implementing registration, verification, recovery, OAuth login, and refresh-token lifecycles.
- Working with relational data, JPA, transactions, and Flyway migrations.
- Using Redis caching with explicit invalidation after relevant changes.
- Validating requests and returning structured errors.
- Testing behavior at the unit, persistence, HTTP, and workflow levels.
- Separating development, integration, and E2E environments with configuration and Docker Compose.
- Documenting APIs through OpenAPI annotations and concrete request/response examples.

I used **Claude as a learning assistant** while working through implementation and backend concepts. This project reflects the Java and Spring experience I developed as I moved toward enterprise backend API development.

## Author

**[Min Thant Tun](https://github.com/Min-Thant794)**  
Computer Science graduate focused on backend development with Java and Spring Boot.
