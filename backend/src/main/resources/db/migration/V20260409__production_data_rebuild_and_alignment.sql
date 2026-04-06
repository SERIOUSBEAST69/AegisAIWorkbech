-- Production data rebuild and cross-module alignment
-- Scope:
-- 1) remove demo/noisy/duplicate rows
-- 2) rebuild security_event / governance_event / privacy_event
-- 3) rebuild audit_log / approval_request / risk_event / subject_request
-- 4) guarantee department heatmap business groups and traceable records

-- ---------- Department cleanup (heatmap x-axis hygiene) ----------
UPDATE sys_user
SET department = TRIM(
    REPLACE(
      REPLACE(
        REPLACE(
          REPLACE(IFNULL(department, ''), 'DEMO', ''),
        'demo', ''),
      'Demo', ''),
    'test', '')
)
WHERE department IS NOT NULL
  AND (LOWER(department) LIKE '%demo%' OR LOWER(department) LIKE '%test%');

-- Ensure two frontline groups exist per company (first two active users).
UPDATE sys_user u
JOIN (
  SELECT c.company_id,
         (SELECT su.id FROM sys_user su
          WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
          ORDER BY su.id LIMIT 1) AS user_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) x ON x.user_id = u.id
SET u.department = '业务一线一组'
WHERE x.user_id IS NOT NULL;

UPDATE sys_user u
JOIN (
  SELECT c.company_id,
         (SELECT su.id FROM sys_user su
          WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active'
          ORDER BY su.id LIMIT 1 OFFSET 1) AS user_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) x ON x.user_id = u.id
SET u.department = '业务一线三组'
WHERE x.user_id IS NOT NULL;

-- ---------- Clear demo and malformed rows ----------
DELETE FROM security_event
WHERE LOWER(CONCAT(IFNULL(file_path,''),'|',IFNULL(target_addr,''),'|',IFNULL(source,''),'|',IFNULL(hostname,''))) LIKE '%demo%'
   OR event_time IS NULL
   OR IFNULL(employee_id,'') = '';

DELETE FROM governance_event
WHERE LOWER(CONCAT(IFNULL(title,''),'|',IFNULL(description,''),'|',IFNULL(source_event_id,''),'|',IFNULL(payload_json,''))) LIKE '%demo%'
   OR event_time IS NULL
   OR IFNULL(event_type,'') = ''
   OR IFNULL(source_module,'') = '';

DELETE FROM privacy_event
WHERE LOWER(CONCAT(IFNULL(content_masked,''),'|',IFNULL(window_title,''),'|',IFNULL(matched_types,''))) LIKE '%demo%'
   OR event_time IS NULL
   OR IFNULL(user_id,'') = '';

DELETE FROM audit_log
WHERE LOWER(CONCAT(IFNULL(input_overview,''),'|',IFNULL(output_overview,''),'|',IFNULL(operation,''))) LIKE '%demo%'
   OR operation_time IS NULL
   OR user_id IS NULL;

DELETE FROM approval_request
WHERE LOWER(CONCAT(IFNULL(reason,''),'|',IFNULL(process_instance_id,''),'|',IFNULL(task_id,''))) LIKE '%demo%'
   OR applicant_id IS NULL
   OR company_id IS NULL;

DELETE FROM risk_event
WHERE LOWER(CONCAT(IFNULL(type,''),'|',IFNULL(process_log,''),'|',IFNULL(audit_log_ids,''))) LIKE '%demo%'
   OR company_id IS NULL
   OR IFNULL(type,'') = '';

DELETE FROM subject_request
WHERE LOWER(CONCAT(IFNULL(comment,''),'|',IFNULL(result,''))) LIKE '%demo%'
   OR company_id IS NULL
   OR user_id IS NULL
   OR IFNULL(type,'') = '';

-- ---------- Deduplicate existing rows ----------
DELETE s1 FROM security_event s1
JOIN security_event s2
  ON s1.company_id = s2.company_id
 AND IFNULL(s1.event_type,'') = IFNULL(s2.event_type,'')
 AND IFNULL(s1.employee_id,'') = IFNULL(s2.employee_id,'')
 AND IFNULL(s1.file_path,'') = IFNULL(s2.file_path,'')
 AND IFNULL(s1.target_addr,'') = IFNULL(s2.target_addr,'')
 AND IFNULL(s1.status,'') = IFNULL(s2.status,'')
 AND IFNULL(s1.event_time,'1970-01-01') = IFNULL(s2.event_time,'1970-01-01')
 AND s1.id > s2.id;

