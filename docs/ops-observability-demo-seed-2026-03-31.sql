-- 真实结构演示数据（可重复执行）
-- 用途：为运维观测、告警中心、影子AI发现、首页趋势等页面提供可视化数据底座。
-- 说明：仅插入带 DEMO_20260331 标识的数据，不会影响已有业务数据。

SET @company_id := 1;

-- 兼容未初始化 client 表的环境
CREATE TABLE IF NOT EXISTS client_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  client_id VARCHAR(64) NOT NULL,
  hostname VARCHAR(255),
  ip_address VARCHAR(64),
  os_username VARCHAR(255),
  os_type VARCHAR(32),
  client_version VARCHAR(32),
  discovered_services TEXT,
  shadow_ai_count INT DEFAULT 0,
  risk_level VARCHAR(20) DEFAULT 'none',
  scan_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS client_scan_queue (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  platform VARCHAR(32) NOT NULL,
  hostname VARCHAR(255),
  os_username VARCHAR(255),
  user_agent VARCHAR(512),
  status VARCHAR(32) DEFAULT 'queued',
  scan_result TEXT,
  download_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 1) 数据资产演示数据
INSERT INTO data_asset (company_id, name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description, create_time, update_time)
SELECT
  @company_id,
  t.name,
  t.type,
  t.sensitivity_level,
  t.location,
  NOW() - INTERVAL t.days_ago DAY,
  (SELECT id FROM sys_user WHERE company_id = @company_id AND username = 'dataadmin' ORDER BY id ASC LIMIT 1),
  t.lineage,
  t.description,
  NOW() - INTERVAL t.days_ago DAY,
  NOW() - INTERVAL t.days_ago DAY
FROM (
  SELECT 'DEMO_20260331_客户画像主表' AS name, 'MySQL' AS type, 'high' AS sensitivity_level,
         'mysql://core/customer_profile' AS location, '{"upstream":["crm_user","order_fact"]}' AS lineage,
         '客户画像核心资产，含手机号与标签。' AS description, 5 AS days_ago
  UNION ALL
  SELECT 'DEMO_20260331_营销触达名单', 'CSV', 'critical',
         'oss://governance/marketing/target-list.csv', '{"upstream":["segment_engine"]}',
         '营销活动名单，包含脱敏前联系方式。', 3
  UNION ALL
  SELECT 'DEMO_20260331_AI训练样本桶', 'ObjectStorage', 'high',
         's3://aegis-ai/train-corpus/', '{"upstream":["chat_audit","privacy_event"]}',
         '用于模型迭代的训练语料存储桶。', 2
) t
WHERE NOT EXISTS (
  SELECT 1 FROM data_asset a
  WHERE a.company_id = @company_id AND a.name = t.name
);

-- 2) 影子AI终端（client_report）演示数据
INSERT INTO client_report (
  company_id, client_id, hostname, ip_address, os_username, os_type, client_version,
  discovered_services, shadow_ai_count, risk_level, scan_time, create_time, update_time
)
SELECT
  @company_id,
  t.client_id,
  t.hostname,
  t.ip_address,
  t.os_username,
  t.os_type,
  '1.0.3',
  t.discovered_services,
  t.shadow_ai_count,
  t.risk_level,
  NOW() - INTERVAL t.minutes_ago MINUTE,
  NOW() - INTERVAL t.minutes_ago MINUTE,
  NOW() - INTERVAL t.minutes_ago MINUTE
FROM (
  SELECT 'demo-client-20260331-01' AS client_id, 'WS-SECOPS-01' AS hostname, '10.10.1.21' AS ip_address,
         'secops' AS os_username, 'Windows' AS os_type,
         '[{"name":"ChatGPT","domain":"chat.openai.com","riskLevel":"high"},{"name":"Claude","domain":"claude.ai","riskLevel":"medium"}]' AS discovered_services,
         2 AS shadow_ai_count, 'high' AS risk_level, 8 AS minutes_ago
  UNION ALL
  SELECT 'demo-client-20260331-02', 'WS-MKT-07', '10.10.1.47',
         'employee', 'Windows',
         '[{"name":"Kimi","domain":"kimi.moonshot.cn","riskLevel":"low"}]',
         1, 'low', 14
  UNION ALL
  SELECT 'demo-client-20260331-03', 'NB-DATA-11', '10.10.2.11',
         'dataadmin', 'macOS',
         '[{"name":"Doubao","domain":"www.doubao.com","riskLevel":"medium"},{"name":"Gemini","domain":"gemini.google.com","riskLevel":"medium"}]',
         2, 'medium', 22
) t
WHERE NOT EXISTS (
  SELECT 1 FROM client_report c
  WHERE c.company_id = @company_id AND c.client_id = t.client_id
);

