import { getSession } from './auth';

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
  const codes = normalizePermissions(user?.permissionCodes);
  return codes.has(required);
}
