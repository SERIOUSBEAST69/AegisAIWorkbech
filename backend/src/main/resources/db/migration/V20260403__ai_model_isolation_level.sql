ALTER TABLE ai_model
  ADD COLUMN IF NOT EXISTS isolation_level VARCHAR(8) DEFAULT 'L2' COMMENT '模型隔离等级（L0-L4）';

UPDATE ai_model
SET isolation_level = 'L4'
WHERE (isolation_level IS NULL OR TRIM(isolation_level) = '')
  AND LOWER(COALESCE(risk_level, '')) IN ('high', 'critical', '高');

UPDATE ai_model
SET isolation_level = 'L3'
WHERE (isolation_level IS NULL OR TRIM(isolation_level) = '')
  AND LOWER(COALESCE(risk_level, '')) IN ('medium', '中');

UPDATE ai_model
SET isolation_level = 'L2'
WHERE isolation_level IS NULL OR TRIM(isolation_level) = '';
