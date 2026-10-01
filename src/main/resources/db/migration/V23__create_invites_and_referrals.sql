-- Invite-only access + referral attribution (Phase 16).
-- invites: one row per issued code. Only the SHA-256 hex of the code is stored (code_hash) —
-- the raw code is never persisted (D-06). consumed_at is the single-use claim target: NULL = unconsumed,
-- set atomically via a conditional UPDATE (D-07). referrer_user_id is NULLABLE so operator/bootstrap
-- codes carry no referrer (D-11). Deliberately NO expiry column and NO revocation/status column — expiry (D-04)
-- and revocation (D-05) are out of scope for this phase.
CREATE TABLE invites (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_hash         VARCHAR(64) NOT NULL UNIQUE,
    referrer_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    consumed_at       TIMESTAMPTZ,
    consumed_by       UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invites_referrer ON invites(referrer_user_id);

-- referrals: attribution written only when a consumed invite had a real referrer. One attribution per new
-- account (uq_referrals_invitee), defensive no-self guard (D-12), and FK integrity to users/invites.
CREATE TABLE referrals (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invite_id    UUID NOT NULL REFERENCES invites(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_referrals_no_self CHECK (referrer_id <> invitee_id),
    CONSTRAINT uq_referrals_invitee UNIQUE (invitee_id)
);

CREATE INDEX idx_referrals_referrer ON referrals(referrer_id);
