CREATE TABLE device_tokens (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id      VARCHAR(255) NOT NULL,
    token          TEXT NOT NULL,
    platform       VARCHAR(16) NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_tokens_user_device UNIQUE (user_id, device_id)
);

CREATE INDEX idx_device_tokens_user_active ON device_tokens(user_id) WHERE active;
