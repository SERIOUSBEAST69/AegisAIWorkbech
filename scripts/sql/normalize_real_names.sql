-- Normalize real_name for company 1 users.
-- Only updates rows where real_name is empty or equal to username.

UPDATE sys_user
SET real_name = CASE LOWER(username)
  WHEN 'admin' THEN 'Zhang Zheng'
  WHEN 'admin_reviewer' THEN 'Li Shenyan'
  WHEN 'admin_ops' THEN 'Wang Yunwei'
  WHEN 'executive' THEN 'Chen Mingyuan'
  WHEN 'executive_2' THEN 'Zhao Jingxing'
  WHEN 'executive_3' THEN 'Sun Zhiheng'
  WHEN 'secops' THEN 'Zhou Rui'
  WHEN 'secops_2' THEN 'Wu Kai'
  WHEN 'secops_3' THEN 'Zheng Hang'
  WHEN 'dataadmin' THEN 'Qian Siyuan'
  WHEN 'dataadmin_2' THEN 'Feng Jiahe'
  WHEN 'dataadmin_3' THEN 'Xie Yining'
  WHEN 'aibuilder' THEN 'Han Qiming'
  WHEN 'aibuilder_2' THEN 'Lin Yanbo'
  WHEN 'aibuilder_3' THEN 'Jiang Ruofan'
  WHEN 'bizowner' THEN 'Xu Chengye'
  WHEN 'bizowner_2' THEN 'Deng Wenxuan'
  WHEN 'bizowner_3' THEN 'Cao Yuanhang'
  WHEN 'employee1' THEN 'Guo Yifan'
  WHEN 'employee2' THEN 'Peng Zixuan'
  WHEN 'employee3' THEN 'Liang Kexin'
  ELSE CASE MOD(id, 12)
    WHEN 0 THEN 'Liu Yang'
    WHEN 1 THEN 'Wang Lei'
    WHEN 2 THEN 'Li Na'
    WHEN 3 THEN 'Zhao Min'
    WHEN 4 THEN 'Chen Tao'
    WHEN 5 THEN 'Yang Xue'
    WHEN 6 THEN 'Zhou Lin'
    WHEN 7 THEN 'Wu Di'
    WHEN 8 THEN 'Zheng Kai'
    WHEN 9 THEN 'Sun Qian'
    WHEN 10 THEN 'Feng Chen'
    ELSE 'Xie Ning'
  END
END,
nickname = CASE
  WHEN nickname IS NULL OR TRIM(nickname) = '' OR LOWER(TRIM(nickname)) = LOWER(TRIM(username)) THEN real_name
  ELSE nickname
END,
update_time = CURRENT_TIMESTAMP
WHERE company_id = 1
  AND (
    real_name IS NULL
    OR TRIM(real_name) = ''
    OR LOWER(TRIM(real_name)) = LOWER(TRIM(username))
  );
