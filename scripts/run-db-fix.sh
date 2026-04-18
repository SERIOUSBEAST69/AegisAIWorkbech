#!/bin/bash

# 数据库修复脚本
# 执行数据库表结构优化

# 配置
DB_HOST="localhost"
DB_PORT="3306"
DB_NAME="aegisai"
DB_USER="root"
DB_PASS="root"

# SQL 脚本路径
SQL_FILE="scripts/fix-database-issues.sql"

# 检查 SQL 文件是否存在
if [ ! -f "$SQL_FILE" ]; then
    echo "Error: SQL file $SQL_FILE not found!"
    exit 1
fi

# 执行 SQL 脚本
echo "Executing database optimization..."
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo ""

# 使用 mysql 命令执行 SQL 脚本
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Database optimization completed successfully!"
    echo ""
    echo "Changes made:"
    echo "1. Removed conflicting 'user' table"
    echo "2. Added constraints to sys_user table"
    echo "3. Added unique constraints to role and permission tables"
    echo "4. Added necessary indexes for performance"
    echo "5. Fixed field type consistency"
    echo ""
    echo "The system should now run smoothly."
else
    echo ""
    echo "❌ Database optimization failed!"
    echo "Please check the error message above."
    exit 1
fi