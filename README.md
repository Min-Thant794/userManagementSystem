# User Management System

This is my **User Management System backend** project built with **Java and Spring Boot**.

I am currently learning Java Spring Boot by building projects myself and using Claude as a learning assistant. This project is mainly for learning how a real backend application works, especially authentication, authorization, database relationships, security, and REST APIs.

It is not meant to be a perfect production-ready application. I am building it step by step and learning from the problems I run into along the way.

## What I am learning from this project

Some of the main things I am practicing in this project are:

* Java
* Spring Boot
* Spring MVC / REST API
* Spring Data JPA
* Spring Security
* JWT authentication
* Refresh tokens
* OAuth2 / Google Login
* PostgreSQL
* Flyway database migrations
* Password hashing with Argon2
* Email verification
* Password reset
* Role-based authorization
* Rate limiting
* Audit logs
* Validation and exception handling
* Maven
* Git and GitHub

## Main Features

### User Authentication

Users can:

* Sign up
* Log in using username or email
* Log out
* Refresh their access token
* Reset their password
* Verify their email
* Change their password
* Complete their profile

The authentication system uses **JWT access tokens** and **refresh tokens**. The refresh token is stored in an HTTP-only cookie. Access tokens expire after 15 minutes and refresh tokens expire after 7 days in the current configuration.

### Email Verification

When a new user signs up, an email verification process is started.

Verification tokens are stored as hashes in the database and expire after 24 hours.

### Password Reset

Users can request a password reset through their email.

The password reset token has a limited lifetime and is stored hashed in the database rather than storing the raw token.

### Google OAuth2 Login

The project also supports Google OAuth2/OIDC login.

When a user logs in with Google, the application can find an existing linked account or create/link a user account. Google accounts with verified email addresses are accepted by the application.

### User Profile

Authenticated users can:

* View their own profile
* Update their profile
* Change their password
* Set an initial password for OAuth accounts
* Complete their profile

These endpoints are under `/api/users`.

### Admin Features

Admins can manage users.

Current admin functionality includes:

* View users
* Search users
* Filter users by role
* Filter users by status
* View a specific user
* Update a user
* Suspend/reactivate accounts
* Change user roles
* Create another admin
* View audit logs

The admin API is protected using `@PreAuthorize("hasRole('ADMIN')")`.

There is also a rule that prevents the last remaining active admin from being demoted or suspended.

### Login Attempt Lockout

The project keeps track of failed login attempts.

After **5 failed login attempts**, the account is temporarily locked for **15 minutes**.

### Rate Limiting

I added rate limiting using **Bucket4j**.

There are different limits for different endpoints. For example:

* Login: 5 requests/minute per IP
* Signup: 5 requests/minute per IP
* Forgot password: 5 requests/minute per IP
* Reset password: 5 requests/minute per IP
* Refresh token: 20 requests/minute per user
* User endpoints: 100 requests/minute per user
* Admin endpoints: 100 requests/minute per user

There is also a global limit of 1000 requests per minute.

## Technologies

| Technology               | Purpose                          |
| ------------------------ | -------------------------------- |
| Java 21                  | Programming language             |
| Spring Boot 3.5.4        | Backend framework                |
| Spring Web               | REST APIs                        |
| Spring Data JPA          | Database access                  |
| Spring Security          | Authentication and authorization |
| JWT                      | Access and refresh tokens        |
| OAuth2 Client            | Google login                     |
| PostgreSQL               | Database                         |
| Flyway                   | Database migrations              |
| Argon2                   | Password hashing                 |
| Bucket4j                 | Rate limiting                    |
| JavaMailSender           | Email                            |
| Lombok                   | Reduce boilerplate code          |
| Maven                    | Build and dependency management  |
| JUnit / Spring Boot Test | Testing                          |

The main dependencies and versions can be found in `pom.xml`.

## Project Structure

The project is organised into different packages based on the feature or responsibility.

```text
src/
└── main/
    ├── java/
    │   └── com/minthanttun/usermanagementsystem/
    │       ├── admin/
    │       ├── auth/
    │       ├── audit/
    │       ├── common/
    │       ├── config/
    │       ├── security/
    │       │   ├── jwt/
    │       │   ├── oauth2/
    │       │   └── ratelimit/
    │       └── user/
    │
    └── resources/
        ├── db/
        │   └── migration/
        └── application.properties
```

I am trying to keep authentication, users, admin functions, security, and common functionality separated instead of putting everything into one package.

## API Endpoints

### Authentication

Base URL:

```text
/api/auth
```

| Method | Endpoint           | Description            |
| ------ | ------------------ | ---------------------- |
| POST   | `/signup`          | Create a new user      |
| POST   | `/login`           | Login                  |
| POST   | `/refresh`         | Get a new access token |
| POST   | `/logout`          | Logout                 |
| POST   | `/forgot-password` | Request password reset |
| POST   | `/reset-password`  | Reset password         |

These endpoints are defined in `AuthController`.

### User

Base URL:

```text
/api/users
```

| Method | Endpoint               | Description          |
| ------ | ---------------------- | -------------------- |
| GET    | `/me`                  | Get current user     |
| PATCH  | `/me`                  | Update profile       |
| PUT    | `/me/password`         | Change password      |
| PUT    | `/me/password/initial` | Set initial password |
| POST   | `/me/complete-profile` | Complete profile     |

