CREATE TABLE user_photos (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    s3_key            VARCHAR(500) NOT NULL,
    thumbnail_s3_key  VARCHAR(500),
    display_order     INT NOT NULL,
    content_type      VARCHAR(50) NOT NULL,
    file_size_bytes   BIGINT NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_photos_user_id ON user_photos(user_id);
CREATE INDEX idx_user_photos_user_order ON user_photos(user_id, display_order);
