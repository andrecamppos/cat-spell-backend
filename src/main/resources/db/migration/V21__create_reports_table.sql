CREATE TABLE reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category     VARCHAR(32) NOT NULL,
    details      VARCHAR(1000) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reports_no_self CHECK (reporter_id <> reported_id),
    CONSTRAINT chk_reports_category CHECK (category IN ('HARASSMENT','SPAM','FAKE_PROFILE','INAPPROPRIATE_CONTENT','OTHER'))
);

CREATE INDEX idx_reports_reported ON reports(reported_id);
