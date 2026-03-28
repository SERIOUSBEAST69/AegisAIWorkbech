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

INSERT INTO company (id, company_code, company_name, status, create_time, update_time)
SELECT 1, 'aegis-default', 'Aegis 默认公司', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM company WHERE id = 1);
