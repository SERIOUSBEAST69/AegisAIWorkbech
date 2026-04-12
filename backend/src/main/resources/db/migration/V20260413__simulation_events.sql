CREATE TABLE IF NOT EXISTS simulation_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    target_key VARCHAR(128) NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'high',
    status VARCHAR(24) NOT NULL DEFAULT 'pending',
    source VARCHAR(64) NOT NULL DEFAULT 'client-trigger',
    trigger_user VARCHAR(64) NULL,
    payload_json LONGTEXT NULL,
    processed_by VARCHAR(64) NULL,
    processed_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sim_event_company_status_time (company_id, status, create_time),
    KEY idx_sim_event_company_id (company_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;