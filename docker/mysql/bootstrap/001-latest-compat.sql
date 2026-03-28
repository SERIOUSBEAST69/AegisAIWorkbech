CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `nickname` VARCHAR(50) COMMENT '昵称',
  `avatar` VARCHAR(255) COMMENT '头像地址',
  `role_id` BIGINT COMMENT '角色ID',
  `device_id` VARCHAR(128) COMMENT '设备ID',
  `department` VARCHAR(50) COMMENT '部门',
  `phone` VARCHAR(20) COMMENT '联系方式',
  `email` VARCHAR(100) COMMENT '邮箱',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1-正常 0-禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_username(`username`),
  INDEX idx_role(`role_id`)
) COMMENT='系统用户表';

SET @has_sys_user_nickname := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'nickname'
);
SET @sql_add_sys_user_nickname := IF(
  @has_sys_user_nickname = 0,
  'ALTER TABLE sys_user ADD COLUMN nickname VARCHAR(50) COMMENT ''昵称'' AFTER real_name',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_sys_user_nickname;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_sys_user_avatar := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'avatar'
);
SET @sql_add_sys_user_avatar := IF(
  @has_sys_user_avatar = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(255) COMMENT ''头像地址'' AFTER nickname',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_sys_user_avatar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_sys_user_device_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'device_id'
);
SET @sql_add_sys_user_device_id := IF(
  @has_sys_user_device_id = 0,
  'ALTER TABLE sys_user ADD COLUMN device_id VARCHAR(128) COMMENT ''设备ID'' AFTER role_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_sys_user_device_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @legacy_user_exists := (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'user'
);

SET @sync_user_sql := IF(
  @legacy_user_exists > 0,
  'INSERT INTO sys_user (id, username, password, real_name, nickname, avatar, role_id, device_id, department, phone, email, status, create_time, update_time) '
  'SELECT u.id, u.username, u.password, u.real_name, COALESCE(NULLIF(u.real_name, ""), u.username), NULL, u.role_id, CONCAT(u.username, ''-device''), u.department, u.phone, u.email, u.status, u.create_time, u.update_time '
  'FROM user u '
  'INNER JOIN ( '
  '  SELECT username, COALESCE(MAX(CASE WHEN password LIKE ''$2a$%%'' THEN id END), MAX(id)) AS keep_id '
  '  FROM user GROUP BY username '
  ') picked ON picked.keep_id = u.id '
  'LEFT JOIN sys_user s ON s.username = (u.username COLLATE utf8mb4_unicode_ci) '
  'WHERE s.id IS NULL',
  'SELECT 1'
);

PREPARE sync_user_stmt FROM @sync_user_sql;
EXECUTE sync_user_stmt;
DEALLOCATE PREPARE sync_user_stmt;

SET @has_audit_log_risk_level := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'risk_level'
);
SET @sql_add_audit_log_risk_level := IF(
  @has_audit_log_risk_level = 0,
  'ALTER TABLE audit_log ADD COLUMN risk_level VARCHAR(20) COMMENT ''风险等级'' AFTER result',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_audit_log_risk_level;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_risk_event_audit_log_ids := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'risk_event' AND column_name = 'audit_log_ids'
);
SET @sql_add_risk_event_audit_log_ids := IF(
  @has_risk_event_audit_log_ids = 0,
  'ALTER TABLE risk_event ADD COLUMN audit_log_ids VARCHAR(500) COMMENT ''关联审计日志ID集合'' AFTER related_log_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_risk_event_audit_log_ids;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `desense_recommend_rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `data_category` VARCHAR(50) COMMENT '数据分类：如身份证、银行卡',
  `user_role` VARCHAR(50) COMMENT '调用方角色：如admin/auditor',
  `strategy` VARCHAR(50) COMMENT '策略：mask/hash/tokenize',
  `rule_id` BIGINT COMMENT '关联脱敏规则ID',
  `priority` INT DEFAULT 0 COMMENT '优先级，越小越高',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_cat_role(`data_category`, `user_role`)
) COMMENT='脱敏推荐规则表';

CREATE TABLE IF NOT EXISTS `system_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `config_key` VARCHAR(128) NOT NULL UNIQUE COMMENT '配置键',
  `config_value` TEXT NOT NULL COMMENT '配置值',
  `description` VARCHAR(255) COMMENT '配置说明',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS `security_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `file_path` VARCHAR(512) COMMENT '涉及文件路径',
  `target_addr` VARCHAR(256) COMMENT '目标地址（模拟远端）',
  `employee_id` VARCHAR(128) COMMENT '员工标识',
  `hostname` VARCHAR(128) COMMENT '主机名',
  `file_size` BIGINT COMMENT '文件大小（字节）',
  `severity` VARCHAR(20) DEFAULT 'medium' COMMENT 'critical/high/medium/low',
  `status` VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/blocked/ignored/reviewing',
  `source` VARCHAR(64) DEFAULT 'agent' COMMENT '上报来源',
  `operator_id` BIGINT COMMENT '操作者ID',
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_event_status(`status`),
  INDEX idx_event_severity(`severity`),
  INDEX idx_event_employee(`employee_id`),
  INDEX idx_event_time(`event_time`)
) COMMENT='安全事件表';

