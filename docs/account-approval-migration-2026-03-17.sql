-- AegisAI migration: account type + registration approval
-- Date: 2026-03-17

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS account_type VARCHAR(20) DEFAULT 'demo' COMMENT '账号类型 demo/real';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS account_status VARCHAR(20) DEFAULT 'active' COMMENT '账号状态 pending/active/rejected/disabled';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS approved_by BIGINT COMMENT '审批人ID';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(255) COMMENT '拒绝原因';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS approved_at DATETIME COMMENT '审批时间';

UPDATE sys_user
SET account_type = 'demo'
WHERE account_type IS NULL OR account_type = '';

UPDATE sys_user
SET account_status = CASE
  WHEN status = 0 THEN 'disabled'
  ELSE 'active'
END
WHERE account_status IS NULL OR account_status = '';

-- Optional hardening: add index for pending approval lookup
CREATE INDEX idx_user_account_status ON sys_user(account_status);
