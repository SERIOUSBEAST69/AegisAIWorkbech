-- Governance data rebuild (no demo payload) + Privacy Shield task rebuild
-- Scope: audit_log / approval_request / risk_event / sensitive_scan_task

ALTER TABLE sensitive_scan_task ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE sensitive_scan_task ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE sensitive_scan_task ADD COLUMN IF NOT EXISTS trace_json LONGTEXT;
ALTER TABLE sensitive_scan_task ADD COLUMN IF NOT EXISTS report_data LONGTEXT;

-- 1) Clean previous seed and demo-like payloads
DELETE FROM audit_log
WHERE input_overview LIKE '[SEED-20260408]%'
   OR output_overview LIKE '[SEED-20260408]%'
   OR input_overview LIKE '%demo%'
   OR output_overview LIKE '%demo%';

DELETE FROM approval_request
WHERE reason LIKE '[SEED-20260408]%'
   OR reason LIKE '%demo%';

DELETE FROM risk_event
WHERE process_log LIKE '[SEED-20260408]%'
   OR process_log LIKE '%demo%';

DELETE FROM sensitive_scan_task
WHERE source_path LIKE '[SEED-20260408]%'
   OR source_path LIKE '%demo%'
   OR trace_json LIKE '%demo%';

-- 2) Seed audit logs: 24 rows per company
INSERT INTO audit_log(
  user_id, asset_id, operation, operation_time, ip, device,
  input_overview, output_overview, result, risk_level, hash, create_time
)
SELECT
  core.applicant_id,
  core.asset_id,
  CASE MOD(seq.n, 6)
    WHEN 0 THEN 'approval_review'
    WHEN 1 THEN 'risk_dispose'
    WHEN 2 THEN 'policy_sync'
    WHEN 3 THEN 'security_block'
    WHEN 4 THEN 'privacy_scan'
    ELSE 'audit_trace'
  END AS operation,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS operation_time,
  CONCAT('10.', MOD(core.company_id, 200), '.', MOD(core.applicant_id, 200), '.', seq.n) AS ip,
  CONCAT('Aegis-Workstation-', core.company_id) AS device,
  CONCAT('[SEED-20260408] 审计链路样本 #', seq.n, ' company=', core.company_id) AS input_overview,
  CONCAT('[SEED-20260408] 处置闭环记录 #', seq.n) AS output_overview,
  CASE WHEN MOD(seq.n, 7) = 0 THEN 'fail' ELSE 'success' END AS result,
  CASE
    WHEN MOD(seq.n, 5) = 0 THEN 'HIGH'
    WHEN MOD(seq.n, 3) = 0 THEN 'MEDIUM'
    ELSE 'LOW'
  END AS risk_level,
  SHA2(CONCAT(core.company_id, '-', core.applicant_id, '-', seq.n, '-', NOW()), 256) AS hash,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS create_time
FROM (
  SELECT
    c.company_id,
    (
      SELECT su.id
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1
    ) AS applicant_id,
    (
      SELECT da.id
      FROM data_asset da
      WHERE da.company_id = c.company_id
      ORDER BY da.id
      LIMIT 1
    ) AS asset_id
  FROM (
    SELECT DISTINCT company_id
    FROM sys_user
    WHERE company_id IS NOT NULL
  ) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
  UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18
  UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
) seq
WHERE core.applicant_id IS NOT NULL;

-- 3) Seed approval requests: 12 rows per company
INSERT INTO approval_request(
  company_id, applicant_id, asset_id, reason, status, approver_id,
  process_instance_id, task_id, create_time, update_time
)
SELECT
  core.company_id,
  core.applicant_id,
  core.asset_id,
  CONCAT('[SEED-20260408] 数据访问复核申请 #', seq.n, '（生产治理演练）') AS reason,
  CASE MOD(seq.n, 3)
    WHEN 0 THEN '通过'
    WHEN 1 THEN '待审批'
    ELSE '拒绝'
  END AS status,
  CASE MOD(seq.n, 3)
    WHEN 1 THEN NULL
    ELSE core.reviewer_id
  END AS approver_id,
  CONCAT('PI-20260408-', core.company_id, '-', seq.n) AS process_instance_id,
  CONCAT('TK-20260408-', core.company_id, '-', seq.n) AS task_id,
  DATE_SUB(NOW(), INTERVAL seq.n DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (
      SELECT su.id
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1
    ) AS applicant_id,
    (
      SELECT su.id
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1 OFFSET 1
    ) AS reviewer_id,
    (
      SELECT da.id
      FROM data_asset da
      WHERE da.company_id = c.company_id
      ORDER BY da.id
      LIMIT 1
    ) AS asset_id
  FROM (
    SELECT DISTINCT company_id
    FROM sys_user
    WHERE company_id IS NOT NULL
  ) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
) seq
WHERE core.applicant_id IS NOT NULL;

