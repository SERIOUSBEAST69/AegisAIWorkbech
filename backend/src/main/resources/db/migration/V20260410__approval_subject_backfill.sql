-- Backfill approval_request and subject_request to ensure >= 24 records per company.
-- Keeps existing records and only inserts missing rows with traceable unique keys.

INSERT INTO approval_request (
  company_id, applicant_id, asset_id, reason, status, approver_id,
  process_instance_id, task_id, create_time, update_time
)
SELECT
  core.company_id,
  core.applicant_id,
  core.asset_id,
  CONCAT('Cross-department data access approval #', core.company_id, '-BF-', LPAD(seq.n, 3, '0')) AS reason,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'PENDING'
    WHEN 1 THEN 'APPROVED'
    WHEN 2 THEN 'REJECTED'
    ELSE 'APPROVED'
  END AS status,
  CASE WHEN MOD(seq.n, 4) = 0 THEN NULL ELSE core.approver_id END AS approver_id,
  CONCAT('PI-BF-', core.company_id, '-', LPAD(seq.n, 4, '0')) AS process_instance_id,
  CONCAT('TK-BF-', core.company_id, '-', LPAD(seq.n, 4, '0')) AS task_id,
  DATE_SUB(NOW(), INTERVAL (seq.n + 24) DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL (seq.n + 24) HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id LIMIT 1) AS applicant_id,
    (SELECT su.id FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id LIMIT 1 OFFSET 1) AS approver_id,
    (SELECT da.id FROM data_asset da
      WHERE da.company_id = c.company_id
      ORDER BY da.id LIMIT 1) AS asset_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 16 AS n UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
  UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
  UNION ALL SELECT 24
) seq
WHERE core.applicant_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM approval_request ap
    WHERE ap.company_id = core.company_id
      AND ap.process_instance_id = CONCAT('PI-BF-', core.company_id, '-', LPAD(seq.n, 4, '0'))
  );

INSERT INTO subject_request (
  company_id, user_id, type, status, comment, handler_id, result, create_time, update_time
)
SELECT
  core.company_id,
  core.user_id,
  CASE MOD(seq.n, 3)
    WHEN 0 THEN 'access'
    WHEN 1 THEN 'export'
    ELSE 'delete'
  END AS type,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'pending'
    WHEN 1 THEN 'processing'
    WHEN 2 THEN 'done'
    ELSE 'rejected'
  END AS status,
  CONCAT('Data subject rights request #', core.company_id, '-BF-', LPAD(seq.n, 3, '0')) AS comment,
  core.handler_id,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN NULL
    WHEN 1 THEN 'Accepted by governance admin and in process'
    WHEN 2 THEN 'Completed with confirmation receipt'
    ELSE 'Rejected due to missing verification materials'
  END AS result,
  DATE_SUB(NOW(), INTERVAL (seq.n + 24) DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL (seq.n + 24) HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id LIMIT 1) AS user_id,
    (SELECT su.id FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id LIMIT 1 OFFSET 1) AS handler_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 16 AS n UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
  UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
  UNION ALL SELECT 24
) seq
WHERE core.user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM subject_request sr
    WHERE sr.company_id = core.company_id
        AND sr.comment = CONCAT('Data subject rights request #', core.company_id, '-BF-', LPAD(seq.n, 3, '0'))
  );
