ALTER TABLE swipes ALTER COLUMN cat_id DROP NOT NULL;

DROP INDEX idx_swipes_unique;

CREATE UNIQUE INDEX idx_swipes_unique_cat ON swipes(swiper_id, cat_id) WHERE cat_id IS NOT NULL;
CREATE UNIQUE INDEX idx_swipes_unique_human ON swipes(swiper_id, target_user_id) WHERE cat_id IS NULL;