CREATE TABLE IF NOT EXISTS `security_detection_rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL,
  `sensitive_extensions` VARCHAR(500) DEFAULT '.docx,.pdf,.xlsx,.pptx,.key,.csv,.sql,.env,.pem,.pfx',
  `sensitive_paths` VARCHAR(1000) DEFAULT 'C:/Users,/home,/Documents,/Desktop',
  `alert_threshold_bytes` BIGINT DEFAULT 1048576,
  `enabled` TINYINT(1) DEFAULT 1,
  `description` VARCHAR(500),
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='安全检测规则表';

CREATE TABLE IF NOT EXISTS `privacy_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` VARCHAR(128) NOT NULL COMMENT '用户标识（用户名）',
  `event_type` VARCHAR(64) DEFAULT 'SENSITIVE_TEXT' COMMENT '事件类型',
  `content_masked` TEXT COMMENT '脱敏后的内容',
  `source` VARCHAR(32) DEFAULT 'extension' COMMENT '来源：extension/clipboard',
  `action` VARCHAR(32) DEFAULT 'detect' COMMENT '动作：ignore/desensitize/detect',
  `device_id` VARCHAR(128) COMMENT '设备ID',
  `hostname` VARCHAR(128) COMMENT '主机名',
  `window_title` VARCHAR(255) COMMENT '窗口标题',
  `matched_types` VARCHAR(255) COMMENT '命中的敏感类型',
  `event_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_privacy_user(`user_id`),
  INDEX idx_privacy_source(`source`),
  INDEX idx_privacy_time(`event_time`)
) COMMENT='隐私盾事件表';

