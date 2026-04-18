-- 数据库表结构优化脚本
-- 修复重要的错误，确保系统平稳运行

-- ============================================
-- 1. 清理冲突的 user 表
-- ============================================

-- 先检查 user 表是否存在
DROP TABLE IF EXISTS `user`;

-- ============================================
-- 2. 为 sys_user 表添加必要的约束
-- ============================================

-- 添加 company_id 的 NOT NULL 约束
ALTER TABLE `sys_user` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

-- 添加 username 的唯一约束
ALTER TABLE `sys_user` 
ADD UNIQUE KEY `uk_username` (`username`);

-- ============================================
-- 3. 为 user_recycle_bin 表添加约束
-- ============================================

-- 添加 user_id 的唯一约束（防止重复回收）
ALTER TABLE `user_recycle_bin` 
ADD UNIQUE KEY `uk_user_id` (`user_id`) ON DELETE CASCADE;

-- ============================================
-- 4. 为 role 表添加约束
-- ============================================

-- 添加 company_id 和 code 的联合唯一约束
ALTER TABLE `role` 
ADD UNIQUE KEY `uk_role_company_code` (`company_id`, `code`);

-- ============================================
-- 5. 为 permission 表添加约束
-- ============================================

-- 添加 company_id 和 code 的联合唯一约束
ALTER TABLE `permission` 
ADD UNIQUE KEY `uk_permission_company_code` (`company_id`, `code`);

-- ============================================
-- 6. 为 role_permission 表添加约束
-- ============================================

-- 添加 (role_id, permission_id) 的联合唯一约束
ALTER TABLE `role_permission` 
ADD UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`);

-- ============================================
-- 7. 为 governance_change_request 表添加索引
-- ============================================

-- 添加复合索引
ALTER TABLE `governance_change_request` 
ADD INDEX `idx_gov_change_company_module_target` (`company_id`, `module`, `target_id`);

-- ============================================
-- 8. 为 sod_conflict_rule 表添加约束
-- ============================================

-- 添加 (role_code_a, role_code_b) 的唯一约束
ALTER TABLE `sod_conflict_rule` 
ADD UNIQUE KEY `uk_sod_roles` (`role_code_a`, `role_code_b`);

-- ============================================
-- 9. 为事件相关表添加索引
-- ============================================

-- security_event 表
ALTER TABLE `security_event` 
ADD INDEX `idx_security_event_type` (`company_id`, `event_type`, `event_time`);

-- privacy_event 表
ALTER TABLE `privacy_event` 
ADD INDEX `idx_privacy_event_type` (`company_id`, `event_type`, `event_time`);

-- governance_event 表
ALTER TABLE `governance_event` 
ADD INDEX `idx_governance_event_type` (`company_id`, `event_type`, `event_time`);

-- ============================================
-- 10. 为合规策略表添加索引
-- ============================================

-- compliance_policy 表
ALTER TABLE `compliance_policy` 
ADD INDEX `idx_policy_type` (`company_id`, `policy_type`);

-- ============================================
-- 11. 为审计日志表添加索引
-- ============================================

-- audit_log 表
ALTER TABLE `audit_log` 
ADD INDEX `idx_audit_operation_time` (`user_id`, `operation`, `operation_time`);

-- ============================================
-- 12. 为 AI 调用日志表添加索引
-- ============================================

-- ai_call_log 表
ALTER TABLE `ai_call_log` 
ADD INDEX `idx_ai_call_model_status` (`model_code`, `status`, `create_time`);

-- ============================================
-- 13. 修复字段类型一致性
-- ============================================

-- 确保所有 company_id 字段类型一致
ALTER TABLE `user_recycle_bin` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `governance_change_request` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `sod_conflict_rule` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `role` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `permission` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `data_asset` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `approval_request` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `compliance_policy` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `risk_event` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `subject_request` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `security_event` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `privacy_event` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `governance_event` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `adversarial_record` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `tenant_health_report` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `privacy_impact_assessment` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `client_report` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `client_scan_queue` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

-- ============================================
-- 14. 添加外键约束（可选，根据实际情况）
-- ============================================

-- 注意：外键约束会影响性能，建议在生产环境谨慎使用
-- 以下是推荐的外键约束

-- sys_user -> role
-- ALTER TABLE `sys_user` 
-- ADD CONSTRAINT `fk_user_role` 
-- FOREIGN KEY (`role_id`) 
-- REFERENCES `role` (`id`) 
-- ON DELETE SET NULL;

-- user_recycle_bin -> sys_user
-- ALTER TABLE `user_recycle_bin` 
-- ADD CONSTRAINT `fk_recycle_user` 
-- FOREIGN KEY (`user_id`) 
-- REFERENCES `sys_user` (`id`) 
-- ON DELETE CASCADE;

-- role_permission -> role
-- ALTER TABLE `role_permission` 
-- ADD CONSTRAINT `fk_rp_role` 
-- FOREIGN KEY (`role_id`) 
-- REFERENCES `role` (`id`) 
-- ON DELETE CASCADE;

-- role_permission -> permission
-- ALTER TABLE `role_permission` 
-- ADD CONSTRAINT `fk_rp_permission` 
-- FOREIGN KEY (`permission_id`) 
-- REFERENCES `permission` (`id`) 
-- ON DELETE CASCADE;

-- ============================================
-- 完成
-- ============================================

-- 清理完成，系统应该能够平稳运行
-- 建议在执行此脚本前进行数据库备份