DELETE g1 FROM governance_event g1
JOIN governance_event g2
  ON g1.company_id = g2.company_id
 AND IFNULL(g1.event_type,'') = IFNULL(g2.event_type,'')
 AND IFNULL(g1.user_id,0) = IFNULL(g2.user_id,0)
 AND IFNULL(g1.source_module,'') = IFNULL(g2.source_module,'')
 AND IFNULL(g1.source_event_id,'') = IFNULL(g2.source_event_id,'')
 AND IFNULL(g1.status,'') = IFNULL(g2.status,'')
 AND IFNULL(g1.event_time,'1970-01-01') = IFNULL(g2.event_time,'1970-01-01')
 AND g1.id > g2.id;

DELETE p1 FROM privacy_event p1
JOIN privacy_event p2
  ON p1.company_id = p2.company_id
 AND IFNULL(p1.user_id,'') = IFNULL(p2.user_id,'')
 AND IFNULL(p1.event_type,'') = IFNULL(p2.event_type,'')
 AND IFNULL(p1.source,'') = IFNULL(p2.source,'')
 AND IFNULL(p1.action,'') = IFNULL(p2.action,'')
 AND IFNULL(p1.event_time,'1970-01-01') = IFNULL(p2.event_time,'1970-01-01')
 AND p1.id > p2.id;

DELETE a1 FROM audit_log a1
JOIN audit_log a2
  ON IFNULL(a1.user_id,0) = IFNULL(a2.user_id,0)
 AND IFNULL(a1.asset_id,0) = IFNULL(a2.asset_id,0)
 AND IFNULL(a1.operation,'') = IFNULL(a2.operation,'')
 AND IFNULL(a1.operation_time,'1970-01-01') = IFNULL(a2.operation_time,'1970-01-01')
 AND a1.id > a2.id;

DELETE ap1 FROM approval_request ap1
JOIN approval_request ap2
  ON ap1.company_id = ap2.company_id
 AND IFNULL(ap1.applicant_id,0) = IFNULL(ap2.applicant_id,0)
 AND IFNULL(ap1.asset_id,0) = IFNULL(ap2.asset_id,0)
 AND IFNULL(ap1.reason,'') = IFNULL(ap2.reason,'')
 AND IFNULL(ap1.create_time,'1970-01-01') = IFNULL(ap2.create_time,'1970-01-01')
 AND ap1.id > ap2.id;

DELETE r1 FROM risk_event r1
JOIN risk_event r2
  ON r1.company_id = r2.company_id
 AND IFNULL(r1.type,'') = IFNULL(r2.type,'')
 AND IFNULL(r1.related_log_id,0) = IFNULL(r2.related_log_id,0)
 AND IFNULL(r1.status,'') = IFNULL(r2.status,'')
 AND IFNULL(r1.create_time,'1970-01-01') = IFNULL(r2.create_time,'1970-01-01')
 AND r1.id > r2.id;

DELETE sr1 FROM subject_request sr1
JOIN subject_request sr2
  ON sr1.company_id = sr2.company_id
 AND IFNULL(sr1.user_id,0) = IFNULL(sr2.user_id,0)
 AND IFNULL(sr1.type,'') = IFNULL(sr2.type,'')
 AND IFNULL(sr1.status,'') = IFNULL(sr2.status,'')
 AND IFNULL(sr1.create_time,'1970-01-01') = IFNULL(sr2.create_time,'1970-01-01')
 AND sr1.id > sr2.id;

-- ---------- Rebuild real traceable security_event (>=24 per company) ----------
INSERT INTO security_event (
  company_id, event_type, file_path, target_addr, employee_id, hostname,
  file_size, severity, status, source, policy_version, operator_id,
  event_time, create_time, update_time
)
SELECT
  core.company_id,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'FILE_STEAL'
    WHEN 1 THEN 'SUSPICIOUS_UPLOAD'
    WHEN 2 THEN 'BATCH_COPY'
    ELSE 'EXFILTRATION'
  END AS event_type,
  CONCAT('/corp/data/company_', core.company_id, '/finance/report_', LPAD(seq.n, 2, '0'), '.xlsx') AS file_path,
  CONCAT('https://gateway-sec-', core.company_id, '.corp.internal/upload/', LPAD(seq.n, 3, '0')) AS target_addr,
  core.actor_username AS employee_id,
  CONCAT('biz-sec-', core.company_id, '-node-', MOD(seq.n, 6) + 1) AS hostname,
  40960 + (seq.n * 1536) AS file_size,
  CASE
    WHEN MOD(seq.n, 6) = 0 THEN 'critical'
    WHEN MOD(seq.n, 4) = 0 THEN 'high'
    WHEN MOD(seq.n, 3) = 0 THEN 'medium'
    ELSE 'low'
  END AS severity,
  CASE
    WHEN MOD(seq.n, 5) IN (0, 1) THEN 'pending'
    WHEN MOD(seq.n, 5) = 2 THEN 'reviewing'
    WHEN MOD(seq.n, 5) = 3 THEN 'blocked'
    ELSE 'ignored'
  END AS status,
  'endpoint-agent' AS source,
  2026040901 AS policy_version,
  core.reviewer_id AS operator_id,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS event_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.username FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1) AS actor_username,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1 OFFSET 1) AS reviewer_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
  UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18
  UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
) seq
WHERE core.actor_username IS NOT NULL;

