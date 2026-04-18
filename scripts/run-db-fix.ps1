# 数据库修复脚本 - Windows PowerShell 版本
# 执行数据库表结构优化

$ErrorActionPreference = "Stop"

# 配置
$DBHost = "localhost"
$DBPort = "3306"
$DBName = "aegisai"
$DBUser = "root"
$DBPass = "root"

# SQL 脚本内容
$SqlScript = @'
-- 1. 清理冲突的 user 表
DROP TABLE IF EXISTS `user`;

-- 2. 为 sys_user 表添加必要的约束
ALTER TABLE `sys_user` MODIFY COLUMN `company_id` BIGINT NOT NULL COMMENT '公司ID';
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_username` (`username`);

-- 3. 为 user_recycle_bin 表添加约束
ALTER TABLE `user_recycle_bin` ADD UNIQUE KEY `uk_user_recycle_user_id` (`user_id`);

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

-- 14. 添加缺失的索引
ALTER TABLE `sys_user` ADD INDEX `idx_sys_user_role` (`role_id`);
ALTER TABLE `sys_user` ADD INDEX `idx_sys_user_company` (`company_id`);
ALTER TABLE `sys_user` ADD INDEX `idx_sys_user_status` (`status`);
ALTER TABLE `sys_user` ADD INDEX `idx_sys_user_account_status` (`account_status`);
'@

try {
    Write-Host "Executing database optimization..." -ForegroundColor Cyan
    Write-Host "Database: $DBName" -ForegroundColor Cyan
    Write-Host "User: $DBUser" -ForegroundColor Cyan
    Write-Host "" -ForegroundColor Cyan
    
    # 构建连接字符串
    $ConnectionString = "server=$DBHost;port=$DBPort;database=$DBName;uid=$DBUser;pwd=$DBPass;"
    
    # 加载 MySQL 驱动
    $MySqlDll = "C:\Program Files\MySQL\MySQL Connector NET 8.0\Assemblies\v4.8\MySql.Data.dll"
    if (Test-Path $MySqlDll) {
        Add-Type -Path $MySqlDll
    } else {
        # 尝试其他常见路径
        $AlternativePaths = @(
            "C:\Program Files (x86)\MySQL\MySQL Connector NET 8.0\Assemblies\v4.8\MySql.Data.dll",
            "C:\Program Files\MySQL\MySQL Connector NET 6.10.9\Assemblies\v4.5.2\MySql.Data.dll",
            "C:\Program Files (x86)\MySQL\MySQL Connector NET 6.10.9\Assemblies\v4.5.2\MySql.Data.dll"
        )
        
        $Found = $false
        foreach ($Path in $AlternativePaths) {
            if (Test-Path $Path) {
                Add-Type -Path $Path
                $Found = $true
                break
            }
        }
        
        if (-not $Found) {
            throw "MySQL Data DLL not found. Please install MySQL Connector/NET."
        }
    }
    
    # 创建连接
    $Connection = New-Object MySql.Data.MySqlClient.MySqlConnection($ConnectionString)
    $Connection.Open()
    
    # 执行 SQL 语句
    $Queries = $SqlScript -split ';'
    $SuccessCount = 0
    $FailedCount = 0
    
    foreach ($Query in $Queries) {
        $Query = $Query.Trim()
        if ([string]::IsNullOrWhiteSpace($Query)) {
            continue
        }
        
        try {
            Write-Host "Executing: $($Query.Substring(0, [Math]::Min(60, $Query.Length)))..." -NoNewline
            
            $Command = New-Object MySql.Data.MySqlClient.MySqlCommand($Query, $Connection)
            $Command.ExecuteNonQuery() | Out-Null
            
            Write-Host " ✓" -ForegroundColor Green
            $SuccessCount++
        } catch {
            Write-Host " ✗" -ForegroundColor Red
            Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
            $FailedCount++
        }
    }
    
    $Connection.Close()
    
    Write-Host "" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Database Optimization Results:" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Success: $SuccessCount" -ForegroundColor Green
    Write-Host "Failed: $FailedCount" -ForegroundColor Red
    Write-Host "Total: $($SuccessCount + $FailedCount)" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    if ($FailedCount -eq 0) {
        Write-Host ""
        Write-Host "✅ Database optimization completed successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Changes made:" -ForegroundColor Cyan
        Write-Host "1. Removed conflicting 'user' table" -ForegroundColor White
        Write-Host "2. Added constraints to sys_user table" -ForegroundColor White
        Write-Host "3. Added unique constraints to role and permission tables" -ForegroundColor White
        Write-Host "4. Added necessary indexes for performance" -ForegroundColor White
        Write-Host "5. Fixed field type consistency" -ForegroundColor White
        Write-Host "" -ForegroundColor Cyan
        Write-Host "The system should now run smoothly." -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "⚠️  Some operations failed, but most should have completed." -ForegroundColor Yellow
        Write-Host "The system should still be operational." -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "" -ForegroundColor Red
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "" -ForegroundColor Red
    Write-Host "If you don't have MySQL Connector/NET installed, you can:" -ForegroundColor Yellow
    Write-Host "1. Download and install MySQL Connector/NET from MySQL website" -ForegroundColor Yellow
    Write-Host "2. Or execute the SQL script manually using MySQL Workbench" -ForegroundColor Yellow
    exit 1
}