-- 3) 客户端下载扫描队列演示数据
INSERT INTO client_scan_queue (
  company_id, platform, hostname, os_username, user_agent, status, scan_result, download_time, create_time, update_time
)
SELECT
  @company_id,
  t.platform,
  t.hostname,
  t.os_username,
  t.user_agent,
  t.status,
  t.scan_result,
  NOW() - INTERVAL t.minutes_ago MINUTE,
  NOW() - INTERVAL t.minutes_ago MINUTE,
  NOW() - INTERVAL t.minutes_ago MINUTE
FROM (
  SELECT 'windows' AS platform, 'WS-SECOPS-01' AS hostname, 'secops' AS os_username,
         'Mozilla/5.0 Demo', 'done' AS status,
         '{"marker":"DEMO_20260331","shadowAiCount":2}' AS scan_result, 18 AS minutes_ago
  UNION ALL
  SELECT 'macos', 'NB-DATA-11', 'dataadmin',
         'Mozilla/5.0 Demo', 'queued',
         '{"marker":"DEMO_20260331","message":"waiting client scan"}', 7
) t
WHERE NOT EXISTS (
  SELECT 1 FROM client_scan_queue q
  WHERE q.company_id = @company_id
    AND q.hostname = t.hostname
    AND q.status = t.status
    AND q.scan_result LIKE '%DEMO_20260331%'
);

-- 4) AI 调用日志演示数据（用于 /api/ai/monitor/summary 与日志列表）
INSERT INTO ai_call_log (
  user_id, data_asset_id, model_id, model_code, provider, input_preview, output_preview,
  status, error_msg, duration_ms, token_usage, ip, create_time
)
SELECT
  COALESCE((SELECT id FROM sys_user WHERE company_id = @company_id AND username = t.username ORDER BY id ASC LIMIT 1),
           (SELECT id FROM sys_user WHERE company_id = @company_id ORDER BY id ASC LIMIT 1)),
  (SELECT id FROM data_asset WHERE company_id = @company_id AND name = 'DEMO_20260331_客户画像主表' ORDER BY id ASC LIMIT 1),
  NULL,
  t.model_code,
  t.provider,
  t.input_preview,
  t.output_preview,
  t.status,
  t.error_msg,
  t.duration_ms,
  t.token_usage,
  t.ip,
  NOW() - INTERVAL t.minutes_ago MINUTE
FROM (
  SELECT 'secops' AS username, 'gpt-4o-mini' AS model_code, 'openai' AS provider,
         '[DEMO_20260331] 合规摘要生成请求' AS input_preview,
         '已生成风险摘要与建议。' AS output_preview,
         'success' AS status, NULL AS error_msg,
         1240 AS duration_ms, 890 AS token_usage, '10.10.1.21' AS ip, 60 AS minutes_ago
  UNION ALL
  SELECT 'employee', 'doubao-pro', 'bytedance',
         '[DEMO_20260331] 营销文案改写',
         '输出已脱敏。',
         'success', NULL,
         980, 540, '10.10.1.47', 48
  UNION ALL
  SELECT 'dataadmin', 'gpt-4o-mini', 'openai',
         '[DEMO_20260331] 数据字典解释',
         '请求被策略拒绝。',
         'fail', 'policy_blocked',
         430, 0, '10.10.2.11', 44
  UNION ALL
  SELECT 'secops', 'claude-3-haiku', 'anthropic',
         '[DEMO_20260331] 告警研判辅助',
         '风险等级建议为 medium。',
         'success', NULL,
         1110, 610, '10.10.1.21', 39
) t
WHERE NOT EXISTS (
  SELECT 1 FROM ai_call_log l
  WHERE l.input_preview = t.input_preview
);

