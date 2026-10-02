-- Waitlist / landing-page join (Phase 17).
-- waitlist_entries: one row per normalized email. normalized_email is the dedupe + per-email throttle key
-- (trim + lowercase + local-part '+suffix' stripped, dots preserved — D-03); email is the delivery address
-- (trimmed, case preserved, last submitted while PENDING). Only the SHA-256 hex of the confirm token is stored
-- (confirm_token_hash) — the raw token is never persisted (D-08); rotation overwrites it, so a prior link dies.
-- Deliberately email-only: NO IP address, user agent, referrer or profile columns (D-05).
CREATE TABLE waitlist_entries (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                     VARCHAR(255) NOT NULL,
    normalized_email          VARCHAR(255) NOT NULL,
    status                    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    confirm_token_hash        VARCHAR(64),
    confirm_token_expires_at  TIMESTAMPTZ,
    confirmed_at              TIMESTAMPTZ,
    invited_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_waitlist_entries_normalized_email UNIQUE (normalized_email),
    CONSTRAINT uq_waitlist_entries_confirm_token_hash UNIQUE (confirm_token_hash),
    CONSTRAINT chk_waitlist_entries_status CHECK (status IN ('PENDING','CONFIRMED','INVITED'))
);

CREATE INDEX idx_waitlist_entries_status_confirmed_at ON waitlist_entries(status, confirmed_at);
