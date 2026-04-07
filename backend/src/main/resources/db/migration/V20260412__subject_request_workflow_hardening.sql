-- Subject request workflow hardening:
-- 1) unique business request number
-- 2) request source / deadline fields
-- 3) data backfill for legacy rows

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'subject_request' AND column_name = 'request_no'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE subject_request ADD COLUMN request_no VARCHAR(32) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'subject_request' AND column_name = 'request_source'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE subject_request ADD COLUMN request_source VARCHAR(32) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'subject_request' AND column_name = 'deadline_at'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE subject_request ADD COLUMN deadline_at TIMESTAMP NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE subject_request
SET request_source = 'web'
WHERE request_source IS NULL OR TRIM(request_source) = '';

UPDATE subject_request
SET request_no = CONCAT(DATE_FORMAT(COALESCE(create_time, NOW()), '%Y%m%d%H%i%s'), LPAD(id, 12, '0'))
WHERE request_no IS NULL OR TRIM(request_no) = '';

UPDATE subject_request
SET deadline_at = DATE_ADD(COALESCE(create_time, NOW()), INTERVAL 21 DAY)
WHERE deadline_at IS NULL;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'subject_request' AND index_name = 'uk_subject_request_no'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE UNIQUE INDEX uk_subject_request_no ON subject_request(request_no)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