-- 5) 模型调用日统计（用于首页趋势 aiCallSeries）
INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT
  NULL,
  COALESCE((SELECT id FROM sys_user WHERE company_id = @company_id AND username = t.username ORDER BY id ASC LIMIT 1),
           (SELECT id FROM sys_user WHERE company_id = @company_id ORDER BY id ASC LIMIT 1)),
  CURDATE() - INTERVAL t.days_ago DAY,
  t.call_count,
  t.total_latency_ms,
  t.cost_cents
FROM (
  SELECT 'secops' AS username, 6 AS days_ago, 82 AS call_count, 103000 AS total_latency_ms, 1280 AS cost_cents
  UNION ALL
  SELECT 'secops', 5, 74, 96000, 1190
  UNION ALL
  SELECT 'dataadmin', 4, 91, 121000, 1420
  UNION ALL
  SELECT 'employee', 3, 65, 74000, 960
  UNION ALL
  SELECT 'secops', 2, 88, 117000, 1360
  UNION ALL
  SELECT 'dataadmin', 1, 96, 133000, 1510
  UNION ALL
  SELECT 'secops', 0, 103, 145000, 1690
) t
WHERE NOT EXISTS (
  SELECT 1 FROM model_call_stat s
  WHERE s.user_id = COALESCE((SELECT id FROM sys_user WHERE company_id = @company_id AND username = t.username ORDER BY id ASC LIMIT 1),
                              (SELECT id FROM sys_user WHERE company_id = @company_id ORDER BY id ASC LIMIT 1))
    AND s.date = CURDATE() - INTERVAL t.days_ago DAY
);

-- 6) 风险事件演示数据（用于风险趋势与风险台账）
INSERT INTO risk_event (
  company_id, type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time
)
SELECT
  @company_id,
  t.type,
  t.level,
  NULL,
  '',
  t.status,
  NULL,
  t.process_log,
  NOW() - INTERVAL t.hours_ago HOUR,
  NOW() - INTERVAL t.hours_ago HOUR
FROM (
  SELECT 'PRIVACY_VIOLATION' AS type, 'HIGH' AS level, '待处理' AS status,
         'DEMO_20260331: 发现未脱敏数据外发尝试' AS process_log, 36 AS hours_ago
  UNION ALL
  SELECT 'ABNORMAL_ACCESS', 'MEDIUM', '待处理', 'DEMO_20260331: 员工在非常用时段批量访问数据', 30
  UNION ALL
  SELECT 'DATA_LEAKAGE', 'HIGH', '已处理', 'DEMO_20260331: 已阻断高危文件传输', 18
  UNION ALL
  SELECT 'MODEL_MISUSE', 'LOW', '待处理', 'DEMO_20260331: 模型调用频次异常抖动', 9
) t
WHERE NOT EXISTS (
  SELECT 1 FROM risk_event r
  WHERE r.company_id = @company_id AND r.process_log = t.process_log
);

-- 7) 告警中心演示数据（用于 /api/alert-center/stats 与告警列表）
INSERT INTO governance_event (
  company_id, user_id, username, event_type, source_module, severity, status,
  title, description, source_event_id, attack_type, policy_version, payload_json,
  handler_id, dispose_note, event_time, disposed_at, create_time, update_time
)
SELECT
  @company_id,
  COALESCE((SELECT id FROM sys_user WHERE company_id = @company_id AND username = t.username ORDER BY id ASC LIMIT 1),
           (SELECT id FROM sys_user WHERE company_id = @company_id ORDER BY id ASC LIMIT 1)),
  t.username,
  t.event_type,
  t.source_module,
  t.severity,
  t.status,
  t.title,
  t.description,
  t.source_event_id,
  t.attack_type,
  1,
  t.payload_json,
  NULL,
  '',
  NOW() - INTERVAL t.minutes_ago MINUTE,
  CASE WHEN t.status = 'pending' THEN NULL ELSE NOW() - INTERVAL (t.minutes_ago - 5) MINUTE END,
  NOW() - INTERVAL t.minutes_ago MINUTE,
  NOW() - INTERVAL t.minutes_ago MINUTE
