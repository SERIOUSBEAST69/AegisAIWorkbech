-- User management cleanup (2026-04-05)
-- Goal: keep records traceable and complete while reducing noisy duplicates.
-- Scope: company_id = 1 demo company.

START TRANSACTION;

-- 1) Fill missing identity fields with traceable fallback values.
UPDATE sys_user
SET real_name = COALESCE(NULLIF(TRIM(real_name), ''), NULLIF(TRIM(nickname), ''), username),
    nickname = COALESCE(NULLIF(TRIM(nickname), ''), NULLIF(TRIM(real_name), ''), username),
    update_time = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND (
    real_name IS NULL OR TRIM(real_name) = ''
    OR nickname IS NULL OR TRIM(nickname) = ''
  );

-- 2) Make repeated regression names uniquely traceable by username suffix.
UPDATE sys_user
SET real_name = CONCAT('Duty Pending User-', username),
    nickname = CONCAT('DutyPending-', username),
    update_time = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND real_name = 'Duty Pending User'
  AND username LIKE 'duty_pending_%';

UPDATE sys_user
SET real_name = CONCAT('Walkthrough User-', username),
    nickname = CONCAT('Walkthrough-', username),
    update_time = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND real_name = 'Walkthrough User'
  AND username LIKE 'walkthrough_%';

-- 3) Pending self-registered accounts keep role empty until admin assignment.
UPDATE sys_user
SET role_id = NULL,
    update_time = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND account_status = 'pending'
  AND role_id IS NOT NULL
  AND (
    username LIKE 'duty_pending_%'
    OR username LIKE 'walkthrough_%'
  );

COMMIT;
