CREATE TABLE IF NOT EXISTS company_invite_code (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  invite_code VARCHAR(64) NOT NULL,
  status VARCHAR(20) DEFAULT 'active',
  created_by BIGINT,
  expires_at TIMESTAMP NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_company_invite_code ON company_invite_code(invite_code);
CREATE INDEX idx_company_invite_company_status ON company_invite_code(company_id, status);
