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

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  account_type VARCHAR(20) DEFAULT 'real',
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

CREATE TABLE IF NOT EXISTS user_recycle_bin (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  user_id BIGINT,
  username VARCHAR(50),
  snapshot_json CLOB,
  deleted_by BIGINT,
  delete_reason VARCHAR(200),
  deleted_at TIMESTAMP,
  restore_status VARCHAR(20) DEFAULT 'deleted',
  restored_by BIGINT,
  restored_at TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS governance_change_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  module VARCHAR(32) NOT NULL,
  action VARCHAR(20) NOT NULL,
  target_id BIGINT,
  payload_json CLOB,
  status VARCHAR(20) DEFAULT 'pending',
  risk_level VARCHAR(20) DEFAULT 'HIGH',
  requester_id BIGINT,
  requester_role_code VARCHAR(50),
  approver_id BIGINT,
  approver_role_code VARCHAR(50),
  approve_note VARCHAR(500),
  approved_at TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sod_conflict_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  scenario VARCHAR(64) NOT NULL,
  role_code_a VARCHAR(50) NOT NULL,
  role_code_b VARCHAR(50) NOT NULL,
  enabled INT DEFAULT 1,
  description VARCHAR(255),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key VARCHAR(128) NOT NULL UNIQUE,
  config_value CLOB NOT NULL,
  description VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS data_asset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  name VARCHAR(100),
  type VARCHAR(50),
  sensitivity_level VARCHAR(50),
  location VARCHAR(255),
  discovery_time TIMESTAMP,
  owner_id BIGINT,
  lineage VARCHAR(500),
  description VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  type VARCHAR(100),
  level VARCHAR(20),
  related_log_id BIGINT,
  audit_log_ids VARCHAR(500),
  status VARCHAR(20),
  handler_id BIGINT,
  process_log VARCHAR(1000),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS approval_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  applicant_id BIGINT,
  asset_id BIGINT,
  reason VARCHAR(500),
  status VARCHAR(20),
  approver_id BIGINT,
  process_instance_id VARCHAR(64),
  task_id VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS subject_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  user_id BIGINT,
  type VARCHAR(20),
  status VARCHAR(20),
  comment VARCHAR(500),
  handler_id BIGINT,
  result VARCHAR(1000),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_call_stat (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_id BIGINT,
  user_id BIGINT,
  date TIMESTAMP,
  call_count INT DEFAULT 0,
  total_latency_ms BIGINT DEFAULT 0,
  cost_cents INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ai_model (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_name VARCHAR(100),
  model_code VARCHAR(100),
  provider VARCHAR(100),
  api_url VARCHAR(255),
  api_key VARCHAR(500),
  model_type VARCHAR(50),
  risk_level VARCHAR(20),
  status VARCHAR(20),
  call_limit INT,
  current_calls INT,
  description VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS security_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT,
  event_type VARCHAR(64) NOT NULL,
  file_path VARCHAR(512),
  target_addr VARCHAR(256),
  employee_id VARCHAR(128),
  hostname VARCHAR(128),
  file_size BIGINT,
  severity VARCHAR(20) DEFAULT 'medium',
  status VARCHAR(20) DEFAULT 'pending',
  source VARCHAR(64) DEFAULT 'agent',
  policy_version BIGINT,
  operator_id BIGINT,
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

CREATE TABLE IF NOT EXISTS compliance_evidence_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  evidence_type VARCHAR(64) NOT NULL,
  period_start TIMESTAMP,
  period_end TIMESTAMP,
  policy_hit_count BIGINT DEFAULT 0,
  audit_trace_count BIGINT DEFAULT 0,
  remediation_closed_count BIGINT DEFAULT 0,
  content_json CLOB,
  evidence_hash VARCHAR(128),
  generated_by BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reliability_drill_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  scenario VARCHAR(64) NOT NULL,
  target_path VARCHAR(255),
  inject_path VARCHAR(255),
  baseline_success_rate DOUBLE DEFAULT 0,
  baseline_p95_ms BIGINT DEFAULT 0,
  injected_error_rate DOUBLE DEFAULT 0,
  recovery_seconds BIGINT DEFAULT 0,
  sli_availability DOUBLE DEFAULT 0,
  sli_latency_ms BIGINT DEFAULT 0,
  slo_status VARCHAR(32) DEFAULT 'unknown',
  alert_event_id BIGINT,
  detail_json CLOB,
  executed_by BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_metric_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  api VARCHAR(255) NOT NULL,
  total BIGINT DEFAULT 0,
  success BIGINT DEFAULT 0,
  fail BIGINT DEFAULT 0,
  p50 BIGINT DEFAULT 0,
  p95 BIGINT DEFAULT 0,
  p99 BIGINT DEFAULT 0,
  max BIGINT DEFAULT 0,
  sampled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS web_vital_metric (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  metric_name VARCHAR(32) NOT NULL,
  metric_value DOUBLE DEFAULT 0,
  rating VARCHAR(32),
  metric_id VARCHAR(128),
  navigation_type VARCHAR(32),
  path VARCHAR(255),
  event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS slow_query_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  mapper_method VARCHAR(255) NOT NULL,
  elapsed_ms BIGINT NOT NULL,
  args_digest VARCHAR(128),
  query_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_hash_chain (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  audit_log_id BIGINT NOT NULL,
  prev_hash VARCHAR(128),
  current_hash VARCHAR(128) NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS external_anchor_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  evidence_type VARCHAR(64) NOT NULL,
  evidence_ref VARCHAR(128),
  payload_hash VARCHAR(128) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  source_time VARCHAR(64),
  nonce VARCHAR(64),
  signature_base64 CLOB,
  key_fingerprint VARCHAR(128),
  verify_status VARCHAR(20) DEFAULT 'unknown',
  detail_json CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO company (id, company_code, company_name, status, create_time, update_time)
SELECT 1, 'aegis-default', 'Aegis 默认公司', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM company WHERE id = 1);