### Admin

Base URL:

```text
/api/admin/users
```

| Method | Endpoint       | Description           |
| ------ | -------------- | --------------------- |
| GET    | `/`            | List/search users     |
| GET    | `/{id}`        | Get a user            |
| PUT    | `/{id}`        | Update a user         |
| PATCH  | `/{id}/status` | Change account status |
| PATCH  | `/{id}/role`   | Change user role      |
| POST   | `/`            | Create an admin       |

All of these admin endpoints require the `ADMIN` role.

### Admin Audit Logs

```text
GET /api/admin/audit-logs
```

Audit logs can be filtered by actor, target user, and action, and the endpoint is restricted to admins.

## Authentication Flow

The basic authentication flow currently looks like this:

```text
User
  │
  ├── Sign Up
  │      │
  │      ├── Validate input
  │      ├── Check duplicate username/email/phone
  │      ├── Hash password with Argon2
  │      ├── Save user
  │      └── Send verification email
  │
  └── Login
         │
         ├── Check email verification
         ├── Check account status
         ├── Check login attempts / lockout
         ├── Verify password
         ├── Create access token
         └── Create refresh token
```

The access token contains information such as the user ID, username, role, and token type.

## Security

Spring Security is configured to use stateless authentication.

The application:

* Uses JWT authentication
* Disables CSRF for the API
* Uses an HTTP-only refresh-token cookie
* Uses Argon2 for password hashing
* Uses role-based authorization
* Supports Google OAuth2 login
* Checks suspended/locked accounts
* Uses rate limiting

Authenticated requests use the JWT filter before the normal Spring Security username/password filter.

The password encoder is configured with Argon2.

## Database

The project uses **PostgreSQL**.

The database currently contains tables for:

* Users
* OAuth accounts
* Refresh tokens
* Password reset tokens
* Email verification tokens
* Audit logs

The user table contains information such as username, email, phone number, password hash, role, account status, email verification status, failed login attempts, and lockout time.

Flyway is used to manage database migrations. The migration files are located in:

```text
src/main/resources/db/migration/
```

Some of the current migrations include:

```text
V1__init.sql
V2__seed_first_admin.sql
V3__add_login_lockout.sql
V4__add_password_reset_tokens.sql
V5__add_email_verification.sql
```

## Requirements

Before running the project, you need:

* Java 21
* Maven
* PostgreSQL
* A PostgreSQL database
* Google OAuth credentials if you want Google login
* Gmail SMTP credentials if you want email features

## Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE user_management_system;
```

The application is currently configured to connect to:

```text
jdbc:postgresql://localhost:5432/user_management_system
```

Flyway will handle the database migrations when the application starts.

## Environment Variables

I am using environment variables for values that should not be hard-coded in the application.

Create your environment configuration with values for:

```text
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GMAIL_USERNAME=
GMAIL_APP_PASSWORD=
```

The application reads these values from the Spring configuration.

**Do not commit your real secrets or passwords to GitHub.**

## Running the Project

Clone the repository:

```bash
git clone https://github.com/Min-Thant794/userManagementSystem.git
```

Go into the project:

```bash
cd usermanagementsystem
```

Then run:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

You can also run the project directly from IntelliJ IDEA or another Java IDE.

## Testing

There is currently a basic Spring Boot test for checking whether the application context loads.

```java
@SpringBootTest
class UsermanagementsystemApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

I plan to add more tests as I continue learning, especially for authentication, user services, and admin functionality.

## What I want to improve next

This project is still a learning project, so there are things I want to improve as I learn more Spring Boot.

Some things I want to work on next:

* Add more unit tests
* Add more integration tests
* Improve API documentation
* Improve exception handling
* Improve validation
* Learn more about Spring Security internals
* Improve database design where necessary
* Add a frontend later
* Improve logging
* Learn more about Docker and deployment
* Deploy the backend
* Clean up parts of the code as I understand Spring better

## Things I learned while building this

This project started as a simple user management idea, but it became much more complicated once I started adding authentication and security.

Some of the things I learned while working on it:

* How Spring Boot applications are structured
* How controllers, services and repositories work together
* How JPA maps Java classes to database tables
* How Spring Security handles authentication
* How JWT authentication works
* Why access tokens and refresh tokens are different
* How password hashing works
* How OAuth2 login works
* How database migrations work with Flyway
* How filters work in Spring Security
* How rate limiting can be implemented
* Why validation should happen before business logic
* Why secrets should not be stored directly in source code

A lot of this project was built by trying things, breaking things, debugging them, and then understanding why they worked or didn't work.

## Current Status

🚧 **Learning / In Progress**

The project is functional, but I am still actively learning and improving it.

I don't consider the current code to be the "final" version. As I learn more about Java and Spring Boot, I expect the architecture and implementation to change.

## Why I made this project

I wanted a project where I could learn Spring Boot by actually building something rather than only following tutorials.

Instead of making only a basic CRUD application, I decided to keep adding features that helped me understand different parts of backend development.

This repository is basically my way of documenting my progress while learning Java Spring Boot.

## Author

**Min Thant Tun**

Learning Java + Spring Boot one project at a time.

---

### Note

This is a personal learning project. Some implementation decisions may change as I learn more about Spring Boot, Spring Security, backend architecture, and application security.
