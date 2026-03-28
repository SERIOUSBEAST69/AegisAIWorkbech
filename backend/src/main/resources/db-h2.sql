-- H2 compatible DDL for TrustAI

CREATE TABLE IF NOT EXISTS client_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  client_id VARCHAR(64) NOT NULL,
  hostname VARCHAR(255),
  os_username VARCHAR(255),
  os_type VARCHAR(32),
  client_version VARCHAR(32),
  discovered_services CLOB,
  shadow_ai_count INT DEFAULT 0,
  risk_level VARCHAR(20) DEFAULT 'none',
  scan_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS client_scan_queue (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  platform VARCHAR(32) NOT NULL,
  hostname VARCHAR(255),
  os_username VARCHAR(255),
  user_agent VARCHAR(512),
  status VARCHAR(32) DEFAULT 'queued',
  scan_result CLOB,
  download_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  account_type VARCHAR(20) DEFAULT 'demo',
  account_status VARCHAR(20) DEFAULT 'active',
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50),
  nickname VARCHAR(50),
  avatar VARCHAR(255),
  role_id BIGINT,
  device_id VARCHAR(128),
  department VARCHAR(50),
  organization_type VARCHAR(50),
  login_type VARCHAR(20) DEFAULT 'password',
  wechat_open_id VARCHAR(120),
  phone VARCHAR(20),
  email VARCHAR(100),
  status INT DEFAULT 1,
  approved_by BIGINT,
  reject_reason VARCHAR(255),
  approved_at TIMESTAMP,
  last_policy_pull_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS company (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_code VARCHAR(64) NOT NULL,
  company_name VARCHAR(128) NOT NULL,
  status INT DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  name VARCHAR(50) NOT NULL,
  code VARCHAR(50) NOT NULL,
  description VARCHAR(200),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  code VARCHAR(50) NOT NULL,
  type VARCHAR(20),
  parent_id BIGINT DEFAULT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS data_asset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(50),
  sensitivity_level VARCHAR(20),
  location VARCHAR(200),
  discovery_time TIMESTAMP,
  owner_id BIGINT,
  lineage CLOB,
  description VARCHAR(200),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_model (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_name VARCHAR(100) NOT NULL,
  model_code VARCHAR(50) NOT NULL,
  provider VARCHAR(50),
  api_url VARCHAR(200),
  api_key VARCHAR(200),
  model_type VARCHAR(50),
  risk_level VARCHAR(20),
  status VARCHAR(20) DEFAULT 'enabled',
  call_limit INT DEFAULT 0,
  current_calls INT DEFAULT 0,
  description VARCHAR(200),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_call_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  data_asset_id BIGINT,
  model_id BIGINT,
  model_code VARCHAR(100),
  provider VARCHAR(50),
  input_preview VARCHAR(200),
  output_preview VARCHAR(200),
  status VARCHAR(20),
  error_msg VARCHAR(500),
  duration_ms BIGINT,
  token_usage INT,
  ip VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  asset_id BIGINT,
  operation VARCHAR(50),
  operation_time TIMESTAMP,
  ip VARCHAR(50),
  device VARCHAR(100),
  input_overview VARCHAR(200),
  output_overview VARCHAR(200),
  result VARCHAR(20),
  risk_level VARCHAR(20),
  hash VARCHAR(128),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS approval_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  applicant_id BIGINT,
  asset_id BIGINT,
  reason VARCHAR(200),
  status VARCHAR(20),
  approver_id BIGINT,
  process_instance_id VARCHAR(64),
  task_id VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS compliance_policy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  name VARCHAR(100) NOT NULL,
  rule_content CLOB,
  scope VARCHAR(200),
  status INT DEFAULT 1,
  version INT DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS desensitize_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100),
  pattern VARCHAR(200),
  mask VARCHAR(50),
  example VARCHAR(200),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS desense_recommend_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  data_category VARCHAR(50),
  user_role VARCHAR(50),
  strategy VARCHAR(50),
  rule_id BIGINT,
  priority INT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_call_stat (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_id BIGINT,
  user_id BIGINT,
  date DATE,
  call_count INT DEFAULT 0,
  total_latency_ms BIGINT DEFAULT 0,
  cost_cents INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS risk_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  type VARCHAR(50),
  level VARCHAR(20),
  related_log_id BIGINT,
  audit_log_ids VARCHAR(500),
  status VARCHAR(20),
  handler_id BIGINT,
  process_log CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sensitive_scan_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_type VARCHAR(20),
  source_path VARCHAR(200),
  status VARCHAR(20),
  sensitive_ratio DECIMAL(5,2),
  report_path VARCHAR(200),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS subject_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  user_id BIGINT,
  type VARCHAR(50),
  comment VARCHAR(500),
  status VARCHAR(20),
  handler_id BIGINT,
  result VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key VARCHAR(128) NOT NULL UNIQUE,
  config_value CLOB NOT NULL,
  description VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_user_op_time ON audit_log(user_id, operation_time);
CREATE INDEX IF NOT EXISTS idx_risk_company_status_time ON risk_event(company_id, status, create_time);
CREATE INDEX IF NOT EXISTS idx_model_stat_user_date ON model_call_stat(user_id, date);
CREATE INDEX IF NOT EXISTS idx_sec_company_status_time ON security_event(company_id, status, event_time);
CREATE INDEX IF NOT EXISTS idx_sec_company_severity_time ON security_event(company_id, severity, event_time);
CREATE INDEX IF NOT EXISTS idx_privacy_company_time ON privacy_event(company_id, event_time);
CREATE INDEX IF NOT EXISTS idx_privacy_company_user_time ON privacy_event(company_id, user_id, event_time);
CREATE INDEX IF NOT EXISTS idx_client_report_company_scan ON client_report(company_id, scan_time);
CREATE INDEX IF NOT EXISTS idx_client_report_company_client_scan ON client_report(company_id, client_id, scan_time);
CREATE INDEX IF NOT EXISTS idx_client_queue_company_download ON client_scan_queue(company_id, download_time);

-- Default admin user will be created by DataInitializer on startup

INSERT INTO company (company_code, company_name, status, create_time, update_time)
SELECT 'aegis-default', 'Aegis 默认公司', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_code = 'aegis-default');

-- Default AI models
INSERT INTO ai_model (model_name, model_code, provider, api_url, api_key, model_type, risk_level, status, call_limit, current_calls, description) VALUES
('GPT-4', 'gpt-4', 'OpenAI', 'https://api.openai.com/v1/chat/completions', 'encrypted_key_1', 'chat', 'medium', 'enabled', 1000, 0, 'OpenAI GPT-4 大语言模型'),
('GPT-3.5', 'gpt-3.5-turbo', 'OpenAI', 'https://api.openai.com/v1/chat/completions', 'encrypted_key_2', 'chat', 'low', 'enabled', 5000, 0, 'OpenAI GPT-3.5 Turbo 模型'),
('Claude 3', 'claude-3-opus', 'Anthropic', 'https://api.anthropic.com/v1/messages', 'encrypted_key_3', 'chat', 'medium', 'enabled', 800, 0, 'Anthropic Claude 3 Opus 模型'),
('文心一言', 'ernie-bot', '百度', 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions', 'encrypted_key_4', 'chat', 'low', 'enabled', 2000, 0, '百度文心一言大模型'),
('通义千问', 'qwen-turbo', '阿里云', 'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation', 'encrypted_key_5', 'chat', 'low', 'enabled', 3000, 0, '阿里云通义千问模型');

-- Default data assets
INSERT INTO data_asset (company_id, name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description, create_time, update_time) VALUES
(1, '客户信息表', 'database', 'high', 'mysql://localhost:3306/crm/customers', CURRENT_TIMESTAMP, 1, 'CRM系统 -> 数据仓库', '包含客户姓名、电话、地址等敏感信息', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, '订单数据', 'database', 'medium', 'mysql://localhost:3306/erp/orders', CURRENT_TIMESTAMP, 1, 'ERP系统 -> 数据仓库', '订单交易数据', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, '产品目录', 'file', 'low', '/data/products/catalog.xlsx', CURRENT_TIMESTAMP, 1, '产品管理系统', '产品基本信息', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Default compliance policies
INSERT INTO compliance_policy (company_id, name, rule_content, scope, status, version, create_time, update_time) VALUES
(1, '数据分类分级规范', '所有数据必须按照敏感程度分为高、中、低三级', '全公司', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, '个人信息保护政策', '收集个人信息需获得用户明确同意', '业务部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, '提示词敏感词拦截策略', '{"keywords":["身份证号","银行卡号","工资条","病历","家庭住址","private key"],"mode":"contains"}', 'ai_prompt', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, '数据出境安全评估', '涉及跨境数据传输需进行安全评估', '技术部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Default desensitize rules
INSERT INTO desensitize_rule (name, pattern, mask, example, create_time, update_time) VALUES
('手机号脱敏', '(\\d{3})\\d{4}(\\d{4})', '$1****$2', '13800138000 -> 138****8000', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('身份证号脱敏', '(\\d{6})\\d{8}(\\d{4})', '$1********$2', '110101199001011234 -> 110101********1234', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('邮箱脱敏', '(\\w{2})\\w+(@\\w+)', '$1***$2', 'zhangsan@example.com -> zh***@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Default risk events
INSERT INTO risk_event (company_id, type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time) VALUES
(1, '数据泄露', 'high', 1, '1,4', 'open', null, '等待法务与安全联合复核', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, '未授权访问', 'medium', 2, '2,5', 'processing', 1, '正在调查中', CURRENT_TIMESTAMP - INTERVAL '1' HOUR, CURRENT_TIMESTAMP),
(1, '异常导出', 'low', 3, '3', 'resolved', 1, '已确认是正常业务操作', CURRENT_TIMESTAMP - INTERVAL '2' HOUR, CURRENT_TIMESTAMP - INTERVAL '1' HOUR),
(1, '跨境传输未走审批', 'high', 6, '6', 'open', 1, '需补录数据出境评估材料', CURRENT_TIMESTAMP - INTERVAL '1' DAY, CURRENT_TIMESTAMP - INTERVAL '12' HOUR),
(1, '模型输出含个人信息', 'medium', 7, '7', 'processing', 1, '已转交模型治理专员复查', CURRENT_TIMESTAMP - INTERVAL '2' DAY, CURRENT_TIMESTAMP - INTERVAL '1' DAY),
(1, '夜间批量下载异常', 'high', 8, '8,9', 'open', null, '触发安全策略封禁下载令牌', CURRENT_TIMESTAMP - INTERVAL '3' DAY, CURRENT_TIMESTAMP - INTERVAL '2' DAY),
(1, '主体删除请求超时', 'medium', 10, '10', 'processing', 1, '正在协调业务系统同步删除', CURRENT_TIMESTAMP - INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '3' DAY),
(1, '高敏资产权限漂移', 'low', 11, '11', 'resolved', 1, '已回收历史冗余角色', CURRENT_TIMESTAMP - INTERVAL '5' DAY, CURRENT_TIMESTAMP - INTERVAL '4' DAY),
(1, '模型调用成本突增', 'medium', 12, '12', 'processing', 1, '已触发预算阈值治理', CURRENT_TIMESTAMP - INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '5' DAY);

-- Default sensitive scan tasks
INSERT INTO sensitive_scan_task (source_type, source_path, status, sensitive_ratio, report_path, create_time, update_time) VALUES
('database', 'mysql://localhost:3306/crm', 'done', 15.5, '/reports/crm_scan_20240308.html', CURRENT_TIMESTAMP - INTERVAL '1' DAY, CURRENT_TIMESTAMP),
('file', '/data/shared/documents', 'running', null, null, CURRENT_TIMESTAMP - INTERVAL '2' HOUR, CURRENT_TIMESTAMP),
('database', 'mysql://localhost:3306/erp', 'pending', null, null, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Default approval requests
INSERT INTO approval_request (company_id, applicant_id, asset_id, reason, status, approver_id, process_instance_id, task_id, create_time, update_time) VALUES
(1, 1, 1, '需要导出客户数据进行营销活动', 'pending', null, null, null, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 1, 2, '申请访问订单数据用于财务分析', 'approved', 1, 'PI_DEMO_001', null, CURRENT_TIMESTAMP - INTERVAL '1' DAY, CURRENT_TIMESTAMP - INTERVAL '12' HOUR);

-- Default subject requests
INSERT INTO subject_request (company_id, user_id, type, comment, status, handler_id, result, create_time, update_time) VALUES
(1, 1, 'access', '申请查看我的个人数据', 'done', 1, '已提供数据副本', CURRENT_TIMESTAMP - INTERVAL '2' DAY, CURRENT_TIMESTAMP - INTERVAL '1' DAY),
(1, 1, 'delete', '请求删除我的账户数据', 'processing', 1, null, CURRENT_TIMESTAMP - INTERVAL '1' DAY, CURRENT_TIMESTAMP),
(1, 1, 'export', '申请导出近三个月模型调用记录', 'pending', 1, null, CURRENT_TIMESTAMP - INTERVAL '5' HOUR, CURRENT_TIMESTAMP),
(1, 1, 'access', '需要查看跨境共享涉及的数据副本', 'processing', 1, '正在生成脱敏副本', CURRENT_TIMESTAMP - INTERVAL '3' DAY, CURRENT_TIMESTAMP - INTERVAL '2' DAY);

-- Default audit logs
INSERT INTO audit_log (user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time) VALUES
(1, 1, 'VIEW', CURRENT_TIMESTAMP - INTERVAL '1' HOUR, '192.168.1.100', 'Chrome/Windows', '查看客户信息', '返回10条记录', 'success', 'low', 'abc123hash', CURRENT_TIMESTAMP - INTERVAL '1' HOUR),
(1, 2, 'EXPORT', CURRENT_TIMESTAMP - INTERVAL '2' HOUR, '192.168.1.100', 'Chrome/Windows', '导出订单数据', '导出100条记录', 'success', 'medium', 'def456hash', CURRENT_TIMESTAMP - INTERVAL '2' HOUR),
(1, 1, 'QUERY', CURRENT_TIMESTAMP - INTERVAL '6' HOUR, '10.10.0.22', 'Edge/Windows', '检索高敏客户手机号', '命中42条记录', 'success', 'high', 'ghi789hash', CURRENT_TIMESTAMP - INTERVAL '6' HOUR),
(1, 1, 'SHARE', CURRENT_TIMESTAMP - INTERVAL '1' DAY, '10.10.0.22', 'Edge/Windows', '发起跨部门数据共享', '进入审批流', 'success', 'medium', 'jkl012hash', CURRENT_TIMESTAMP - INTERVAL '1' DAY),
(1, 2, 'APPROVE', CURRENT_TIMESTAMP - INTERVAL '2' DAY, '10.10.0.38', 'Chrome/macOS', '审批订单数据分析申请', '审批通过', 'success', 'low', 'mno345hash', CURRENT_TIMESTAMP - INTERVAL '2' DAY),
(1, 1, 'BLOCK', CURRENT_TIMESTAMP - INTERVAL '3' DAY, '10.10.0.56', 'Chrome/Windows', '阻断未授权导出', '下载令牌已吊销', 'success', 'high', 'pqr678hash', CURRENT_TIMESTAMP - INTERVAL '3' DAY),
(1, 3, 'SCAN', CURRENT_TIMESTAMP - INTERVAL '4' DAY, '10.10.0.19', 'API/TaskRunner', '执行共享目录敏感扫描', '输出扫描报告', 'success', 'medium', 'stu901hash', CURRENT_TIMESTAMP - INTERVAL '4' DAY),
(1, 1, 'DESENSE', CURRENT_TIMESTAMP - INTERVAL '5' DAY, '10.10.0.88', 'Chrome/Windows', '预览手机号脱敏策略', '生成示例遮罩结果', 'success', 'low', 'vwx234hash', CURRENT_TIMESTAMP - INTERVAL '5' DAY),
(1, 2, 'QUERY', CURRENT_TIMESTAMP - INTERVAL '6' DAY, '10.10.0.41', 'Chrome/Linux', '查询模型调用成本波动', '返回7日趋势', 'success', 'medium', 'yz5678hash', CURRENT_TIMESTAMP - INTERVAL '6' DAY),
(1, 1, 'EXPORT', CURRENT_TIMESTAMP - INTERVAL '6' DAY - INTERVAL '5' HOUR, '10.10.0.41', 'Chrome/Linux', '尝试夜间批量导出客户数据', '触发风控阻断', 'blocked', 'high', 'za1357hash', CURRENT_TIMESTAMP - INTERVAL '6' DAY - INTERVAL '5' HOUR);

-- 近7日模型调用统计，用于首页工作台趋势图
INSERT INTO model_call_stat (model_id, user_id, date, call_count, total_latency_ms, cost_cents) VALUES
(1, 1, CURRENT_DATE - 6, 32, 18200, 1280),
(2, 1, CURRENT_DATE - 6, 14, 7600, 420),
(3, 1, CURRENT_DATE - 5, 28, 19600, 1320),
(4, 1, CURRENT_DATE - 5, 11, 5900, 260),
(1, 1, CURRENT_DATE - 4, 36, 20500, 1480),
(5, 1, CURRENT_DATE - 4, 16, 8400, 360),
(1, 1, CURRENT_DATE - 3, 41, 21900, 1620),
(3, 1, CURRENT_DATE - 3, 13, 9200, 510),
(2, 1, CURRENT_DATE - 2, 34, 17100, 980),
(4, 1, CURRENT_DATE - 2, 18, 8600, 300),
(1, 1, CURRENT_DATE - 1, 44, 24100, 1760),
(5, 1, CURRENT_DATE - 1, 19, 9900, 390),
(1, 1, CURRENT_DATE, 39, 21400, 1710),
(3, 1, CURRENT_DATE, 17, 10200, 540);

-- ── 安全事件表（OpenClaw 模拟程序窃取事件） ──────────────────────────────────
CREATE TABLE IF NOT EXISTS security_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  file_path VARCHAR(512) COMMENT '涉及文件路径',
  target_addr VARCHAR(256) COMMENT '目标地址（模拟远端）',
  employee_id VARCHAR(128) COMMENT '员工标识',
  hostname VARCHAR(128) COMMENT '主机名',
  file_size BIGINT COMMENT '文件大小（字节）',
  severity VARCHAR(20) DEFAULT 'medium' COMMENT 'critical/high/medium/low',
  status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/blocked/ignored/reviewing',
  source VARCHAR(64) DEFAULT 'agent' COMMENT '上报来源',
  policy_version BIGINT,
  operator_id BIGINT COMMENT '操作者ID',
  event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ── 安全检测规则表 ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS security_detection_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  sensitive_extensions VARCHAR(500) DEFAULT '.docx,.pdf,.xlsx,.pptx,.key,.csv,.sql,.env,.pem,.pfx',
  sensitive_paths VARCHAR(1000) DEFAULT 'C:/Users,/home,/Documents,/Desktop',
  alert_threshold_bytes BIGINT DEFAULT 1048576,
  enabled BOOLEAN DEFAULT TRUE,
  description VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS privacy_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  user_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(64) DEFAULT 'SENSITIVE_TEXT',
  content_masked CLOB,
  source VARCHAR(32) DEFAULT 'extension',
  action VARCHAR(32) DEFAULT 'detect',
  device_id VARCHAR(128),
  hostname VARCHAR(128),
  window_title VARCHAR(255),
  matched_types VARCHAR(255),
  policy_version BIGINT,
  event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS governance_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  user_id BIGINT,
  username VARCHAR(128),
  event_type VARCHAR(64) NOT NULL,
  source_module VARCHAR(64) NOT NULL,
  severity VARCHAR(20) DEFAULT 'medium',
  status VARCHAR(20) DEFAULT 'pending',
  title VARCHAR(255),
  description CLOB,
  source_event_id VARCHAR(64),
  attack_type VARCHAR(64),
  policy_version BIGINT,
  payload_json CLOB,
  handler_id BIGINT,
  dispose_note VARCHAR(500),
  event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  disposed_at TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS adversarial_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  user_id BIGINT,
  username VARCHAR(128),
  governance_event_id BIGINT,
  scenario VARCHAR(64),
  policy_version BIGINT,
  result_json CLOB,
  effectiveness_analysis CLOB,
  suggestions_json CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 默认检测规则
INSERT INTO security_detection_rule (name, sensitive_extensions, sensitive_paths, alert_threshold_bytes, enabled, description) VALUES
('默认文件窃取检测规则', '.docx,.pdf,.xlsx,.pptx,.key,.csv,.sql,.env,.pem,.pfx,.db,.bak', 'C:/Users,/home,/Documents,/Desktop,/confidential', 102400, TRUE, '检测常见敏感文档类型的非授权外传行为'),
('源代码泄露规则', '.java,.py,.go,.ts,.js,.env,.yml,.yaml,.json,.xml,.properties', '/src,/source,/code,/project', 51200, TRUE, '检测源码目录下文件的批量外传'),
('高价值数据规则', '.sql,.bak,.dump,.tar,.zip', '/backup,/export,/archive', 524288, TRUE, '检测数据库备份、归档文件的异常传输');
