ALTER TABLE refresh_tokens
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN user_agent VARCHAR(500),
    ADD COLUMN last_used_at TIMESTAMPTZ NOT NULL DEFAULT now();