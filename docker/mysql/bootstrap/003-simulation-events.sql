CREATE TABLE IF NOT EXISTS simulation_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  target_key VARCHAR(255) DEFAULT NULL,
  severity VARCHAR(32) DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  source VARCHAR(64) DEFAULT NULL,
  trigger_user VARCHAR(128) DEFAULT NULL,
  payload_json LONGTEXT,
  processed_by VARCHAR(128) DEFAULT NULL,
  processed_at DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sim_events_company_status_id (company_id, status, id),
  KEY idx_sim_events_company_create (company_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
