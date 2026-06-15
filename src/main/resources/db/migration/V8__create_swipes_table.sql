CREATE TABLE swipes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    swiper_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cat_id          UUID NOT NULL REFERENCES cat_profiles(id) ON DELETE CASCADE,
    target_user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action          VARCHAR(10) NOT NULL CHECK (action IN ('LIKE', 'PASS')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_swipes_unique ON swipes(swiper_id, cat_id);
CREATE INDEX idx_swipes_target_action ON swipes(target_user_id, action) WHERE action = 'LIKE';
CREATE INDEX idx_swipes_swiper ON swipes(swiper_id);
