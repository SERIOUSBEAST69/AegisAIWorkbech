INSERT INTO role (id, name, code, description, create_time, update_time)
SELECT 1, '平台管理员', 'ADMIN', '负责可信AI数据治理与隐私合规平台全局运营', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM role WHERE code = 'ADMIN'
);

INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, model_type, risk_level, status, call_limit, current_calls, description, create_time, update_time)
SELECT 'GPT-4', 'gpt-4', 'OpenAI', 'https://api.openai.com/v1/chat/completions', 'encrypted_key_1', 'chat', 'medium', 'enabled', 1000, 0, '高复杂度分析与研判模型', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE model_code = 'gpt-4');

INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, model_type, risk_level, status, call_limit, current_calls, description, create_time, update_time)
SELECT 'GPT-3.5', 'gpt-3.5-turbo', 'OpenAI', 'https://api.openai.com/v1/chat/completions', 'encrypted_key_2', 'chat', 'low', 'enabled', 5000, 0, '常规合规问答模型', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE model_code = 'gpt-3.5-turbo');

INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, model_type, risk_level, status, call_limit, current_calls, description, create_time, update_time)
SELECT 'Claude 3', 'claude-3-opus', 'Anthropic', 'https://api.anthropic.com/v1/messages', 'encrypted_key_3', 'chat', 'high', 'enabled', 800, 0, '高风险语义研判模型', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE model_code = 'claude-3-opus');

INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, model_type, risk_level, status, call_limit, current_calls, description, create_time, update_time)
SELECT '文心一言', 'ernie-bot', '百度', 'https://aip.baidubce.com', 'encrypted_key_4', 'chat', 'low', 'enabled', 2000, 0, '国产通用大模型', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE model_code = 'ernie-bot');

INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, model_type, risk_level, status, call_limit, current_calls, description, create_time, update_time)
SELECT '通义千问', 'qwen-turbo', '阿里云', 'https://dashscope.aliyuncs.com', 'encrypted_key_5', 'chat', 'medium', 'enabled', 3000, 0, '国产办公助手模型', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE model_code = 'qwen-turbo');

INSERT INTO data_asset (name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description, create_time, update_time)
SELECT '客户主数据', 'database', 'high', 'mysql://crm/customer_master', NOW(), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), 'CRM -> 数据中台', '包含实名、手机号、身份证片段', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM data_asset WHERE name = '客户主数据');

INSERT INTO data_asset (name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description, create_time, update_time)
SELECT '跨境订单明细', 'database', 'medium', 'mysql://trade/global_order', NOW(), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '交易系统 -> 风控集市', '含地区与支付记录', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM data_asset WHERE name = '跨境订单明细');

INSERT INTO data_asset (name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description, create_time, update_time)
SELECT '模型训练语料', 'file', 'high', '/mnt/train/corpus_2026', NOW(), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '湖仓 -> 训练工作区', '需执行脱敏后方可训练', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM data_asset WHERE name = '模型训练语料');

INSERT INTO data_asset (name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description, create_time, update_time)
SELECT '员工组织架构', 'api', 'low', 'https://iam.example.com/org', NOW(), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), 'IAM -> 权限引擎', '用于主体与权限映射', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM data_asset WHERE name = '员工组织架构');

INSERT INTO alert_record (title, level, status, assignee_id, related_log_id, resolution, create_time, update_time)
SELECT '检测到异常登录行为', 'high', 'open', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), NULL, '等待二次校验结果', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM alert_record WHERE title = '检测到异常登录行为');

INSERT INTO alert_record (title, level, status, assignee_id, related_log_id, resolution, create_time, update_time)
SELECT '模型输出命中敏感词策略', 'high', 'claimed', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), NULL, '已转人工复核', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()
WHERE NOT EXISTS (SELECT 1 FROM alert_record WHERE title = '模型输出命中敏感词策略');

INSERT INTO alert_record (title, level, status, assignee_id, related_log_id, resolution, create_time, update_time)
SELECT '跨境传输审批超时', 'medium', 'open', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), NULL, '待业务责任人补件', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 12 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM alert_record WHERE title = '跨境传输审批超时');

INSERT INTO alert_record (title, level, status, assignee_id, related_log_id, resolution, create_time, update_time)
SELECT '主体权利请求接近SLA', 'low', 'open', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), NULL, '需在24小时内完成导出', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)
WHERE NOT EXISTS (SELECT 1 FROM alert_record WHERE title = '主体权利请求接近SLA');