-- AI 调用审计日志表（含 data_asset_id）
CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `user_id` BIGINT DEFAULT NULL COMMENT '调用用户',
  `data_asset_id` BIGINT DEFAULT NULL COMMENT '关联数据资产ID',
  `model_id` BIGINT DEFAULT NULL COMMENT '模型ID',
  `model_code` VARCHAR(100) DEFAULT NULL COMMENT '模型代码',
  `provider` VARCHAR(50) DEFAULT NULL COMMENT '供应商',
  `input_preview` VARCHAR(200) DEFAULT NULL COMMENT '输入预览（已脱敏）',
  `output_preview` VARCHAR(200) DEFAULT NULL COMMENT '输出预览（已脱敏）',
  `status` VARCHAR(20) DEFAULT NULL COMMENT 'success/fail',
  `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时毫秒',
  `token_usage` INT DEFAULT NULL COMMENT 'token 用量',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT '调用者IP',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_model_code_date(`model_code`, `create_time`),
  INDEX idx_user_date(`user_id`, `create_time`)
) COMMENT='AI 调用审计日志';

-- 若 ai_call_log 已存在但缺少 data_asset_id 列，则补充
SET @has_ai_call_log_data_asset_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'data_asset_id'
);
SET @sql_add_ai_call_log_data_asset_id := IF(
  @has_ai_call_log_data_asset_id = 0,
  'ALTER TABLE ai_call_log ADD COLUMN data_asset_id BIGINT DEFAULT NULL COMMENT ''关联数据资产ID'' AFTER user_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_data_asset_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 model_code 列，则补充
SET @has_ai_call_log_model_code := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'model_code'
);
SET @sql_add_ai_call_log_model_code := IF(
  @has_ai_call_log_model_code = 0,
  'ALTER TABLE ai_call_log ADD COLUMN model_code VARCHAR(100) DEFAULT NULL COMMENT ''模型代码'' AFTER model_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_model_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 provider 列，则补充
SET @has_ai_call_log_provider := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'provider'
);
SET @sql_add_ai_call_log_provider := IF(
  @has_ai_call_log_provider = 0,
  'ALTER TABLE ai_call_log ADD COLUMN provider VARCHAR(50) DEFAULT NULL COMMENT ''供应商'' AFTER model_code',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_provider;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 input_preview 列，则补充
SET @has_ai_call_log_input_preview := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'input_preview'
);
SET @sql_add_ai_call_log_input_preview := IF(
  @has_ai_call_log_input_preview = 0,
  'ALTER TABLE ai_call_log ADD COLUMN input_preview VARCHAR(200) DEFAULT NULL COMMENT ''输入预览（已脱敏）'' AFTER provider',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_input_preview;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 output_preview 列，则补充
SET @has_ai_call_log_output_preview := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'output_preview'
);
SET @sql_add_ai_call_log_output_preview := IF(
  @has_ai_call_log_output_preview = 0,
  'ALTER TABLE ai_call_log ADD COLUMN output_preview VARCHAR(200) DEFAULT NULL COMMENT ''输出预览（已脱敏）'' AFTER input_preview',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_output_preview;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 error_msg 列，则补充
SET @has_ai_call_log_error_msg := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'error_msg'
);
SET @sql_add_ai_call_log_error_msg := IF(
  @has_ai_call_log_error_msg = 0,
  'ALTER TABLE ai_call_log ADD COLUMN error_msg VARCHAR(500) DEFAULT NULL COMMENT ''失败原因'' AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_error_msg;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 duration_ms 列，则补充
SET @has_ai_call_log_duration_ms := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'duration_ms'
);
SET @sql_add_ai_call_log_duration_ms := IF(
  @has_ai_call_log_duration_ms = 0,
  'ALTER TABLE ai_call_log ADD COLUMN duration_ms BIGINT DEFAULT NULL COMMENT ''耗时毫秒'' AFTER error_msg',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_duration_ms;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 token_usage 列，则补充
SET @has_ai_call_log_token_usage := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'token_usage'
);
SET @sql_add_ai_call_log_token_usage := IF(
  @has_ai_call_log_token_usage = 0,
  'ALTER TABLE ai_call_log ADD COLUMN token_usage INT DEFAULT NULL COMMENT ''token 用量'' AFTER duration_ms',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_token_usage;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 若 ai_call_log 已存在但缺少 ip 列，则补充
SET @has_ai_call_log_ip := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_call_log' AND column_name = 'ip'
);
SET @sql_add_ai_call_log_ip := IF(
  @has_ai_call_log_ip = 0,
  'ALTER TABLE ai_call_log ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT ''调用者IP'' AFTER token_usage',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_ai_call_log_ip;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 租户与审批兼容补丁（历史库无对应列时自动补齐）
SET @has_role_company_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'role' AND column_name = 'company_id'
);
SET @sql_add_role_company_id := IF(
  @has_role_company_id = 0,
  'ALTER TABLE role ADD COLUMN company_id BIGINT',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_role_company_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_approval_company_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'approval_request' AND column_name = 'company_id'
);
SET @sql_add_approval_company_id := IF(
  @has_approval_company_id = 0,
  'ALTER TABLE approval_request ADD COLUMN company_id BIGINT',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_approval_company_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_approval_process_instance_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'approval_request' AND column_name = 'process_instance_id'
);
SET @sql_add_approval_process_instance_id := IF(
  @has_approval_process_instance_id = 0,
  'ALTER TABLE approval_request ADD COLUMN process_instance_id VARCHAR(64)',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_approval_process_instance_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_approval_task_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'approval_request' AND column_name = 'task_id'
);
SET @sql_add_approval_task_id := IF(
  @has_approval_task_id = 0,
  'ALTER TABLE approval_request ADD COLUMN task_id VARCHAR(64)',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_approval_task_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_subject_company_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'subject_request' AND column_name = 'company_id'
);
SET @sql_add_subject_company_id := IF(
  @has_subject_company_id = 0,
  'ALTER TABLE subject_request ADD COLUMN company_id BIGINT',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_subject_company_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_policy_company_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'compliance_policy' AND column_name = 'company_id'
);
SET @sql_add_policy_company_id := IF(
  @has_policy_company_id = 0,
  'ALTER TABLE compliance_policy ADD COLUMN company_id BIGINT',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_policy_company_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_security_event_company_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'security_event' AND column_name = 'company_id'
);
SET @sql_add_security_event_company_id := IF(
  @has_security_event_company_id = 0,
  'ALTER TABLE security_event ADD COLUMN company_id BIGINT',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_security_event_company_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_client_report_company_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'client_report' AND column_name = 'company_id'
);
SET @sql_add_client_report_company_id := IF(
  @has_client_report_company_id = 0,
  'ALTER TABLE client_report ADD COLUMN company_id BIGINT',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_client_report_company_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_client_scan_queue_company_id := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'client_scan_queue' AND column_name = 'company_id'
);
SET @sql_add_client_scan_queue_company_id := IF(
  @has_client_scan_queue_company_id = 0,
  'ALTER TABLE client_scan_queue ADD COLUMN company_id BIGINT',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_client_scan_queue_company_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE role SET company_id = 1 WHERE company_id IS NULL;
UPDATE approval_request SET company_id = 1 WHERE company_id IS NULL;
UPDATE subject_request SET company_id = 1 WHERE company_id IS NULL;
UPDATE compliance_policy SET company_id = 1 WHERE company_id IS NULL;
UPDATE security_event SET company_id = 1 WHERE company_id IS NULL;
UPDATE client_report SET company_id = 1 WHERE company_id IS NULL;
UPDATE client_scan_queue SET company_id = 1 WHERE company_id IS NULL;