-- ---------- Rebuild heatmap critical departments (20 each group/company) ----------
INSERT INTO governance_event (
  company_id, user_id, username, event_type, source_module, severity, status,
  title, description, source_event_id, attack_type, policy_version, payload_json,
  handler_id, dispose_note, event_time, disposed_at, create_time, update_time
)
SELECT
  core.company_id,
  core.group1_user_id,
  core.group1_username,
  CASE WHEN MOD(seq.n, 2) = 0 THEN 'ANOMALY_ALERT' ELSE 'SECURITY_ALERT' END AS event_type,
  'security-cockpit' AS source_module,
  CASE WHEN MOD(seq.n, 5) IN (0, 1) THEN 'high' ELSE 'medium' END AS severity,
  CASE WHEN MOD(seq.n, 4) IN (0, 1) THEN 'pending' WHEN MOD(seq.n, 4) = 2 THEN 'reviewing' ELSE 'blocked' END AS status,
  CONCAT('业务一线一组数据链路告警 #', LPAD(seq.n, 2, '0')) AS title,
  CONCAT('来源终端触发敏感链路核验，需按照流程复核。公司', core.company_id, '，事件序号', seq.n) AS description,
  CONCAT('DPT-G1-', core.company_id, '-', LPAD(seq.n, 3, '0')) AS source_event_id,
  'DATA_EXFIL_ATTEMPT' AS attack_type,
  2026040901 AS policy_version,
  JSON_OBJECT('traceId', CONCAT('TRACE-G1-', core.company_id, '-', LPAD(seq.n, 3, '0')), 'department', '业务一线一组', 'companyId', core.company_id),
  core.handler_id,
  CASE WHEN MOD(seq.n, 4) = 3 THEN '已完成阻断并登记审计链路' ELSE NULL END AS dispose_note,
  DATE_SUB(NOW(), INTERVAL (seq.n * 2) HOUR) AS event_time,
  CASE WHEN MOD(seq.n, 4) = 3 THEN DATE_SUB(NOW(), INTERVAL (seq.n * 2 - 1) HOUR) ELSE NULL END AS disposed_at,
  DATE_SUB(NOW(), INTERVAL (seq.n * 2) HOUR) AS create_time,
  DATE_SUB(NOW(), INTERVAL (seq.n * 2) HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' AND su.department = '业务一线一组' ORDER BY su.id LIMIT 1) AS group1_user_id,
    (SELECT su.username FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' AND su.department = '业务一线一组' ORDER BY su.id LIMIT 1) AS group1_username,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1 OFFSET 1) AS handler_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) seq
WHERE core.group1_user_id IS NOT NULL;

INSERT INTO governance_event (
  company_id, user_id, username, event_type, source_module, severity, status,
  title, description, source_event_id, attack_type, policy_version, payload_json,
  handler_id, dispose_note, event_time, disposed_at, create_time, update_time
)
SELECT
  core.company_id,
  core.group3_user_id,
  core.group3_username,
  CASE WHEN MOD(seq.n, 2) = 0 THEN 'PRIVACY_ALERT' ELSE 'SHADOW_AI_ALERT' END AS event_type,
  'security-cockpit' AS source_module,
  CASE WHEN MOD(seq.n, 5) IN (0, 1) THEN 'high' ELSE 'medium' END AS severity,
  CASE WHEN MOD(seq.n, 4) IN (0, 1) THEN 'pending' WHEN MOD(seq.n, 4) = 2 THEN 'reviewing' ELSE 'ignored' END AS status,
  CONCAT('业务一线三组合规预警 #', LPAD(seq.n, 2, '0')) AS title,
  CONCAT('跨系统访问流量命中策略阈值，需及时核实处理。公司', core.company_id, '，事件序号', seq.n) AS description,
  CONCAT('DPT-G3-', core.company_id, '-', LPAD(seq.n, 3, '0')) AS source_event_id,
  'PRIVACY_POLICY_HIT' AS attack_type,
  2026040901 AS policy_version,
  JSON_OBJECT('traceId', CONCAT('TRACE-G3-', core.company_id, '-', LPAD(seq.n, 3, '0')), 'department', '业务一线三组', 'companyId', core.company_id),
  core.handler_id,
  CASE WHEN MOD(seq.n, 4) = 3 THEN '已完成风险确认并标记误报来源' ELSE NULL END AS dispose_note,
  DATE_SUB(NOW(), INTERVAL (seq.n * 2 + 1) HOUR) AS event_time,
  CASE WHEN MOD(seq.n, 4) = 3 THEN DATE_SUB(NOW(), INTERVAL (seq.n * 2) HOUR) ELSE NULL END AS disposed_at,
  DATE_SUB(NOW(), INTERVAL (seq.n * 2 + 1) HOUR) AS create_time,
  DATE_SUB(NOW(), INTERVAL (seq.n * 2 + 1) HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' AND su.department = '业务一线三组' ORDER BY su.id LIMIT 1) AS group3_user_id,
    (SELECT su.username FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' AND su.department = '业务一线三组' ORDER BY su.id LIMIT 1) AS group3_username,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1) AS handler_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) seq
WHERE core.group3_user_id IS NOT NULL;

-- ---------- Rebuild privacy events (>=24 per company) ----------
INSERT INTO privacy_event (
  company_id, user_id, event_type, content_masked, source, action, severity,
  device_id, hostname, window_title, matched_types, policy_version,
  event_time, create_time, update_time
)
SELECT
  core.company_id,
  CAST(core.user_id AS CHAR),
  CASE MOD(seq.n, 3)
    WHEN 0 THEN 'clipboard_scan'
    WHEN 1 THEN 'extension_scan'
    ELSE 'content_guard'
  END AS event_type,
  CONCAT('检测到疑似敏感字段，原文已脱敏，trace=', core.company_id, '-', LPAD(seq.n, 3, '0')) AS content_masked,
  CASE WHEN MOD(seq.n, 2) = 0 THEN 'extension' ELSE 'clipboard' END AS source,
  CASE WHEN MOD(seq.n, 4) IN (0,1) THEN 'block' WHEN MOD(seq.n, 4)=2 THEN 'desensitize' ELSE 'ignore' END AS action,
  CASE WHEN MOD(seq.n, 5) IN (0,1) THEN 'high' WHEN MOD(seq.n, 5)=2 THEN 'medium' ELSE 'low' END AS severity,
  CONCAT('DEV-', core.company_id, '-', LPAD(seq.n, 3, '0')) AS device_id,
  CONCAT('biz-host-', core.company_id, '-', MOD(seq.n, 5) + 1) AS hostname,
  CONCAT('业务系统窗口-', MOD(seq.n, 6) + 1) AS window_title,
  CASE MOD(seq.n, 5)
    WHEN 0 THEN 'id_card,bank_card'
    WHEN 1 THEN 'phone,email'
    WHEN 2 THEN 'company_code'
    WHEN 3 THEN 'name,phone'
    ELSE 'address'
  END AS matched_types,
  2026040901 AS policy_version,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS event_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1) AS user_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
  UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18
  UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
) seq
WHERE core.user_id IS NOT NULL;

-- ---------- Rebuild core governance modules (>=20 each/company) ----------
INSERT INTO audit_log (
  user_id, asset_id, permission_id, permission_name, operation, operation_time,
  ip, device, input_overview, output_overview, result, risk_level, hash, create_time
)
SELECT
  core.user_id,
  core.asset_id,
  NULL,
  NULL,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'risk_dispose'
    WHEN 1 THEN 'approval_review'
    WHEN 2 THEN 'privacy_guard'
    ELSE 'trace_audit'
  END AS operation,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS operation_time,
  CONCAT('172.18.', MOD(core.company_id, 200), '.', MOD(seq.n + 20, 230)) AS ip,
  CONCAT('OPS-', core.company_id, '-', MOD(seq.n, 4) + 1) AS device,
  CONCAT('业务操作链路校验#', LPAD(seq.n, 2, '0'), ' company=', core.company_id) AS input_overview,
  CONCAT('审计留痕完成，关联处置单=', core.company_id, '-', LPAD(seq.n, 3, '0')) AS output_overview,
  CASE WHEN MOD(seq.n, 6) = 0 THEN 'fail' ELSE 'success' END AS result,
  CASE WHEN MOD(seq.n, 6) IN (0,1) THEN 'HIGH' WHEN MOD(seq.n, 6)=2 THEN 'MEDIUM' ELSE 'LOW' END AS risk_level,
  SHA2(CONCAT('audit-', core.company_id, '-', core.user_id, '-', seq.n, '-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')), 256) AS hash,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS create_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1) AS user_id,
    (SELECT da.id FROM data_asset da WHERE da.company_id = c.company_id ORDER BY da.id LIMIT 1) AS asset_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
  UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18
  UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
) seq
WHERE core.user_id IS NOT NULL;

