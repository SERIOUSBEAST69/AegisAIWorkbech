import { createRouter, createWebHistory } from 'vue-router';

import Home from '../views/Home.vue';
import DataAsset from '../views/DataAsset.vue';
import AuditCenter from '../views/AuditCenter.vue';
import UserManage from '../views/UserManage.vue';
import RoleManage from '../views/RoleManage.vue';
import PermissionManage from '../views/PermissionManage.vue';
import SecurityCommand from '../views/SecurityCommand.vue';
import RiskEventManage from '../views/RiskEventManage.vue';
import OpsObservability from '../views/OpsObservability.vue';
import ApprovalCenterHub from '../views/ApprovalCenterHub.vue';
import PolicyManage from '../views/PolicyManage.vue';
import SubjectRequest from '../views/SubjectRequest.vue';
import Login from '../views/Login.vue';
import Profile from '../views/Profile.vue';
import Settings from '../views/Settings.vue';
import ShadowAiDiscovery from '../views/ShadowAiDiscovery.vue';
import EmployeeAiBehaviorMonitor from '../views/EmployeeAiBehaviorMonitor.vue';
import ThreatMonitor from '../views/ThreatMonitor.vue';
import { getSession, hasActiveSession } from '../utils/auth';
import { canAccessPath } from '../utils/persona';
import { isEmployeeUser } from '../utils/employeePolicy';
import { hasPermissionByUser } from '../utils/permission';

function isPlatformAdmin(user) {
  const roleCode = String(user?.roleCode || '').trim().toUpperCase();
  return roleCode === 'ADMIN';
}

function isAdminReviewerReadOnlyPath(user, path) {
  const roleCode = String(user?.roleCode || '').trim().toUpperCase();
  if (roleCode !== 'ADMIN_REVIEWER') {
    return false;
  }
  return path === '/user-manage' || path === '/role-manage' || path === '/permission-manage';
}

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { public: true, depth: 0 } },
  { path: '/', name: 'Home', component: Home, meta: { depth: 1 } },
  { path: '/data-asset', name: 'DataAsset', component: DataAsset, meta: { depth: 2 } },
  { path: '/audit-center', name: 'AuditCenter', component: AuditCenter, meta: { depth: 2, permission: 'audit:log:view' } },
  { path: '/audit-log', redirect: to => ({ path: '/audit-center', query: to.query }) },
  { path: '/audit-report', redirect: to => ({ path: '/audit-center', query: to.query }) },
  { path: '/user-manage', name: 'UserManage', component: UserManage, meta: { depth: 3, permission: 'user:manage' } },
  { path: '/role-manage', name: 'RoleManage', component: RoleManage, meta: { depth: 3, permission: 'role:manage' } },
  { path: '/permission-manage', name: 'PermissionManage', component: PermissionManage, meta: { depth: 3, permission: 'permission:manage' } },
  { path: '/operations-command', name: 'SecurityCommand', component: SecurityCommand, meta: { depth: 3 } },
  { path: '/ops-observability', name: 'OpsObservability', component: OpsObservability, meta: { depth: 3 } },
  { path: '/approval-center', name: 'ApprovalCenter', component: ApprovalCenterHub, meta: { depth: 3 } },
  { path: '/approval-manage', redirect: to => ({ path: '/approval-center', query: to.query }) },
  { path: '/policy-manage', name: 'PolicyManage', component: PolicyManage, meta: { depth: 3 } },
  { path: '/risk-event-manage', name: 'RiskEventManage', component: RiskEventManage, meta: { depth: 3 } },
  { path: '/sensitive-data-governance', redirect: to => ({ path: '/data-asset', query: { ...to.query, tab: 'sensitive' } }) },
  { path: '/sensitive-scan', redirect: to => ({ path: '/data-asset', query: { ...to.query, tab: 'sensitive' } }) },
  { path: '/desense-preview', redirect: to => ({ path: '/data-asset', query: { ...to.query, tab: 'sensitive' } }) },
  { path: '/subject-request', name: 'SubjectRequest', component: SubjectRequest, meta: { depth: 2 } },
  { path: '/ai/risk-rating', redirect: to => ({ path: '/shadow-ai', query: { ...to.query, tab: 'risk' } }) },
  { path: '/ai/anomaly', name: 'AnomalyDetection', component: EmployeeAiBehaviorMonitor, meta: { depth: 3 } },
  { path: '/shadow-ai', name: 'ShadowAiDiscovery', component: ShadowAiDiscovery, meta: { depth: 2 } },
  { path: '/threat-monitor', name: 'ThreatMonitor', component: ThreatMonitor, meta: { depth: 2 } },
  { path: '/profile', name: 'Profile', component: Profile, meta: { depth: 2 } },
  { path: '/settings', name: 'Settings', component: Settings, meta: { depth: 2 } }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 简单登录守卫：无 token 则跳转登录
router.beforeEach((to, from, next) => {
  if (to.meta.public) return next();
  if (!hasActiveSession()) {
    return next({ path: '/login', query: { redirect: to.fullPath } });
  }

  const session = getSession();
  const isPermissionLogJump = to.path === '/audit-log'
    && from.path === '/permission-manage'
    && hasPermissionByUser(session?.user, 'permission:manage');
  if (isPermissionLogJump) {
    return next();
  }
  if (to.meta?.permission
    && !hasPermissionByUser(session?.user, to.meta.permission)
    && !isPlatformAdmin(session?.user)
    && !isAdminReviewerReadOnlyPath(session?.user, to.path)
    && !isPermissionLogJump) {
    return next('/');
  }
  if (!canAccessPath(to.path, session?.user)) {
    return next(isEmployeeUser(session?.user) ? '/shadow-ai' : '/');
  }

  // 根据路由深度自动设置转场方向（供 usePageTransition 读取）
  // 深度相同视为同级，深度更大为"前进"，反之为"后退"
  const fromDepth = from.meta?.depth ?? 1;
  const toDepth   = to.meta?.depth   ?? 1;
  to.meta._transitionDir = toDepth >= fromDepth ? 'forward' : 'back';

  next();
});

export default router;
