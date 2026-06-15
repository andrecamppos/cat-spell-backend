CREATE TABLE conversation_participants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_at    TIMESTAMPTZ,
    muted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_conv_participants_conv_user ON conversation_participants(conversation_id, user_id);
CREATE INDEX idx_conv_participants_user ON conversation_participants(user_id);
