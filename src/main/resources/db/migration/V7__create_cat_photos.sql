CREATE TABLE cat_photos (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cat_profile_id   UUID NOT NULL REFERENCES cat_profiles(id) ON DELETE CASCADE,
    s3_key           VARCHAR(500) NOT NULL,
    thumbnail_s3_key VARCHAR(500),
    display_order    INT NOT NULL,
    content_type     VARCHAR(50) NOT NULL,
    file_size_bytes  BIGINT NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cat_photos_cat_profile_id ON cat_photos(cat_profile_id);
CREATE INDEX idx_cat_photos_cat_order ON cat_photos(cat_profile_id, display_order);
