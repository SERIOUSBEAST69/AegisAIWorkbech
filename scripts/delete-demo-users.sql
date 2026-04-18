-- 删除部门是demo的，未分配部门的账号

-- 1. 先检查哪些用户需要删除
SELECT id, username, real_name, nickname, company_id, department 
FROM sys_user 
WHERE department = 'demo' OR department IS NULL OR department = '';

-- 2. 开始事务
START TRANSACTION;

-- 3. 删除部门是demo的用户
DELETE FROM sys_user 
WHERE department = 'demo' OR department IS NULL OR department = '';

-- 4. 提交事务
COMMIT;

-- 5. 验证删除结果
SELECT COUNT(*) as remaining_users 
FROM sys_user;

SELECT COUNT(*) as demo_department_users 
FROM sys_user 
WHERE department = 'demo' OR department IS NULL OR department = '';