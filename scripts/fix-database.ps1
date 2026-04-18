# 修复数据库问题的 PowerShell 脚本
# 执行 SQL 语句来修复表结构问题

$ErrorActionPreference = "Stop"

# SQL 连接信息
$Server = "localhost"
$Database = "aegisai"
$Username = "root"
$Password = "root"  # 请根据实际情况修改

# 构建 SQL 语句
$SqlQueries = @'
-- 1. 清理冲突的 user 表
DROP TABLE IF EXISTS `user`;

-- 2. 为 sys_user 表添加必要的约束
ALTER TABLE `sys_user` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_username` (`username`);

-- 3. 为 user_recycle_bin 表添加约束
ALTER TABLE `user_recycle_bin` ADD UNIQUE KEY `uk_user_id` (`user_id`);

-- 4. 为 role 表添加约束
ALTER TABLE `role` ADD UNIQUE KEY `uk_role_company_code` (`company_id`, `code`);

-- 5. 为 permission 表添加约束
ALTER TABLE `permission` ADD UNIQUE KEY `uk_permission_company_code` (`company_id`, `code`);

-- 6. 为 role_permission 表添加约束
ALTER TABLE `role_permission` ADD UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`);

-- 7. 为 governance_change_request 表添加索引
ALTER TABLE `governance_change_request` ADD INDEX `idx_gov_change_company_module_target` (`company_id`, `module`, `target_id`);

-- 8. 为 sod_conflict_rule 表添加约束
ALTER TABLE `sod_conflict_rule` ADD UNIQUE KEY `uk_sod_roles` (`role_code_a`, `role_code_b`);

-- 9. 为事件相关表添加索引
ALTER TABLE `security_event` ADD INDEX `idx_security_event_type` (`company_id`, `event_type`, `event_time`);
ALTER TABLE `privacy_event` ADD INDEX `idx_privacy_event_type` (`company_id`, `event_type`, `event_time`);
ALTER TABLE `governance_event` ADD INDEX `idx_governance_event_type` (`company_id`, `event_type`, `event_time`);

-- 10. 为合规策略表添加索引
ALTER TABLE `compliance_policy` ADD INDEX `idx_policy_type` (`company_id`, `policy_type`);

-- 11. 为审计日志表添加索引
ALTER TABLE `audit_log` ADD INDEX `idx_audit_operation_time` (`user_id`, `operation`, `operation_time`);

-- 12. 为 AI 调用日志表添加索引
ALTER TABLE `ai_call_log` ADD INDEX `idx_ai_call_model_status` (`model_code`, `status`, `create_time`);

-- 13. 修复字段类型一致性
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
'@

try {
    # 加载 MySQL .NET 驱动
    Add-Type -Path "C:\Program Files\MySQL\MySQL Connector NET 8.0\Assemblies\v4.8\MySql.Data.dll" -ErrorAction SilentlyContinue
    
    # 创建连接
    $ConnectionString = "server=$Server;database=$Database;uid=$Username;pwd=$Password;"
    $Connection = New-Object MySql.Data.MySqlClient.MySqlConnection($ConnectionString)
    $Connection.Open()
    
    Write-Host "Connected to database: $Database" -ForegroundColor Green
    
    # 执行 SQL 语句
    $Queries = $SqlQueries -split ';'
    foreach ($Query in $Queries) {
        $Query = $Query.Trim()
        if ([string]::IsNullOrWhiteSpace($Query)) {
            continue
        }
        
        Write-Host "Executing: $($Query.Substring(0, [Math]::Min(50, $Query.Length)))..." -ForegroundColor Cyan
        
        $Command = New-Object MySql.Data.MySqlClient.MySqlCommand($Query, $Connection)
        $Command.ExecuteNonQuery() | Out-Null
        
        Write-Host "  ✓ Success" -ForegroundColor Green
    }
    
    $Connection.Close()
    Write-Host "`nDatabase fixes completed successfully!" -ForegroundColor Green
    
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}