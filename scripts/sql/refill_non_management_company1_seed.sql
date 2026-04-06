-- Refill non-management module data for company_id=1
-- Guarantees: complete fields, traceable to in-company accounts, >20 unique rows per table.

USE aegisai;
SET @target_company := 1;
SET @admin_id := 2028091269201293314;

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS seq24;
CREATE TEMPORARY TABLE seq24 (n INT PRIMARY KEY);
INSERT INTO seq24 (n) VALUES
  (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),
  (13),(14),(15),(16),(17),(18),(19),(20),(21),(22),(23),(24);

-- 1) subject_request: 24 unique rows
INSERT INTO subject_request (
  company_id, user_id, type, status, comment, handler_id, result, create_time, update_time
)
SELECT
  @target_company,
  CASE MOD(n,10)
    WHEN 1 THEN 2028091269201293314
    WHEN 2 THEN 2028091269201293316
    WHEN 3 THEN 2028091269201293317
    WHEN 4 THEN 2028091269201293318
    WHEN 5 THEN 2028091269201293319
    WHEN 6 THEN 2028091269201293320
    WHEN 7 THEN 2028091269201293321
    WHEN 8 THEN 2028091269201293322
    WHEN 9 THEN 2028091269201293323
    ELSE 2028091269201293324
  END AS user_id,
  CASE MOD(n,3)
    WHEN 0 THEN 'access'
    WHEN 1 THEN 'export'
    ELSE 'delete'
  END AS type,
  CASE MOD(n,4)
    WHEN 0 THEN 'pending'
    WHEN 1 THEN 'processing'
    WHEN 2 THEN 'done'
    ELSE 'rejected'
  END AS status,
  CONCAT(
    '主体权利工单补种 #', LPAD(n,2,'0'),
    ' [TRACE username=',
    CASE MOD(n,10)
      WHEN 1 THEN 'admin'
      WHEN 2 THEN 'sec01'
      WHEN 3 THEN 'data01'
      WHEN 4 THEN 'audit01'
      WHEN 5 THEN 'exec.demo'
      WHEN 6 THEN 'secops.demo'
      WHEN 7 THEN 'data.demo'
      WHEN 8 THEN 'builder.demo'
      WHEN 9 THEN 'biz.demo'
      ELSE 'employee.demo'
    END,
    ' userId=',
    CASE MOD(n,10)
      WHEN 1 THEN '2028091269201293314'
      WHEN 2 THEN '2028091269201293316'
      WHEN 3 THEN '2028091269201293317'
      WHEN 4 THEN '2028091269201293318'
      WHEN 5 THEN '2028091269201293319'
      WHEN 6 THEN '2028091269201293320'
      WHEN 7 THEN '2028091269201293321'
      WHEN 8 THEN '2028091269201293322'
      WHEN 9 THEN '2028091269201293323'
      ELSE '2028091269201293324'
    END,
    ' role=EMPLOYEE department=治理运营 center=privacy companyId=1 device=seed-device-', LPAD(n,2,'0'),
    ']'
  ) AS comment,
  CASE WHEN MOD(n,4)=0 THEN NULL ELSE @admin_id END AS handler_id,
  CASE MOD(n,4)
    WHEN 0 THEN NULL
    WHEN 1 THEN CONCAT('处理中：补种工单', LPAD(n,2,'0'))
    WHEN 2 THEN CONCAT('已完成：补种工单', LPAD(n,2,'0'))
    ELSE CONCAT('已驳回：补种工单', LPAD(n,2,'0'))
  END AS result,
  DATE_SUB(NOW(), INTERVAL (48 - n) HOUR) AS create_time,
  DATE_SUB(NOW(), INTERVAL (24 - n) HOUR) AS update_time
FROM seq24;

-- 2) risk_event: 24 unique rows
INSERT INTO risk_event (
  type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time, company_id
)
SELECT
  CONCAT('seed_risk_type_', LPAD(n,2,'0')),
  CASE MOD(n,4)
    WHEN 0 THEN 'LOW'
    WHEN 1 THEN 'MEDIUM'
    WHEN 2 THEN 'HIGH'
    ELSE 'CRITICAL'
  END,
  8000 + n,
  CONCAT(9000 + n),
  CASE MOD(n,4)
    WHEN 0 THEN 'OPEN'
    WHEN 1 THEN 'PROCESSING'
    WHEN 2 THEN 'RESOLVED'
    ELSE 'IGNORED'
  END,
  @admin_id,
  CONCAT('风险记录补种 #', LPAD(n,2,'0'), ' [TRACE operator=admin role=ADMIN department=治理中台 position=治理管理员 companyId=1 device=seed-risk-', LPAD(n,2,'0'), ']'),
  DATE_SUB(NOW(), INTERVAL (72 - n) HOUR),
  DATE_SUB(NOW(), INTERVAL (36 - n) HOUR),
  @target_company