-- 4) Seed risk events: 12 rows per company
INSERT INTO risk_event(
  company_id, type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time
)
SELECT
  core.company_id,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'SECURITY_ALERT'
    WHEN 1 THEN 'PRIVACY_ALERT'
    WHEN 2 THEN 'ANOMALY_ALERT'
    ELSE 'GOVERNANCE_ALERT'
  END AS type,
  CASE MOD(seq.n, 3)
    WHEN 0 THEN 'HIGH'
    WHEN 1 THEN 'MEDIUM'
    ELSE 'LOW'
  END AS level,
  (
    SELECT al.id
    FROM audit_log al
    WHERE al.user_id = core.applicant_id
      AND al.input_overview LIKE CONCAT('[SEED-20260408] 审计链路样本 #', seq.n, '%')
    ORDER BY al.id DESC
    LIMIT 1
  ) AS related_log_id,
  (
    SELECT CAST(al.id AS CHAR)
    FROM audit_log al
    WHERE al.user_id = core.applicant_id
      AND al.input_overview LIKE CONCAT('[SEED-20260408] 审计链路样本 #', seq.n, '%')
    ORDER BY al.id DESC
    LIMIT 1
  ) AS audit_log_ids,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'OPEN'
    WHEN 1 THEN 'PROCESSING'
    WHEN 2 THEN 'RESOLVED'
    ELSE 'IGNORED'
  END AS status,
  COALESCE(core.reviewer_id, core.applicant_id) AS handler_id,
  CONCAT('[SEED-20260408] 风险闭环记录 #', seq.n, ' company=', core.company_id) AS process_log,
  DATE_SUB(NOW(), INTERVAL seq.n DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (
      SELECT su.id
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1
    ) AS applicant_id,
    (
      SELECT su.id
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1 OFFSET 1
    ) AS reviewer_id
  FROM (
    SELECT DISTINCT company_id
    FROM sys_user
    WHERE company_id IS NOT NULL
  ) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
) seq
WHERE core.applicant_id IS NOT NULL;

-- 5) Rebuild Privacy Shield scan tasks: 16 rows per company, no demo / no employee portrait payload
INSERT INTO sensitive_scan_task(
  company_id, user_id, source_type, source_path, trace_json,
  status, sensitive_ratio, report_path, report_data, create_time, update_time
)
SELECT
  core.company_id,
  core.applicant_id,
  CASE WHEN MOD(seq.n, 2) = 0 THEN 'db' ELSE 'file' END AS source_type,
  CASE
    WHEN MOD(seq.n, 2) = 0 THEN CONCAT('[SEED-20260408] company_', core.company_id, '.asset_', COALESCE(core.asset_id, 0), '_table_', seq.n)
    ELSE CONCAT('[SEED-20260408] /secure/company_', core.company_id, '/asset_', COALESCE(core.asset_id, 0), '/snapshot_', seq.n, '.csv')
  END AS source_path,
  JSON_OBJECT(
    'username', core.applicant_username,
    'userId', core.applicant_id,
    'role', core.role_code,
    'department', core.department,
    'companyId', core.company_id,
    'device', CONCAT('Aegis-Workstation-', core.company_id)
  ) AS trace_json,
  CASE WHEN MOD(seq.n, 4) = 0 THEN 'pending' ELSE 'done' END AS status,
  CASE MOD(seq.n, 5)
    WHEN 0 THEN 62.5
    WHEN 1 THEN 48.0
    WHEN 2 THEN 31.5
    WHEN 3 THEN 17.2
    ELSE 9.8
  END AS sensitive_ratio,
  CONCAT('/reports/privacy-shield/company_', core.company_id, '_task_', seq.n, '.json') AS report_path,
  CASE
    WHEN MOD(seq.n, 4) = 0 THEN NULL
    ELSE JSON_OBJECT(
      'summary', JSON_OBJECT('total', 1, 'ratio',
        CASE MOD(seq.n, 5)
          WHEN 0 THEN 62.5
          WHEN 1 THEN 48.0
          WHEN 2 THEN 31.5
          WHEN 3 THEN 17.2
          ELSE 9.8
        END,
        'sensitiveFields', JSON_ARRAY('id_card', 'phone')
      ),
      'results', JSON_ARRAY(JSON_OBJECT('text', '字段命中已脱敏，仅展示治理摘要', 'label', 'id_card', 'score', 0.98))
    )
  END AS report_data,
  DATE_SUB(NOW(), INTERVAL seq.n DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (
      SELECT su.id
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1
    ) AS applicant_id,
    (
      SELECT su.username
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1
    ) AS applicant_username,
    (
      SELECT COALESCE(NULLIF(TRIM(su.department), ''), '-')
      FROM sys_user su
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1
    ) AS department,
    (
      SELECT COALESCE(r.code, '')
      FROM sys_user su
      LEFT JOIN role r ON r.id = su.role_id
      WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
      ORDER BY su.id
      LIMIT 1
    ) AS role_code,
    (
      SELECT da.id
      FROM data_asset da
      WHERE da.company_id = c.company_id
      ORDER BY da.id
      LIMIT 1
    ) AS asset_id
  FROM (
    SELECT DISTINCT company_id
    FROM sys_user
    WHERE company_id IS NOT NULL
  ) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
  UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16
) seq
WHERE core.applicant_id IS NOT NULL;