CREATE TABLE IF NOT EXISTS privacy_impact_assessment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  framework VARCHAR(32) DEFAULT 'PIPL',
  impact_score INT NOT NULL,
  risk_level VARCHAR(20) NOT NULL,
  risk_factors_json LONGTEXT,
  assessed_by BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dia_company_asset_time ON privacy_impact_assessment(company_id, asset_id, create_time);
CREATE INDEX idx_dia_company_level_time ON privacy_impact_assessment(company_id, risk_level, create_time);

CREATE TABLE IF NOT EXISTS tenant_health_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  check_at TIMESTAMP NOT NULL,
  permission_gaps_json LONGTEXT,
  audit_coverage DECIMAL(5,2) DEFAULT 0,
  privacy_debt_score INT DEFAULT 0,
  risk_metrics_json LONGTEXT,
  status VARCHAR(20) DEFAULT 'healthy',
  created_by BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_health_company_time ON tenant_health_report(company_id, check_at);
CREATE INDEX idx_tenant_health_company_status_time ON tenant_health_report(company_id, status, check_at);
