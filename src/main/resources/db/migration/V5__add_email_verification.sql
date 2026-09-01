ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;

-- Existing admins (including the seeded superadmin) shouldn't be retroactively
-- locked out by a feature that didn't exist when they were created.
UPDATE users SET email_verified = true WHERE role = 'ADMIN';

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_tokens_token_has ON email_verification_tokens (token_hash);