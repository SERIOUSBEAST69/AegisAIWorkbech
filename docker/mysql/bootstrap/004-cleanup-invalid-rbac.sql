DELIMITER $$

DROP PROCEDURE IF EXISTS cleanup_invalid_rbac $$
CREATE PROCEDURE cleanup_invalid_rbac()
BEGIN
  DECLARE has_permission INT DEFAULT 0;
  DECLARE has_role_permission INT DEFAULT 0;
  DECLARE has_role INT DEFAULT 0;
  DECLARE has_sys_user INT DEFAULT 0;
  DECLARE has_governance_change_request INT DEFAULT 0;
  DECLARE has_permission_parent_id INT DEFAULT 0;

  SELECT COUNT(1) INTO has_permission
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'permission';

  SELECT COUNT(1) INTO has_role_permission
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'role_permission';

  SELECT COUNT(1) INTO has_role
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'role';

  SELECT COUNT(1) INTO has_sys_user
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'sys_user';

  SELECT COUNT(1) INTO has_governance_change_request
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'governance_change_request';

  IF has_permission = 1 THEN
    SELECT COUNT(1) INTO has_permission_parent_id
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'permission' AND column_name = 'parent_id';
  END IF;

  IF has_permission = 1 AND has_role_permission = 1 THEN
    DELETE rp
    FROM role_permission rp
    JOIN permission p ON p.id = rp.permission_id
    WHERE p.code IN ('ASSET_VIEW', 'ASSET_EDIT', 'DENY_SECOPS')
       OR LOWER(COALESCE(p.name, '')) IN ('查看资产', '编辑资产', 'deny_secops_submit');
  END IF;

  IF has_permission = 1 THEN
    DELETE FROM permission
    WHERE code IN ('ASSET_VIEW', 'ASSET_EDIT', 'DENY_SECOPS')
       OR LOWER(COALESCE(name, '')) IN ('查看资产', '编辑资产', 'deny_secops_submit');

    IF has_permission_parent_id = 1 THEN
      UPDATE permission p
      SET p.parent_id = NULL
      WHERE p.parent_id IS NOT NULL
        AND NOT EXISTS (
          SELECT 1
          FROM (
            SELECT id FROM permission
          ) x
          WHERE x.id = p.parent_id
        );
    END IF;
  END IF;

  IF has_role = 1 THEN
    IF has_sys_user = 1 THEN
      UPDATE sys_user
      SET role_id = NULL
      WHERE role_id IN (
        SELECT id
        FROM (
          SELECT r.id
          FROM `role` r
          WHERE r.id = 1775369855
             OR UPPER(COALESCE(r.code, '')) = 'DENY_SECOPS'
             OR UPPER(COALESCE(r.code, '')) LIKE 'SMOKE_ROLE_%'
             OR LOWER(COALESCE(r.name, '')) LIKE 'smoke role%'
        ) t
      );
    END IF;

    IF has_role_permission = 1 THEN
      DELETE FROM role_permission
      WHERE role_id IN (
        SELECT id
        FROM (
          SELECT r.id
          FROM `role` r
          WHERE r.id = 1775369855
             OR UPPER(COALESCE(r.code, '')) = 'DENY_SECOPS'
             OR UPPER(COALESCE(r.code, '')) LIKE 'SMOKE_ROLE_%'
             OR LOWER(COALESCE(r.name, '')) LIKE 'smoke role%'
        ) t
      );
    END IF;

    DELETE FROM `role`
    WHERE id = 1775369855
       OR UPPER(COALESCE(code, '')) = 'DENY_SECOPS'
       OR UPPER(COALESCE(code, '')) LIKE 'SMOKE_ROLE_%'
       OR LOWER(COALESCE(name, '')) LIKE 'smoke role%';
  END IF;

  IF has_governance_change_request = 1 THEN
    DELETE FROM governance_change_request
    WHERE LOWER(COALESCE(payload_json, '')) LIKE '%deny_secops_submit%'
       OR LOWER(COALESCE(payload_json, '')) LIKE '%smoke role%'
       OR UPPER(COALESCE(payload_json, '')) LIKE '%SMOKE_ROLE_%';
  END IF;
END $$

CALL cleanup_invalid_rbac() $$
DROP PROCEDURE IF EXISTS cleanup_invalid_rbac $$

DELIMITER ;