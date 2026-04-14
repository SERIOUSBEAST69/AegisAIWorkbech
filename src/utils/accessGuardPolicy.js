import { hasPermissionByUser } from './permission';

export function isPlatformAdmin(user) {
  const roleCode = String(user?.roleCode || '').trim().toUpperCase();
  return roleCode === 'ADMIN';
}

export function isAdminReviewerReadOnlyPath(user, path) {
  const roleCode = String(user?.roleCode || '').trim().toUpperCase();
  if (roleCode !== 'ADMIN_REVIEWER') {
    return false;
  }
  return path === '/user-manage' || path === '/role-manage' || path === '/permission-manage';
}

export function isPermissionLogJump({ toPath, fromPath, user }) {
  return toPath === '/audit-log'
    && fromPath === '/permission-manage'
    && hasPermissionByUser(user, 'permission:manage');
}

export function shouldBlockByMetaPermission({ toMetaPermission, toPath, user }) {
  if (!toMetaPermission) {
    return false;
  }
  if (hasPermissionByUser(user, toMetaPermission)) {
    return false;
  }
  if (isPlatformAdmin(user)) {
    return false;
  }
  if (isAdminReviewerReadOnlyPath(user, toPath)) {
    return false;
  }
  return true;
}
