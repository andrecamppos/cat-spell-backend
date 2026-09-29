-- Relocate date of birth from user_profiles to users as the single source of truth (D-04/D-10).
-- Added NULLABLE so grandfathered accounts with no DOB stay usable and un-gated (D-11).
ALTER TABLE users ADD COLUMN date_of_birth DATE;

-- Backfill existing DOBs from completed profiles. The WHERE ... IS NULL guard makes this
-- idempotent and a safe no-op on an empty table (mirrors V17's grandfather backfill).
-- DOBs are moved verbatim; existing rows are left exactly as they are (D-12).
UPDATE users u
SET date_of_birth = up.date_of_birth
FROM user_profiles up
WHERE up.user_id = u.id
  AND u.date_of_birth IS NULL;

-- Drop the old column only after the backfill so DOB now lives solely on users (D-04).
ALTER TABLE user_profiles DROP COLUMN date_of_birth;
