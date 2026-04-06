CREATE TABLE IF NOT EXISTS `adversarial_task` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT NOT NULL,
  `task_id` VARCHAR(64) NOT NULL,
  `scenario` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'queued',
  `started_at` DATETIME NULL,
  `finished_at` DATETIME NULL,
  `rounds_planned` INT NOT NULL DEFAULT 12,
  `rounds_completed` INT NOT NULL DEFAULT 0,
  `seed` INT NULL,
  `created_by` BIGINT NULL,
  `created_username` VARCHAR(64) NULL,
  `raw_payload` LONGTEXT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_adversarial_task_company_task` (`company_id`, `task_id`),
  INDEX `idx_adversarial_task_company_status` (`company_id`, `status`, `update_time`)
) COMMENT='对抗任务主表';

CREATE TABLE IF NOT EXISTS `adversarial_round_metric` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT NOT NULL,
  `task_id` VARCHAR(64) NOT NULL,
  `round_num` INT NOT NULL,
  `attack_success_rate` DECIMAL(8,4) NULL,
  `defense_intercept_rate` DECIMAL(8,4) NULL,
  `model_strength_score` INT NULL,
  `threshold_delta` DECIMAL(8,4) NULL,
  `strategy_delta` DECIMAL(8,4) NULL,
  `adaptive_threshold` DECIMAL(8,4) NULL,
  `rule_id` VARCHAR(128) NULL,
  `token_features_json` LONGTEXT NULL,
  `explain_text` LONGTEXT NULL,
  `event_time` DATETIME NULL,
  `raw_payload` LONGTEXT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_adversarial_round` (`company_id`, `task_id`, `round_num`),
  INDEX `idx_adversarial_round_company_task` (`company_id`, `task_id`, `round_num`)
) COMMENT='对抗轮次指标';

CREATE TABLE IF NOT EXISTS `adversarial_event_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT NOT NULL,
  `task_id` VARCHAR(64) NOT NULL,
  `event_time` DATETIME NULL,
  `round_num` INT NULL,
  `event_type` VARCHAR(32) NULL,
  `rule_id` VARCHAR(128) NULL,
  `token_features_json` LONGTEXT NULL,
  `explain_text` LONGTEXT NULL,
  `raw_payload` LONGTEXT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_adversarial_event_dedup` (`company_id`, `task_id`, `event_time`, `round_num`, `event_type`),
  INDEX `idx_adversarial_event_company_task` (`company_id`, `task_id`, `create_time`)
) COMMENT='对抗事件日志';

CREATE TABLE IF NOT EXISTS `adversarial_model_update` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT NOT NULL,
  `task_id` VARCHAR(64) NOT NULL,
  `log_time` DATETIME NULL,
  `round_num` INT NULL,
  `phase` VARCHAR(64) NULL,
  `message` LONGTEXT NULL,
  `raw_payload` LONGTEXT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_adversarial_model_update_dedup` (`company_id`, `task_id`, `log_time`, `round_num`, `phase`),
  INDEX `idx_adversarial_model_update_company_task` (`company_id`, `task_id`, `create_time`)
) COMMENT='对抗训练更新日志';

CREATE TABLE IF NOT EXISTS `adversarial_report` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT NOT NULL,
  `task_id` VARCHAR(64) NOT NULL,
  `report_file` VARCHAR(512) NULL,
  `report_json` LONGTEXT NULL,
  `generated_at` DATETIME NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_adversarial_report_company_task` (`company_id`, `task_id`),
  INDEX `idx_adversarial_report_company_time` (`company_id`, `update_time`)
) COMMENT='对抗优化报告';