FROM (
  SELECT 'secops' AS username, 'PRIVACY_ALERT' AS event_type, 'privacy-shield' AS source_module,
         'high' AS severity, 'pending' AS status,
         'DEMO_20260331 隐私文本命中' AS title,
         '检测到身份证与手机号组合外发请求。' AS description,
         'DEMO_20260331-GOV-001' AS source_event_id,
         'data_exfil_plain' AS attack_type,
         '{"marker":"DEMO_20260331","channel":"clipboard"}' AS payload_json,
         55 AS minutes_ago
  UNION ALL
  SELECT 'employee', 'ANOMALY_ALERT', 'behavior-monitor',
         'medium', 'pending',
         'DEMO_20260331 异常访问行为',
         '检测到短时高频敏感资产访问。',
         'DEMO_20260331-GOV-002',
         'abnormal_access',
         '{"marker":"DEMO_20260331","window":"night"}',
         43
  UNION ALL
  SELECT 'dataadmin', 'SHADOW_AI_ALERT', 'client-report',
         'high', 'blocked',
         'DEMO_20260331 影子AI高风险终端',
         '终端发现多款未纳管 AI 服务。',
         'DEMO_20260331-GOV-003',
         'shadow_ai_unmanaged',
         '{"marker":"DEMO_20260331","clientId":"demo-client-20260331-01"}',
         37
  UNION ALL
  SELECT 'secops', 'SECURITY_ALERT', 'threat-monitor',
         'critical', 'pending',
         'DEMO_20260331 可疑外联阻断',
         '检测到疑似数据渗漏通道并自动阻断。',
         'DEMO_20260331-GOV-004',
         'dns_tunnel',
         '{"marker":"DEMO_20260331","action":"blocked"}',
         29
) t
WHERE NOT EXISTS (
  SELECT 1 FROM governance_event g
  WHERE g.company_id = @company_id AND g.source_event_id = t.source_event_id
);

-- 8) 安全事件演示数据（供威胁监控与终端态势查看）
INSERT INTO security_event (
  company_id, event_type, file_path, target_addr, employee_id, hostname, file_size,
  severity, status, source, policy_version, operator_id, event_time, create_time, update_time
)
SELECT
  @company_id,
  t.event_type,
  t.file_path,
  t.target_addr,
  t.employee_id,
  t.hostname,
  t.file_size,
  t.severity,
  t.status,
  'agent',
  1,
  NULL,
  NOW() - INTERVAL t.minutes_ago MINUTE,
  NOW() - INTERVAL t.minutes_ago MINUTE,
  NOW() - INTERVAL t.minutes_ago MINUTE
FROM (
  SELECT 'LARGE_FILE_TRANSFER' AS event_type, 'C:/Users/employee/Documents/customer_list.xlsx' AS file_path,
         '198.51.100.17:443' AS target_addr, 'employee' AS employee_id,
         'WS-MKT-07' AS hostname, 6815744 AS file_size,
         'high' AS severity, 'blocked' AS status, 52 AS minutes_ago
  UNION ALL
  SELECT 'SENSITIVE_FILE_OPEN', 'C:/Users/employee/Desktop/id_card_samples.csv',
         'N/A', 'employee', 'WS-MKT-07', 932104,
         'medium', 'pending', 41
  UNION ALL
  SELECT 'ABNORMAL_PROCESS', '/Users/dataadmin/Downloads/export.sql',
         '203.0.113.22:8443', 'dataadmin', 'NB-DATA-11', 2482176,
         'critical', 'pending', 26
) t
WHERE NOT EXISTS (
  SELECT 1 FROM security_event s
  WHERE s.company_id = @company_id
    AND s.file_path = t.file_path
    AND s.event_type = t.event_type
    AND s.event_time > NOW() - INTERVAL 7 DAY
);
