CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_messages_conv_created ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_undelivered ON messages(conversation_id, delivered) WHERE NOT delivered;
