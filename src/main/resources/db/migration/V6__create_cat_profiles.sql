CREATE TABLE cat_profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    age         INT NOT NULL,
    age_unit    VARCHAR(10) NOT NULL,
    breed       VARCHAR(100),
    bio         VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cat_profiles_user_id ON cat_profiles(user_id);
