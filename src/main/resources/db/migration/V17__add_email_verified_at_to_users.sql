ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;

-- Grandfather every pre-existing account so nobody is locked out on rollout (VERIFY-05, D-06/D-09).
-- The WHERE ... IS NULL guard makes this idempotent and a safe no-op on an empty users table.
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;