INSERT INTO subject_request (user_id, type, comment, status, handler_id, result, create_time, update_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), 'access', '申请查看个人数据副本', 'done', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '已生成脱敏副本', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)
WHERE NOT EXISTS (SELECT 1 FROM subject_request WHERE comment = '申请查看个人数据副本');

INSERT INTO subject_request (user_id, type, comment, status, handler_id, result, create_time, update_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), 'delete', '请求删除历史训练痕迹', 'processing', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '正在联动业务系统删除', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM subject_request WHERE comment = '请求删除历史训练痕迹');

INSERT INTO subject_request (user_id, type, comment, status, handler_id, result, create_time, update_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), 'export', '申请导出近三个月模型调用记录', 'pending', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), NOW()
WHERE NOT EXISTS (SELECT 1 FROM subject_request WHERE comment = '申请导出近三个月模型调用记录');

INSERT INTO audit_log (user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), COALESCE((SELECT id FROM data_asset WHERE name = '客户主数据' LIMIT 1), 1), 'VIEW', DATE_SUB(NOW(), INTERVAL 1 HOUR), '192.168.10.21', 'Chrome/Windows', '查看客户主数据', '返回10条记录', 'success', 'low', 'hash_001', DATE_SUB(NOW(), INTERVAL 1 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE hash = 'hash_001');

INSERT INTO audit_log (user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), COALESCE((SELECT id FROM data_asset WHERE name = '客户主数据' LIMIT 1), 1), 'QUERY', DATE_SUB(NOW(), INTERVAL 6 HOUR), '192.168.10.21', 'Edge/Windows', '按手机号检索客户', '命中42条记录', 'success', 'high', 'hash_002', DATE_SUB(NOW(), INTERVAL 6 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE hash = 'hash_002');

INSERT INTO audit_log (user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), COALESCE((SELECT id FROM data_asset WHERE name = '跨境订单明细' LIMIT 1), 1), 'EXPORT', DATE_SUB(NOW(), INTERVAL 1 DAY), '192.168.10.45', 'Chrome/macOS', '导出跨境订单明细', '进入审批流', 'success', 'medium', 'hash_003', DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE hash = 'hash_003');

INSERT INTO audit_log (user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), COALESCE((SELECT id FROM data_asset WHERE name = '模型训练语料' LIMIT 1), 1), 'BLOCK', DATE_SUB(NOW(), INTERVAL 3 DAY), '192.168.10.67', 'API/TaskRunner', '阻断未脱敏训练语料访问', '访问令牌已吊销', 'blocked', 'high', 'hash_004', DATE_SUB(NOW(), INTERVAL 3 DAY)
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE hash = 'hash_004');

INSERT INTO audit_log (user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time)
SELECT COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), COALESCE((SELECT id FROM data_asset WHERE name = '员工组织架构' LIMIT 1), 1), 'DESENSE', DATE_SUB(NOW(), INTERVAL 5 DAY), '192.168.10.88', 'Chrome/Linux', '预览手机号脱敏策略', '生成遮罩样例', 'success', 'low', 'hash_005', DATE_SUB(NOW(), INTERVAL 5 DAY)
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE hash = 'hash_005');

INSERT INTO risk_event (type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time)
SELECT '数据泄露', 'high', (SELECT id FROM audit_log WHERE hash = 'hash_002' LIMIT 1), CAST((SELECT id FROM audit_log WHERE hash = 'hash_002' LIMIT 1) AS CHAR), 'open', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '等待法务与安全联合复核', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM risk_event WHERE process_log = '等待法务与安全联合复核');

INSERT INTO risk_event (type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time)
SELECT '跨境传输未走审批', 'high', (SELECT id FROM audit_log WHERE hash = 'hash_003' LIMIT 1), CAST((SELECT id FROM audit_log WHERE hash = 'hash_003' LIMIT 1) AS CHAR), 'open', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '需补录出境安全评估材料', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 12 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM risk_event WHERE process_log = '需补录出境安全评估材料');

INSERT INTO risk_event (type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time)
SELECT '模型输出含个人信息', 'medium', (SELECT id FROM audit_log WHERE hash = 'hash_004' LIMIT 1), CAST((SELECT id FROM audit_log WHERE hash = 'hash_004' LIMIT 1) AS CHAR), 'processing', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '已转模型治理专员复查', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM risk_event WHERE process_log = '已转模型治理专员复查');

INSERT INTO risk_event (type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time)
SELECT '主体删除请求超时', 'medium', (SELECT id FROM audit_log WHERE hash = 'hash_005' LIMIT 1), CAST((SELECT id FROM audit_log WHERE hash = 'hash_005' LIMIT 1) AS CHAR), 'processing', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '正在协调下游系统清理', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)
WHERE NOT EXISTS (SELECT 1 FROM risk_event WHERE process_log = '正在协调下游系统清理');

