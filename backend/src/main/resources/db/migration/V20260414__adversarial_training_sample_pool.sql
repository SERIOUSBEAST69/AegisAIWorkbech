CREATE TABLE IF NOT EXISTS `adversarial_training_sample` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT NOT NULL,
  `task_id` VARCHAR(64) NOT NULL,
  `round_num` INT NOT NULL,
  `attack_type` VARCHAR(64) NOT NULL,
  `attack_strategy` VARCHAR(128) NULL,
  `feature_vector_json` LONGTEXT NULL,
  `predicted_score` DECIMAL(8,4) NULL,
  `predicted_label` VARCHAR(32) NULL,
  `sample_quality` VARCHAR(16) NOT NULL DEFAULT 'medium',
  `used_in_run_id` VARCHAR(64) NULL,
  `raw_payload` LONGTEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_adv_training_sample_company_task_round` (`company_id`, `task_id`, `round_num`),
  INDEX `idx_adv_training_sample_company_used` (`company_id`, `used_in_run_id`, `created_at`),
  INDEX `idx_adv_training_sample_company_attack_type` (`company_id`, `attack_type`, `created_at`)
) COMMENT='攻防模拟增量训练样本池';
