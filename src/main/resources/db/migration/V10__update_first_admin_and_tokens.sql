UPDATE users
SET password_hashed = '$argon2id$v=19$m=65536,t=3,p=1$Fj7G9hX88MiA17l+y1Lb7w$tzn7M0KfBTYSiGaDFtv8TQ8ohg7y7s/IDo5qp67gGkI',
    failed_login_attempts = 0,
    locked_until = NULL,
    updated_at = now()
WHERE username = 'superadmin'
  AND email = 'admin@yourdomain.com'
  AND role = 'ADMIN'
RETURNING id, username, email_verified, status;

UPDATE refresh_tokens
SET revoked = true
WHERE user_id = (
    SELECT id FROM users WHERE username = 'superadmin'
);

UPDATE password_reset_tokens
SET used = true
WHERE user_id = (
    SELECT id FROM users WHERE username = 'superadmin'
);