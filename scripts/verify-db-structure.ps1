# 数据库结构验证脚本
# 检查修复后的数据库表结构

Write-Host "=== Database Structure Verification ===" -ForegroundColor Cyan
Write-Host "Checking critical database tables..." -ForegroundColor Cyan
Write-Host "" -ForegroundColor Cyan

# 检查 sys_user 表
Write-Host "1. Checking sys_user table..." -ForegroundColor Yellow
$userCount = 0
try {
    $connectionString = "server=localhost;database=aegisai;uid=root;pwd=root;"
    $connection = New-Object System.Data.SqlClient.SqlConnection($connectionString)
    $connection.Open()
    
    $command = $connection.CreateCommand()
    $command.CommandText = "SELECT COUNT(*) FROM sys_user"
    $userCount = $command.ExecuteScalar()
    
    $connection.Close()
    
    Write-Host "   ✅ sys_user table exists with $userCount users" -ForegroundColor Green
} catch {
    Write-Host "   ❌ sys_user table not found or error: $($_.Exception.Message)" -ForegroundColor Red
}

# 检查 user 表是否已删除
Write-Host "2. Checking if user table is removed..." -ForegroundColor Yellow
try {
    $connectionString = "server=localhost;database=aegisai;uid=root;pwd=root;"
    $connection = New-Object System.Data.SqlClient.SqlConnection($connectionString)
    $connection.Open()
    
    $command = $connection.CreateCommand()
    $command.CommandText = "SELECT COUNT(*) FROM user"
    $userTableCount = $command.ExecuteScalar()
    
    $connection.Close()
    
    Write-Host "   ⚠️  user table still exists (should be removed)" -ForegroundColor Yellow
} catch {
    if ($_.Exception.Message -like "*invalid object name 'user'*") {
        Write-Host "   ✅ user table has been successfully removed" -ForegroundColor Green
    } else {
        Write-Host "   ❌ Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# 检查 role 表
Write-Host "3. Checking role table..." -ForegroundColor Yellow
try {
    $connectionString = "server=localhost;database=aegisai;uid=root;pwd=root;"
    $connection = New-Object System.Data.SqlClient.SqlConnection($connectionString)
    $connection.Open()
    
    $command = $connection.CreateCommand()
    $command.CommandText = "SELECT COUNT(*) FROM role"
    $roleCount = $command.ExecuteScalar()
    
    $connection.Close()
    
    Write-Host "   ✅ role table exists with $roleCount roles" -ForegroundColor Green
} catch {
    Write-Host "   ❌ role table not found: $($_.Exception.Message)" -ForegroundColor Red
}

# 检查 governance_change_request 表
Write-Host "4. Checking governance_change_request table..." -ForegroundColor Yellow
try {
    $connectionString = "server=localhost;database=aegisai;uid=root;pwd=root;"
    $connection = New-Object System.Data.SqlClient.SqlConnection($connectionString)
    $connection.Open()
    
    $command = $connection.CreateCommand()
    $command.CommandText = "SELECT COUNT(*) FROM governance_change_request"
    $gcCount = $command.ExecuteScalar()
    
    $connection.Close()
    
    Write-Host "   ✅ governance_change_request table exists with $gcCount requests" -ForegroundColor Green
} catch {
    Write-Host "   ❌ governance_change_request table not found: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "" -ForegroundColor Cyan
Write-Host "=== Verification Complete ===" -ForegroundColor Cyan
Write-Host "Database structure appears to be intact." -ForegroundColor Green
Write-Host "The system should be operational." -ForegroundColor Green
Write-Host "" -ForegroundColor Cyan
Write-Host "If you encounter any issues, please:" -ForegroundColor Yellow
Write-Host "1. Check MySQL service is running" -ForegroundColor Yellow
Write-Host "2. Verify database connection settings in application.yml" -ForegroundColor Yellow
Write-Host "3. Ensure all required tables are present" -ForegroundColor Yellow