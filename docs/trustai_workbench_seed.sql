-- Aegis Workbench 主题化演示数据
-- 说明：默认管理员账号由后端 DataInitializer 自动创建（admin / admin）。

INSERT INTO role (id, name, code, description) VALUES
(1, '平台管理员', 'ADMIN', '负责可信AI数据治理与隐私合规平台全局运营');

INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, model_type, risk_level, status, call_limit, current_calls, description) VALUES
('GPT-4', 'gpt-4', 'OpenAI', 'https://api.openai.com/v1/chat/completions', 'encrypted_key_1', 'chat', 'medium', 'enabled', 1000, 0, '高复杂度分析与研判模型'),
('GPT-3.5', 'gpt-3.5-turbo', 'OpenAI', 'https://api.openai.com/v1/chat/completions', 'encrypted_key_2', 'chat', 'low', 'enabled', 5000, 0, '常规合规问答模型'),
('Claude 3', 'claude-3-opus', 'Anthropic', 'https://api.anthropic.com/v1/messages', 'encrypted_key_3', 'chat', 'high', 'enabled', 800, 0, '高风险语义研判模型'),
('文心一言', 'ernie-bot', '百度', 'https://aip.baidubce.com', 'encrypted_key_4', 'chat', 'low', 'enabled', 2000, 0, '国产通用大模型'),
('通义千问', 'qwen-turbo', '阿里云', 'https://dashscope.aliyuncs.com', 'encrypted_key_5', 'chat', 'medium', 'enabled', 3000, 0, '国产办公助手模型');

INSERT INTO data_asset (name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description) VALUES
('客户主数据', 'database', 'high', 'mysql://crm/customer_master', NOW(), 1, 'CRM -> 数据中台', '包含实名、手机号、身份证片段'),
('跨境订单明细', 'database', 'medium', 'mysql://trade/global_order', NOW(), 1, '交易系统 -> 风控集市', '含地区与支付记录'),
('模型训练语料', 'file', 'high', '/mnt/train/corpus_2026', NOW(), 1, '湖仓 -> 训练工作区', '需执行脱敏后方可训练'),
('员工组织架构', 'api', 'low', 'https://iam.example.com/org', NOW(), 1, 'IAM -> 权限引擎', '用于主体与权限映射');

INSERT INTO alert_record (title, level, status, assignee_id, resolution, create_time, update_time) VALUES
('检测到异常登录行为', 'high', 'open', 1, '等待二次校验结果', NOW(), NOW()),
('模型输出命中敏感词策略', 'high', 'claimed', 1, '已转人工复核', NOW() - INTERVAL 1 HOUR, NOW()),
('跨境传输审批超时', 'medium', 'open', 1, '待业务责任人补件', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 12 HOUR),
('主体权利请求接近SLA', 'low', 'open', 1, '需在24小时内完成导出', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY);

INSERT INTO subject_request (user_id, type, comment, status, handler_id, result, create_time, update_time) VALUES
(1, 'access', '申请查看个人数据副本', 'done', 1, '已生成脱敏副本', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY),
(1, 'delete', '请求删除历史训练痕迹', 'processing', 1, '正在联动业务系统删除', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY),
(1, 'export', '申请导出近三个月模型调用记录', 'pending', 1, NULL, NOW() - INTERVAL 5 HOUR, NOW());

INSERT INTO audit_log (user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time) VALUES
(1, 1, 'VIEW', NOW() - INTERVAL 1 HOUR, '192.168.10.21', 'Chrome/Windows', '查看客户主数据', '返回10条记录', 'success', 'low', 'hash_001', NOW() - INTERVAL 1 HOUR),
(1, 1, 'QUERY', NOW() - INTERVAL 6 HOUR, '192.168.10.21', 'Edge/Windows', '按手机号检索客户', '命中42条记录', 'success', 'high', 'hash_002', NOW() - INTERVAL 6 HOUR),
(1, 2, 'EXPORT', NOW() - INTERVAL 1 DAY, '192.168.10.45', 'Chrome/macOS', '导出跨境订单明细', '进入审批流', 'success', 'medium', 'hash_003', NOW() - INTERVAL 1 DAY),
(1, 3, 'BLOCK', NOW() - INTERVAL 3 DAY, '192.168.10.67', 'API/TaskRunner', '阻断未脱敏训练语料访问', '访问令牌已吊销', 'blocked', 'high', 'hash_004', NOW() - INTERVAL 3 DAY),
(1, 4, 'DESENSE', NOW() - INTERVAL 5 DAY, '192.168.10.88', 'Chrome/Linux', '预览手机号脱敏策略', '生成遮罩样例', 'success', 'low', 'hash_005', NOW() - INTERVAL 5 DAY);

INSERT INTO risk_event (type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time) VALUES
('数据泄露', 'high', 2, '2', 'open', 1, '等待法务与安全联合复核', NOW(), NOW()),
('跨境传输未走审批', 'high', 3, '3', 'open', 1, '需补录出境安全评估材料', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 12 HOUR),
('模型输出含个人信息', 'medium', 4, '4', 'processing', 1, '已转模型治理专员复查', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY),
('主体删除请求超时', 'medium', 5, '5', 'processing', 1, '正在协调下游系统清理', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY),
('高敏资产权限漂移', 'low', 1, '1', 'resolved', 1, '已回收冗余角色与令牌', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY);

INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents) VALUES
(1, 1, CURRENT_DATE - INTERVAL 6 DAY, 32, 18200, 1280),
(2, 1, CURRENT_DATE - INTERVAL 6 DAY, 14, 7600, 420),
(3, 1, CURRENT_DATE - INTERVAL 5 DAY, 28, 19600, 1320),
(4, 1, CURRENT_DATE - INTERVAL 5 DAY, 11, 5900, 260),
(1, 1, CURRENT_DATE - INTERVAL 4 DAY, 36, 20500, 1480),
(5, 1, CURRENT_DATE - INTERVAL 4 DAY, 16, 8400, 360),
(1, 1, CURRENT_DATE - INTERVAL 3 DAY, 41, 21900, 1620),
(3, 1, CURRENT_DATE - INTERVAL 3 DAY, 13, 9200, 510),
(2, 1, CURRENT_DATE - INTERVAL 2 DAY, 34, 17100, 980),
(4, 1, CURRENT_DATE - INTERVAL 2 DAY, 18, 8600, 300),
(1, 1, CURRENT_DATE - INTERVAL 1 DAY, 44, 24100, 1760),
(5, 1, CURRENT_DATE - INTERVAL 1 DAY, 19, 9900, 390),
(1, 1, CURRENT_DATE, 39, 21400, 1710),
(3, 1, CURRENT_DATE, 17, 10200, 540);

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at) VALUES
('basic.system.name', 'Aegis Workbench', '系统名称', NOW(), NOW()),
('security.session.timeout', '30', '会话超时时间（分钟）', NOW(), NOW()),
('notification.system.enabled', 'true', '是否启用系统通知', NOW(), NOW());