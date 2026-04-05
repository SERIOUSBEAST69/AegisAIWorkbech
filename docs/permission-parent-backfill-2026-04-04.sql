-- Permission parent_id backfill for existing data.
-- Safe to run multiple times: only updates rows with NULL/incorrect parent_id.

START TRANSACTION;

-- data_asset children
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'menu:data_asset'
SET child.parent_id = parent.id
WHERE child.code IN ('data_asset:upload', 'data_asset:delete')
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

-- user/role/permission management children
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'menu:user_manage'
SET child.parent_id = parent.id
WHERE child.code = 'user:manage'
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'menu:role_manage'
SET child.parent_id = parent.id
WHERE child.code = 'role:manage'
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'menu:permission_manage'
SET child.parent_id = parent.id
WHERE child.code IN ('permission:manage', 'permission:matrix:view')
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

-- SoD
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'sod:rule:view'
SET child.parent_id = parent.id
WHERE child.code = 'sod:rule:edit'
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

-- governance change
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'govern:change:view'
SET child.parent_id = parent.id
WHERE child.code IN ('govern:change:create', 'govern:change:review')
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

-- approval
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'approval:view'
SET child.parent_id = parent.id
WHERE child.code IN ('approval:operate', 'approval:operate:data', 'approval:operate:governance', 'approval:operate:business')
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

-- risk/security
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'risk:event:view'
SET child.parent_id = parent.id
WHERE child.code = 'risk:event:handle'
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'security:event:view'
SET child.parent_id = parent.id
WHERE child.code IN ('security:event:handle', 'security:rule:manage')
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

-- policy
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'policy:view'
SET child.parent_id = parent.id
WHERE child.code IN ('policy:structure:manage', 'policy:status:toggle')
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

-- audit report
UPDATE permission child
JOIN permission parent
  ON parent.company_id = child.company_id
 AND parent.code = 'audit:report:view'
SET child.parent_id = parent.id
WHERE child.code = 'audit:report:generate'
  AND (child.parent_id IS NULL OR child.parent_id <> parent.id);

COMMIT;

-- Optional check:
-- SELECT company_id, code, parent_id FROM permission ORDER BY company_id, id;