INSERT INTO risk_event (type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time)
SELECT '高敏资产权限漂移', 'low', (SELECT id FROM audit_log WHERE hash = 'hash_001' LIMIT 1), CAST((SELECT id FROM audit_log WHERE hash = 'hash_001' LIMIT 1) AS CHAR), 'resolved', COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), '已回收冗余角色与令牌', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)
WHERE NOT EXISTS (SELECT 1 FROM risk_event WHERE process_log = '已回收冗余角色与令牌');

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 6 DAY), 32, 18200, 1280
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 6 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-3.5-turbo' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 6 DAY), 14, 7600, 420
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-3.5-turbo' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 6 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'claude-3-opus' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 5 DAY), 28, 19600, 1320
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'claude-3-opus' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'ernie-bot' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 5 DAY), 11, 5900, 260
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'ernie-bot' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 4 DAY), 36, 20500, 1480
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'qwen-turbo' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 4 DAY), 16, 8400, 360
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'qwen-turbo' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 3 DAY), 41, 21900, 1620
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'claude-3-opus' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 3 DAY), 13, 9200, 510
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'claude-3-opus' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-3.5-turbo' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 2 DAY), 34, 17100, 980
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-3.5-turbo' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'ernie-bot' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 2 DAY), 18, 8600, 300
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'ernie-bot' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 44, 24100, 1760
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'qwen-turbo' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 19, 9900, 390
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'qwen-turbo' LIMIT 1), 1) AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), CURDATE(), 39, 21400, 1710
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'gpt-4' LIMIT 1), 1) AND date = CURDATE());

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents)
SELECT COALESCE((SELECT id FROM ai_model WHERE model_code = 'claude-3-opus' LIMIT 1), 1), COALESCE((SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1), 1), CURDATE(), 17, 10200, 540
WHERE NOT EXISTS (SELECT 1 FROM model_call_stat WHERE model_id = COALESCE((SELECT id FROM ai_model WHERE model_code = 'claude-3-opus' LIMIT 1), 1) AND date = CURDATE());

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
SELECT 'basic.system.name', 'Aegis Workbench', '系统名称', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'basic.system.name');

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
SELECT 'security.session.timeout', '30', '会话超时时间（分钟）', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'security.session.timeout');

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
SELECT 'notification.system.enabled', 'true', '是否启用系统通知', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'notification.system.enabled');

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
SELECT 'privacy.shield.config',
       '{"monitorEnabled":true,"predictEnabled":true,"dedupeSeconds":60,"sensitiveKeywords":["身份证","银行卡","手机号","公司代码"],"siteSelectors":[{"siteId":"chatgpt","hosts":["chat.openai.com","chatgpt.com"],"inputSelectors":["#prompt-textarea","textarea[data-testid=prompt-textarea]","textarea"]},{"siteId":"doubao","hosts":["doubao.com","www.doubao.com"],"inputSelectors":["textarea","div[contenteditable=true]","[data-testid=chat-input]"]},{"siteId":"yiyan","hosts":["yiyan.baidu.com"],"inputSelectors":["textarea","div[contenteditable=true]","#chat-input"]}],"aiWindowRules":{"titleKeywords":["ChatGPT","豆包","文心一言","Kimi","通义千问"],"processNames":["chrome","msedge","firefox","doubao","qqbrowser"]}}',
       '隐私盾配置', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'privacy.shield.config');

INSERT INTO privacy_event (user_id, event_type, content_masked, source, action, device_id, hostname, window_title, matched_types, event_time, create_time, update_time)
SELECT 'employee.demo', 'SENSITIVE_TEXT', '客户身份证 410101******1234 与手机号 138****8000', 'extension', 'desensitize', 'employee.demo-device', 'demo-host', 'ChatGPT', 'id_card,phone', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM privacy_event WHERE user_id = 'employee.demo' AND source = 'extension');

INSERT INTO privacy_event (user_id, event_type, content_masked, source, action, device_id, hostname, window_title, matched_types, event_time, create_time, update_time)
SELECT 'employee.demo', 'SENSITIVE_TEXT', '银行卡 6222****2021 准备粘贴到外部AI', 'clipboard', 'ignore', 'employee.demo-device', 'demo-host', '豆包', 'bank_card', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 1 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM privacy_event WHERE user_id = 'employee.demo' AND source = 'clipboard');