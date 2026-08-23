INSERT INTO users (id, username, email, phone_number, password_hashed, role, status, created_at, updated_at)
VALUES (
           gen_random_uuid(),
           'superadmin',
           'admin@yourdomain.com',
           '+10000000000',
           '$argon2id$v=19$m=65536,t=3,p=1$NlwnXRHvXUcabAYXBJv4Jw$q+k9oQeWcFgG/NWn/mJZoj4NyiKSUBMrhH6Q9BlppuA',
           'ADMIN',
           'ACTIVE',
           now(),
           now()
       );