import { getSession } from './auth';

const ROLE_DEFAULT_PERMISSIONS = {
  ADMIN: ['audit:log:view', 'audit:export', 'audit:report:view', 'audit:report:generate'],
  ADMIN_REVIEWER: ['audit:log:view', 'audit:report:view'],
  SECOPS: ['audit:log:view', 'audit:export', 'audit:report:view'],
  BUSINESS_OWNER: ['audit:log:view'],
  AUDIT: ['audit:log:view', 'audit:report:view'],
};

const ROLE_PARENT_ALIAS = {
  ADMIN_REVIEWER: 'ADMIN',
  ADMIN_OPS: 'ADMIN',
  SECOPS_RESPONDER: 'SECOPS',
  BUSINESS_OWNER_APPROVER: 'BUSINESS_OWNER',
};

function normalizePermissions(permissionCodes) {
  if (!Array.isArray(permissionCodes)) {
    return new Set();
  }
  return new Set(
    permissionCodes
      .map(item => String(item || '').trim().toLowerCase())
      .filter(Boolean)
  );
}

export function hasPermission(permissionCode) {
  if (!permissionCode) {
    return true;
  }
  const session = getSession();
  return hasPermissionByUser(session?.user, permissionCode);
}

export function hasPermissionByUser(user, permissionCode) {
  if (!permissionCode) {
    return true;
  }
  const required = String(permissionCode).trim().toLowerCase();
  if (!required) {
    return true;
  }
  const roleCode = String(user?.roleCode || '').trim().toUpperCase();
  const inheritedRoleCode = ROLE_PARENT_ALIAS[roleCode];
  const roleDefaults = [
    ...(ROLE_DEFAULT_PERMISSIONS[roleCode] || []),
    ...(inheritedRoleCode ? (ROLE_DEFAULT_PERMISSIONS[inheritedRoleCode] || []) : []),
  ];
  const codes = normalizePermissions([...(user?.permissionCodes || []), ...roleDefaults]);
  return codes.has(required);
}
