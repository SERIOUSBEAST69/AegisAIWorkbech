-- Bulk enrich AI radar + AI analysis hub evidence per active account (company_id=1)
-- Idempotent by marker cleanup before insert.

SET @cid := 1;

DELETE FROM governance_event
WHERE company_id = @cid
  AND (title LIKE 'BULK-ENRICH-20260408-%' OR source_event_id LIKE 'bulk-20260408-%');

DELETE FROM ai_call_log
WHERE input_preview LIKE 'bulk-enrich-20260408%';

DELETE FROM audit_log
WHERE hash LIKE 'bulk20260408-%';

DELETE FROM governance_change_request
WHERE company_id = @cid
  AND payload_json LIKE '%"seedTag":"bulk-enrich-20260408"%';

INSERT INTO governance_event (
  company_id, user_id, username, event_type, source_module, severity, status,
  title, description, source_event_id, attack_type, policy_version, payload_json,
  event_time, create_time, update_time
)
SELECT
  u.company_id,
  u.id,
  u.username,
  CASE (n.n % 4)
    WHEN 0 THEN 'PRIVACY_ALERT'
    WHEN 1 THEN 'ANOMALY_ALERT'
    WHEN 2 THEN 'SHADOW_AI_ALERT'
    ELSE 'SECURITY_ALERT'
  END AS event_type,
  CASE (n.n % 4)
    WHEN 0 THEN 'privacy-shield'
    WHEN 1 THEN 'anomaly-center'
    WHEN 2 THEN 'shadow-ai-discovery'
    ELSE 'threat-monitor'
  END AS source_module,
  CASE
    WHEN n.n IN (3, 7, 11) THEN 'critical'
    WHEN n.n IN (2, 6, 10) THEN 'high'
    WHEN n.n IN (1, 5, 9) THEN 'medium'
    ELSE 'low'
  END AS severity,
  CASE
    WHEN n.n IN (1, 2, 5, 9) THEN 'pending'
    WHEN n.n IN (3, 6, 10) THEN 'reviewing'
    WHEN n.n = 4 THEN 'blocked'
    ELSE 'resolved'
  END AS status,
  CONCAT('BULK-ENRICH-20260408-', u.username, '-', LPAD(n.n, 2, '0')) AS title,
  CONCAT('批量补数治理事件 #', n.n, ' for ', u.username) AS description,
  CONCAT('bulk-20260408-', u.id, '-', n.n) AS source_event_id,
  CASE (n.n % 3)
    WHEN 0 THEN 'data-exfiltration'
    WHEN 1 THEN 'policy-bypass'
    ELSE 'prompt-injection'
  END AS attack_type,
  20260408 AS policy_version,
  JSON_OBJECT('seedTag', 'bulk-enrich-20260408', 'seq', n.n, 'roleCode', COALESCE(r.code, 'UNKNOWN')),
  NOW() - INTERVAL (n.n * 3 + (u.id % 17)) HOUR,
  NOW() - INTERVAL (n.n * 3 + (u.id % 17)) HOUR,
  NOW() - INTERVAL (n.n * 2) HOUR
FROM sys_user u
LEFT JOIN role r ON r.id = u.role_id
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
  SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
  SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
) n
WHERE u.company_id = @cid
  AND COALESCE(u.account_status, 'active') = 'active';

INSERT INTO ai_call_log (
  user_id, data_asset_id, model_id, model_code, provider,
  input_preview, output_preview, status, error_msg, duration_ms, token_usage, ip,
  create_time
)
SELECT
  u.id,
  NULL,
  NULL,
  CASE (n.n % 3)
    WHEN 0 THEN 'deepseek-chat'
    WHEN 1 THEN 'qwen-plus'
    ELSE 'llama3.1-8b'
  END AS model_code,
  CASE (n.n % 3)
    WHEN 0 THEN 'deepseek'
    WHEN 1 THEN 'tongyi'
    ELSE 'ollama'
  END AS provider,
  CONCAT('bulk-enrich-20260408 input #', n.n, ' by ', u.username),
  CONCAT('bulk-enrich-20260408 output #', n.n),
  CASE
    WHEN n.n IN (4, 8, 12) THEN 'timeout'
    WHEN n.n IN (6, 11) THEN 'fail'
    ELSE 'success'
  END AS status,
  CASE
    WHEN n.n IN (4, 8, 12) THEN 'gateway timeout'
    WHEN n.n IN (6, 11) THEN 'provider error'
    ELSE NULL
  END AS error_msg,
  CASE
    WHEN n.n IN (4, 8, 12) THEN 4200 + (n.n * 70)
    WHEN n.n IN (6, 11) THEN 2600 + (n.n * 45)
    ELSE 600 + (n.n * 110)
  END AS duration_ms,
  600 + (n.n * 35),
  CONCAT('10.1.', (u.id % 100), '.', (20 + n.n)),
  NOW() - INTERVAL (n.n * 2 + (u.id % 9)) HOUR
