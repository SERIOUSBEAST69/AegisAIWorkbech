ALTER TABLE company_invite_code
    ADD COLUMN IF NOT EXISTS disable_reason VARCHAR(255);

CREATE TABLE IF NOT EXISTS role_self_register_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    requested_allow_self_register BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    requested_by BIGINT NOT NULL,
    reviewed_by BIGINT,
    review_note VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_role_src_company_status ON role_self_register_change(company_id, status);
CREATE INDEX idx_role_src_role ON role_self_register_change(role_id);