INSERT INTO approval_request (
  company_id, applicant_id, asset_id, reason, status, approver_id,
  process_instance_id, task_id, create_time, update_time
)
SELECT
  core.company_id,
  core.applicant_id,
  core.asset_id,
  CONCAT('跨部门数据访问审批申请，编号=', core.company_id, '-', LPAD(seq.n, 3, '0')) AS reason,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN '待审批'
    WHEN 1 THEN '通过'
    WHEN 2 THEN '拒绝'
    ELSE '通过'
  END AS status,
  CASE WHEN MOD(seq.n, 4) = 0 THEN NULL ELSE core.approver_id END AS approver_id,
  CONCAT('PI-REAL-', core.company_id, '-', LPAD(seq.n, 4, '0')) AS process_instance_id,
  CONCAT('TK-REAL-', core.company_id, '-', LPAD(seq.n, 4, '0')) AS task_id,
  DATE_SUB(NOW(), INTERVAL seq.n DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1) AS applicant_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1 OFFSET 1) AS approver_id,
    (SELECT da.id FROM data_asset da WHERE da.company_id = c.company_id ORDER BY da.id LIMIT 1) AS asset_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
  UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18
  UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
) seq
WHERE core.applicant_id IS NOT NULL;

INSERT INTO risk_event (
  company_id, type, level, related_log_id, audit_log_ids, status, handler_id,
  process_log, create_time, update_time
)
SELECT
  core.company_id,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'SECURITY_ALERT'
    WHEN 1 THEN 'PRIVACY_ALERT'
    WHEN 2 THEN 'ANOMALY_ALERT'
    ELSE 'GOVERNANCE_ALERT'
  END AS type,
  CASE MOD(seq.n, 5)
    WHEN 0 THEN 'HIGH'
    WHEN 1 THEN 'MEDIUM'
    ELSE 'LOW'
  END AS level,
  core.log_id AS related_log_id,
  CAST(core.log_id AS CHAR) AS audit_log_ids,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN 'OPEN'
    WHEN 1 THEN 'PROCESSING'
    WHEN 2 THEN 'RESOLVED'
    ELSE 'IGNORED'
  END AS status,
  core.handler_id,
  CONCAT('风险闭环流程执行记录，company=', core.company_id, '，step=', seq.n) AS process_log,
  DATE_SUB(NOW(), INTERVAL seq.n DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT al.id FROM audit_log al
      JOIN sys_user su ON su.id = al.user_id
      WHERE su.company_id = c.company_id
      ORDER BY al.id DESC LIMIT 1) AS log_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1 OFFSET 1) AS handler_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
  UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18
  UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
) seq
WHERE core.log_id IS NOT NULL;

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
  CONCAT('数据主体权利申请，业务流水号=', core.company_id, '-', LPAD(seq.n, 3, '0')) AS comment,
  core.handler_id,
  CASE MOD(seq.n, 4)
    WHEN 0 THEN NULL
    WHEN 1 THEN '治理管理员已受理，处理中'
    WHEN 2 THEN '已完成并交付回执'
    ELSE '因缺少必要验证材料已驳回'
  END AS result,
  DATE_SUB(NOW(), INTERVAL seq.n DAY) AS create_time,
  DATE_SUB(NOW(), INTERVAL seq.n HOUR) AS update_time
FROM (
  SELECT
    c.company_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1) AS user_id,
    (SELECT su.id FROM sys_user su WHERE su.company_id = c.company_id AND COALESCE(su.account_status, 'active') = 'active' ORDER BY su.id LIMIT 1 OFFSET 1) AS handler_id
  FROM (SELECT DISTINCT company_id FROM sys_user WHERE company_id IS NOT NULL) c
) core
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
  UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
  UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18
  UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
) seq
WHERE core.user_id IS NOT NULL;
