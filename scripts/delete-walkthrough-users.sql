-- 删除walkthrough相关用户
START TRANSACTION;

-- 检查walkthrough用户
SELECT id, username, real_name, nickname, company_id 
FROM sys_user 
WHERE company_id = 1 
  AND (username LIKE 'walkthrough_%' OR real_name LIKE '%Walkthrough%' OR real_name LIKE '%walkthrough%');

-- 删除walkthrough用户
DELETE FROM sys_user 
WHERE company_id = 1 
  AND (username LIKE 'walkthrough_%' OR real_name LIKE '%Walkthrough%' OR real_name LIKE '%walkthrough%');

-- 验证删除结果
SELECT COUNT(*) as deleted_count 
FROM sys_user 
WHERE company_id = 1 
  AND (username LIKE 'walkthrough_%' OR real_name LIKE '%Walkthrough%' OR real_name LIKE '%walkthrough%');

COMMIT;