-- Tenant hardening migration (MySQL)
-- 1) force company_id NOT NULL where applicable
-- 2) add company/user indexes for high-frequency isolation queries
-- 3) add company_id null-guard triggers

-- Ensure user semantic field exists
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS job_title VARCHAR(128) NULL;

-- Company null backfill before NOT NULL
UPDATE role SET company_id = 1 WHERE company_id IS NULL;
UPDATE permission SET company_id = 1 WHERE company_id IS NULL;
UPDATE data_asset SET company_id = 1 WHERE company_id IS NULL;
UPDATE risk_event SET company_id = 1 WHERE company_id IS NULL;
UPDATE security_event SET company_id = 1 WHERE company_id IS NULL;
UPDATE approval_request SET company_id = 1 WHERE company_id IS NULL;
UPDATE subject_request SET company_id = 1 WHERE company_id IS NULL;
UPDATE compliance_policy SET company_id = 1 WHERE company_id IS NULL;
UPDATE client_report SET company_id = 1 WHERE company_id IS NULL;
UPDATE client_scan_queue SET company_id = 1 WHERE company_id IS NULL;
UPDATE ai_call_log l LEFT JOIN sys_user u ON l.user_id = u.id
SET l.company_id = COALESCE(l.company_id, u.company_id, 1),
    l.username = COALESCE(l.username, u.username)
WHERE l.company_id IS NULL OR l.username IS NULL OR l.username = '';
UPDATE privacy_event SET company_id = 1 WHERE company_id IS NULL;

ALTER TABLE role MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE permission MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE data_asset MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE risk_event MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE security_event MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE approval_request MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE subject_request MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE compliance_policy MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE client_report MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE client_scan_queue MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE ai_call_log MODIFY COLUMN company_id BIGINT NOT NULL;
ALTER TABLE privacy_event MODIFY COLUMN company_id BIGINT NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_call_company_time ON ai_call_log(company_id, create_time);
CREATE INDEX IF NOT EXISTS idx_ai_call_company_user_time ON ai_call_log(company_id, user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_client_report_company_client_scan ON client_report(company_id, client_id, scan_time);
CREATE INDEX IF NOT EXISTS idx_client_queue_company_download ON client_scan_queue(company_id, download_time);

DELIMITER $$

DROP TRIGGER IF EXISTS trg_role_company_nn_ins $$
CREATE TRIGGER trg_role_company_nn_ins BEFORE INSERT ON role
FOR EACH ROW
BEGIN
  IF NEW.company_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'role.company_id cannot be NULL';
  END IF;
END $$

DROP TRIGGER IF EXISTS trg_role_company_nn_upd $$
CREATE TRIGGER trg_role_company_nn_upd BEFORE UPDATE ON role
FOR EACH ROW
BEGIN
  IF NEW.company_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'role.company_id cannot be NULL';
  END IF;
END $$

DROP TRIGGER IF EXISTS trg_client_report_company_nn_ins $$
CREATE TRIGGER trg_client_report_company_nn_ins BEFORE INSERT ON client_report
FOR EACH ROW
BEGIN
  IF NEW.company_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'client_report.company_id cannot be NULL';
  END IF;
END $$

DROP TRIGGER IF EXISTS trg_security_event_company_nn_ins $$
CREATE TRIGGER trg_security_event_company_nn_ins BEFORE INSERT ON security_event
FOR EACH ROW
BEGIN
  IF NEW.company_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'security_event.company_id cannot be NULL';
  END IF;
END $$

DROP TRIGGER IF EXISTS trg_ai_call_log_company_nn_ins $$
CREATE TRIGGER trg_ai_call_log_company_nn_ins BEFORE INSERT ON ai_call_log
FOR EACH ROW
BEGIN
  IF NEW.company_id IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_call_log.company_id cannot be NULL';
  END IF;
END $$

DELIMITER ;
