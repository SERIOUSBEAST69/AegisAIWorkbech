-- 2026-03-17
-- AegisAI: company isolation + module prune migration

START TRANSACTION;

-- 1) company master table
CREATE TABLE IF NOT EXISTS company (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_code VARCHAR(64) NOT NULL,
  company_name VARCHAR(128) NOT NULL,
  status TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_company_code (company_code)
) COMMENT='公司主数据表';

INSERT INTO company (company_code, company_name, status)
SELECT 'aegis-default', 'Aegis 默认公司', 1
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_code = 'aegis-default');

-- 2) tenant columns
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE data_asset ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE security_event ADD COLUMN IF NOT EXISTS company_id BIGINT;

-- 3) backfill existing rows
UPDATE sys_user SET company_id = 1 WHERE company_id IS NULL;
UPDATE data_asset SET company_id = 1 WHERE company_id IS NULL;
UPDATE risk_event SET company_id = 1 WHERE company_id IS NULL;
UPDATE security_event SET company_id = 1 WHERE company_id IS NULL;

-- 4) prune removed module tables
-- keep ai_model/model_call_stat for runtime AI gateway + dashboard baseline metrics.
DROP TABLE IF EXISTS data_share_request;
DROP TABLE IF EXISTS alert_record;

COMMIT;