FROM sys_user u
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
  SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
  SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL
  SELECT 13 UNION ALL SELECT 14
) n
WHERE u.company_id = @cid
  AND COALESCE(u.account_status, 'active') = 'active';

INSERT INTO audit_log (
  user_id, asset_id, operation, operation_time, ip, device,
  input_overview, output_overview, result, risk_level, hash, create_time
)
SELECT
  u.id,
  NULL,
  CASE (n.n % 5)
    WHEN 0 THEN 'EXPORT'
    WHEN 1 THEN 'SHARE'
    WHEN 2 THEN 'DOWNLOAD'
    WHEN 3 THEN 'QUERY'
    ELSE 'APPROVE'
  END AS operation,
  NOW() - INTERVAL (n.n * 4 + (u.id % 13)) HOUR,
  CONCAT('10.2.', (u.id % 120), '.', (30 + n.n)),
  CONCAT('bulk-device-', (u.id % 50), '-', n.n),
  CONCAT('bulk-enrich audit input #', n.n, ' by ', u.username),
  CONCAT('bulk-enrich audit output #', n.n),
  CASE WHEN n.n IN (4, 9) THEN 'fail' ELSE 'success' END AS result,
  CASE
    WHEN n.n IN (3, 4, 8, 12) THEN 'critical'
    WHEN n.n IN (2, 6, 10) THEN 'high'
    WHEN n.n IN (1, 5, 9) THEN 'medium'
    ELSE 'low'
  END AS risk_level,
  CONCAT('bulk20260408-', u.id, '-', n.n),
  NOW() - INTERVAL (n.n * 4 + (u.id % 13)) HOUR
FROM sys_user u
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
  SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL
  SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
) n
WHERE u.company_id = @cid
  AND COALESCE(u.account_status, 'active') = 'active';

INSERT INTO governance_change_request (
  company_id, module, action, target_id, payload_json, status, risk_level,
  requester_id, requester_role_code, create_time, update_time
)
SELECT
  u.company_id,
  CASE (n.n % 4)
    WHEN 0 THEN 'ROLE'
    WHEN 1 THEN 'PERMISSION'
    WHEN 2 THEN 'POLICY'
    ELSE 'USER'
  END AS module,
  CASE (n.n % 3)
    WHEN 0 THEN 'ADD'
    WHEN 1 THEN 'UPDATE'
    ELSE 'DELETE'
  END AS action,
  NULL,
  JSON_OBJECT(
    'title', CONCAT('批量补数审批单-', u.username, '-', n.n),
    'reason', '批量构造待我审批数据用于页面压测与演示',
    'impact', CONCAT('scope=', COALESCE(r.code, 'UNKNOWN'), ', seq=', n.n),
    'seedTag', 'bulk-enrich-20260408',
    'trace', JSON_OBJECT('username', u.username, 'role', COALESCE(r.code, 'UNKNOWN'), 'device', CONCAT('bulk-host-', (u.id % 40)))
  ),
  'pending',
  'HIGH',
  u.id,
  COALESCE(r.code, 'UNKNOWN'),
  NOW() - INTERVAL (n.n * 6 + (u.id % 11)) HOUR,
  NOW() - INTERVAL (n.n * 3) HOUR
FROM sys_user u
LEFT JOIN role r ON r.id = u.role_id
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
) n
WHERE u.company_id = @cid
  AND COALESCE(u.account_status, 'active') = 'active'
  AND COALESCE(r.code, '') NOT IN ('ADMIN', 'ADMIN_REVIEWER');

SELECT
  (SELECT COUNT(1) FROM governance_event WHERE company_id = @cid AND title LIKE 'BULK-ENRICH-20260408-%') AS governance_events_seeded,
  (SELECT COUNT(1) FROM ai_call_log WHERE input_preview LIKE 'bulk-enrich-20260408%') AS ai_call_logs_seeded,
  (SELECT COUNT(1) FROM audit_log WHERE hash LIKE 'bulk20260408-%') AS audit_logs_seeded,
  (SELECT COUNT(1) FROM governance_change_request WHERE company_id = @cid AND payload_json LIKE '%"seedTag":"bulk-enrich-20260408"%') AS governance_change_seeded;
