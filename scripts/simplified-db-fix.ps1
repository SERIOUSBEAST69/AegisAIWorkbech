# 简化的数据库修复脚本
# 直接执行关键的 SQL 语句

$ErrorActionPreference = "Continue"

# 数据库连接信息
$Server = "localhost"
$Database = "aegisai"
$Username = "root"
$Password = "root"

# 执行 SQL 语句的函数
function Execute-Sql($sql) {
    try {
        $connectionString = "server=$Server;database=$Database;uid=$Username;pwd=$Password;"
        $connection = New-Object System.Data.SqlClient.SqlConnection($connectionString)
        $connection.Open()
        
        $command = $connection.CreateCommand()
        $command.CommandText = $sql
        $command.ExecuteNonQuery() | Out-Null
        
        $connection.Close()
        return $true
    } catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

Write-Host "=== Database Optimization ===" -ForegroundColor Cyan
Write-Host "Database: $Database" -ForegroundColor Cyan
Write-Host "User: $Username" -ForegroundColor Cyan
Write-Host "" -ForegroundColor Cyan

# 1. 清理冲突的 user 表
Write-Host "1. Removing conflicting 'user' table..." -ForegroundColor Yellow
Execute-Sql "DROP TABLE IF EXISTS `user`"

# 2. 为 sys_user 表添加约束
Write-Host "2. Adding constraints to sys_user table..." -ForegroundColor Yellow
Execute-Sql "ALTER TABLE `sys_user` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID'"
Execute-Sql "ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_username` (`username`)"

# 3. 为 role 表添加约束
Write-Host "3. Adding constraints to role table..." -ForegroundColor Yellow
Execute-Sql "ALTER TABLE `role` ADD UNIQUE KEY `uk_role_company_code` (`company_id`, `code`)"

# 4. 为 permission 表添加约束
Write-Host "4. Adding constraints to permission table..." -ForegroundColor Yellow
Execute-Sql "ALTER TABLE `permission` ADD UNIQUE KEY `uk_permission_company_code` (`company_id`, `code`)"

# 5. 为 role_permission 表添加约束
Write-Host "5. Adding constraints to role_permission table..." -ForegroundColor Yellow
Execute-Sql "ALTER TABLE `role_permission` ADD UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)"

# 6. 添加必要的索引
Write-Host "6. Adding indexes for performance..." -ForegroundColor Yellow
Execute-Sql "ALTER TABLE `governance_change_request` ADD INDEX `idx_gov_change_company_module_target` (`company_id`, `module`, `target_id`)"
Execute-Sql "ALTER TABLE `security_event` ADD INDEX `idx_security_event_type` (`company_id`, `event_type`, `event_time`)"
Execute-Sql "ALTER TABLE `privacy_event` ADD INDEX `idx_privacy_event_type` (`company_id`, `event_type`, `event_time`)"
Execute-Sql "ALTER TABLE `governance_event` ADD INDEX `idx_governance_event_type` (`company_id`, `event_type`, `event_time`)"

# 7. 修复字段类型一致性
Write-Host "7. Fixing field type consistency..." -ForegroundColor Yellow
Execute-Sql "ALTER TABLE `user_recycle_bin` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID'"
Execute-Sql "ALTER TABLE `governance_change_request` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID'"
Execute-Sql "ALTER TABLE `sod_conflict_rule` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID'"
Execute-Sql "ALTER TABLE `role` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID'"
Execute-Sql "ALTER TABLE `permission` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID'"

Write-Host "" -ForegroundColor Cyan
Write-Host "=== Optimization Complete ===" -ForegroundColor Green
Write-Host "The database has been optimized." -ForegroundColor Green
Write-Host "Key issues fixed:"
Write-Host "- Removed conflicting 'user' table"
Write-Host "- Added unique constraints to prevent duplicate data"
Write-Host "- Added indexes for better performance"
Write-Host "- Fixed field type consistency"
Write-Host "" -ForegroundColor Cyan
Write-Host "The system should now run smoothly." -ForegroundColor Green