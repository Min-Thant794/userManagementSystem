CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       username VARCHAR(50) UNIQUE,
                       email VARCHAR(255) UNIQUE NOT NULL,
                       phone_number VARCHAR(20) UNIQUE,
                       password_hashed VARCHAR(255),
                       role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK ( role IN ('USER', 'ADMIN') ),
                       status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK ( status IN ('ACTIVE', 'SUSPENDED') ),
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_phone_number ON users (phone_number);

CREATE TABLE oauth_accounts (
                                id BIGSERIAL PRIMARY KEY ,
                                user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                provider VARCHAR(20) NOT NULL,
                                provider_user_id VARCHAR(255) NOT NULL,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                CONSTRAINT uq_oauth_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_oauth_accounts_user_id ON oauth_accounts (user_id);

CREATE TABLE refresh_tokens (
                                id BIGSERIAL PRIMARY KEY,
                                user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                token_hash VARCHAR(255) NOT NULL,
                                expires_at TIMESTAMPTZ NOT NULL,
                                revoked BOOLEAN NOT NULL DEFAULT false,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

CREATE TABLE audit_logs (
                            id BIGSERIAL PRIMARY KEY,
                            actor_user_id UUID REFERENCES users (id),
                            target_user_id UUID REFERENCES users (id),
                            action VARCHAR(30) NOT NULL CHECK ( action IN ('ROLE_CHANGE', 'SUSPEND', 'REACTIVATE', 'UPDATE', 'ADMIN_CREATED') ),
                            details JSONB,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_target ON audit_logs (target_user_id);