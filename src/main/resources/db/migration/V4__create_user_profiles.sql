CREATE TABLE user_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    display_name    VARCHAR(100) NOT NULL,
    bio             VARCHAR(1000),
    date_of_birth   DATE NOT NULL,
    gender          VARCHAR(20) NOT NULL,
    gender_preference VARCHAR(20) NOT NULL,
    age_min         INT NOT NULL,
    age_max         INT NOT NULL,
    max_distance_km INT NOT NULL,
    location        GEOMETRY(POINT, 4326),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_location ON user_profiles USING GIST(location);
