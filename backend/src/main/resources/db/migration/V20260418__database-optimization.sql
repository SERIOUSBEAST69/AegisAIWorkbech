-- Database schema optimization for AegisAI Workbench
-- Fixes critical issues to ensure stable system operation

-- ============================================
-- 1. Remove conflicting user table
-- ============================================

DROP TABLE IF EXISTS `user`;

-- ============================================
-- 2. Add constraints to sys_user table
-- ============================================

ALTER TABLE `sys_user` 
MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

ALTER TABLE `sys_user` 
ADD UNIQUE KEY `uk_sys_user_username` (`username`);

-- ============================================
-- 3. Add constraints to user_recycle_bin table
-- ============================================

ALTER TABLE `user_recycle_bin` 
ADD UNIQUE KEY `uk_user_recycle_user_id` (`user_id`);

-- ============================================
-- 4. Add constraints to role table
-- ============================================

ALTER TABLE `role` 
ADD UNIQUE KEY `uk_role_company_code` (`company_id`, `code`);

-- ============================================
-- 5. Add constraints to permission table
-- ============================================

ALTER TABLE `permission` 
ADD UNIQUE KEY `uk_permission_company_code` (`company_id`, `code`);

-- ============================================
-- 6. Add constraints to role_permission table
-- ============================================

ALTER TABLE `role_permission` 
ADD UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`);

-- ============================================
-- 7. Add indexes for governance_change_request
-- ============================================

ALTER TABLE `governance_change_request` 
ADD INDEX `idx_gov_change_company_module_target` (`company_id`, `module`, `target_id`);

-- ============================================
-- 8. Add constraints to sod_conflict_rule
-- ============================================

ALTER TABLE `sod_conflict_rule` 
ADD UNIQUE KEY `uk_sod_roles` (`role_code_a`, `role_code_b`);

-- ============================================
-- 9. Add indexes for event tables
-- ============================================

ALTER TABLE `security_event` 
ADD INDEX `idx_security_event_type` (`company_id`, `event_type`, `event_time`);

ALTER TABLE `privacy_event` 
ADD INDEX `idx_privacy_event_type` (`company_id`, `event_type`, `event_time`);

ALTER TABLE `governance_event` 
ADD INDEX `idx_governance_event_type` (`company_id`, `event_type`, `event_time`);

-- ============================================
-- 10. Add indexes for compliance_policy
-- ============================================

ALTER TABLE `compliance_policy` 
ADD INDEX `idx_policy_type` (`company_id`, `policy_type`);

-- ============================================
-- 11. Add indexes for audit_log
-- ============================================

ALTER TABLE `audit_log` 
ADD INDEX `idx_audit_operation_time` (`user_id`, `operation`, `operation_time`);

-- ============================================
-- 12. Add indexes for ai_call_log
-- ============================================

ALTER TABLE `ai_call_log` 
ADD INDEX `idx_ai_call_model_status` (`model_code`, `status`, `create_time`);

-- ============================================
-- 13. Fix field type consistency for company_id
-- ============================================

ALTER TABLE `user_recycle_bin` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `governance_change_request` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `sod_conflict_rule` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `role` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `permission` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `data_asset` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `approval_request` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `compliance_policy` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `risk_event` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `subject_request` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `security_event` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `privacy_event` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `governance_event` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `adversarial_record` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `tenant_health_report` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `privacy_impact_assessment` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `client_report` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `client_scan_queue` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';

-- ============================================
-- 14. Add missing indexes for better performance
-- ============================================

-- Add index for role_id in sys_user
ALTER TABLE `sys_user` 
ADD INDEX `idx_sys_user_role` (`role_id`);

-- Add index for company_id in sys_user
ALTER TABLE `sys_user` 
ADD INDEX `idx_sys_user_company` (`company_id`);

-- Add index for status in sys_user
ALTER TABLE `sys_user` 
ADD INDEX `idx_sys_user_status` (`status`);

-- Add index for account_status in sys_user
ALTER TABLE `sys_user` 
ADD INDEX `idx_sys_user_account_status` (`account_status`);

-- ============================================
-- 15. Add constraints for data integrity
-- ============================================

-- Add NOT NULL constraints for critical fields
ALTER TABLE `sys_user` 
MODIFY COLUMN `username` VARCHAR(50) NOT NULL COMMENT '用户名',
MODIFY COLUMN `password` VARCHAR(100) NOT NULL COMMENT '密码（加密存储）';

ALTER TABLE `role` 
MODIFY COLUMN `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
MODIFY COLUMN `code` VARCHAR(50) NOT NULL COMMENT '角色编码';

ALTER TABLE `permission` 
MODIFY COLUMN `name` VARCHAR(50) NOT NULL COMMENT '权限名称',
MODIFY COLUMN `code` VARCHAR(50) NOT NULL COMMENT '权限编码';

-- ============================================
-- 16. Clean up redundant indexes
-- ============================================

-- Remove duplicate indexes if they exist
-- Note: These commands will fail if the indexes don't exist, which is fine

-- ALTER TABLE `sys_user` DROP INDEX `idx_username`;
-- ALTER TABLE `sys_user` DROP INDEX `idx_company`;
-- ALTER TABLE `sys_user` DROP INDEX `idx_role`;