FROM seq24;

-- 3) security_event: 24 unique rows
INSERT INTO security_event (
  company_id, event_type, file_path, target_addr, employee_id, hostname, file_size, severity, status, source, operator_id, event_time, create_time, update_time
)
SELECT
  @target_company,
  CASE MOD(n,4)
    WHEN 0 THEN 'FILE_STEAL'
    WHEN 1 THEN 'SUSPICIOUS_UPLOAD'
    WHEN 2 THEN 'EXFILTRATION'
    ELSE 'CREDENTIAL_DUMP'
  END,
  CONCAT('/seed/company1/file_', LPAD(n,2,'0'), '.dat'),
  CONCAT('203.0.113.', 20 + n),
  CASE MOD(n,10)
    WHEN 1 THEN 'admin'
    WHEN 2 THEN 'sec01'
    WHEN 3 THEN 'data01'
    WHEN 4 THEN 'audit01'
    WHEN 5 THEN 'exec.demo'
    WHEN 6 THEN 'secops.demo'
    WHEN 7 THEN 'data.demo'
    WHEN 8 THEN 'builder.demo'
    WHEN 9 THEN 'biz.demo'
    ELSE 'employee.demo'
  END,
  CONCAT('seed-host-', LPAD(n,2,'0')),
  1024 * n,
  CASE MOD(n,4)
    WHEN 0 THEN 'critical'
    WHEN 1 THEN 'high'
    WHEN 2 THEN 'medium'
    ELSE 'low'
  END,
  CASE MOD(n,4)
    WHEN 0 THEN 'pending'
    WHEN 1 THEN 'reviewing'
    WHEN 2 THEN 'blocked'
    ELSE 'ignored'
  END,
  'seed-refill',
  @admin_id,
  DATE_SUB(NOW(), INTERVAL (96 - n) HOUR),
  DATE_SUB(NOW(), INTERVAL (96 - n) HOUR),
  DATE_SUB(NOW(), INTERVAL (48 - n) HOUR)
FROM seq24;

-- 4) approval_request: 24 unique rows
INSERT INTO approval_request (
  company_id, applicant_id, asset_id, reason, status, approver_id, process_instance_id, task_id, create_time, update_time
)
SELECT
  @target_company,
  CASE MOD(n,10)
    WHEN 1 THEN 2028091269201293314
    WHEN 2 THEN 2028091269201293316
    WHEN 3 THEN 2028091269201293317
    WHEN 4 THEN 2028091269201293318
    WHEN 5 THEN 2028091269201293319
    WHEN 6 THEN 2028091269201293320
    WHEN 7 THEN 2028091269201293321
    WHEN 8 THEN 2028091269201293322
    WHEN 9 THEN 2028091269201293323
    ELSE 2028091269201293324
  END,
  NULL,
  CONCAT('审批工单补种 #', LPAD(n,2,'0'), ' [TRACE username=',
    CASE MOD(n,10)
      WHEN 1 THEN 'admin'
      WHEN 2 THEN 'sec01'
      WHEN 3 THEN 'data01'
      WHEN 4 THEN 'audit01'
      WHEN 5 THEN 'exec.demo'
      WHEN 6 THEN 'secops.demo'
      WHEN 7 THEN 'data.demo'
      WHEN 8 THEN 'builder.demo'
      WHEN 9 THEN 'biz.demo'
      ELSE 'employee.demo'
    END,
    ' companyId=1 device=seed-approval-', LPAD(n,2,'0'), ']'),
  CASE MOD(n,4)
    WHEN 0 THEN '待审批'
    WHEN 1 THEN '通过'
    WHEN 2 THEN '拒绝'
    ELSE '合规审批通过'
  END,
  @admin_id,
  CONCAT('SEED-PI-', LPAD(n,4,'0')),
  CONCAT('SEED-TASK-', LPAD(n,4,'0')),
  DATE_SUB(NOW(), INTERVAL (120 - n) HOUR),
  DATE_SUB(NOW(), INTERVAL (60 - n) HOUR)
FROM seq24;

COMMIT;

SELECT 'subject_request' AS table_name, COUNT(*) AS rows_kept FROM subject_request WHERE company_id = @target_company
UNION ALL
SELECT 'risk_event', COUNT(*) FROM risk_event WHERE company_id = @target_company
UNION ALL
SELECT 'security_event', COUNT(*) FROM security_event WHERE company_id = @target_company
UNION ALL
SELECT 'approval_request', COUNT(*) FROM approval_request WHERE company_id = @target_company;
