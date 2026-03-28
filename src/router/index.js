import { createRouter, createWebHistory } from 'vue-router';

import Home from '../views/Home.vue';
import DataAsset from '../views/DataAsset.vue';
import AuditLog from '../views/AuditLog.vue';
import AuditReport from '../views/AuditReport.vue';
import UserManage from '../views/UserManage.vue';
import RoleManage from '../views/RoleManage.vue';
import PermissionManage from '../views/PermissionManage.vue';
import OperationsCommand from '../views/OperationsCommand.vue';
import PolicyManage from '../views/PolicyManage.vue';
import SensitiveScan from '../views/SensitiveScan.vue';
import SubjectRequest from '../views/SubjectRequest.vue';
import DesensePreview from '../views/DesensePreview.vue';
import Login from '../views/Login.vue';
import Profile from '../views/Profile.vue';
import Settings from '../views/Settings.vue';
import ShadowAiDiscovery from '../views/ShadowAiDiscovery.vue';
import AiRiskRating from '../views/AiRiskRating.vue';
import EmployeeAiBehaviorMonitor from '../views/EmployeeAiBehaviorMonitor.vue';
import ThreatMonitor from '../views/ThreatMonitor.vue';
import { getSession, hasActiveSession } from '../utils/auth';
import { canAccessPath } from '../utils/persona';
import { isEmployeeUser } from '../utils/employeePolicy';

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { public: true, depth: 0 } },
  { path: '/', name: 'Home', component: Home, meta: { depth: 1 } },
  { path: '/data-asset', name: 'DataAsset', component: DataAsset, meta: { depth: 2 } },
  { path: '/audit-log', name: 'AuditLog', component: AuditLog, meta: { depth: 2 } },
  { path: '/audit-report', name: 'AuditReport', component: AuditReport, meta: { depth: 2 } },
  { path: '/user-manage', name: 'UserManage', component: UserManage, meta: { depth: 3 } },
  { path: '/role-manage', name: 'RoleManage', component: RoleManage, meta: { depth: 3 } },
  { path: '/permission-manage', name: 'PermissionManage', component: PermissionManage, meta: { depth: 3 } },
  { path: '/operations-command', name: 'OperationsCommand', component: OperationsCommand, meta: { depth: 3 } },
  { path: '/approval-manage', name: 'ApprovalManage', component: OperationsCommand, meta: { depth: 3, lane: 'approval' } },
  { path: '/policy-manage', name: 'PolicyManage', component: PolicyManage, meta: { depth: 3 } },
  { path: '/risk-event-manage', name: 'RiskEventManage', component: OperationsCommand, meta: { depth: 3, lane: 'risk' } },
  { path: '/sensitive-scan', name: 'SensitiveScan', component: SensitiveScan, meta: { depth: 2 } },
  { path: '/subject-request', name: 'SubjectRequest', component: SubjectRequest, meta: { depth: 2 } },
  { path: '/desense-preview', name: 'DesensePreview', component: DesensePreview, meta: { depth: 2 } },
  { path: '/ai/risk-rating', name: 'AiRiskRating', component: AiRiskRating, meta: { depth: 3 } },
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
  if (!canAccessPath(to.path, session?.user)) {
    return next(isEmployeeUser(session?.user) ? '/ai/anomaly' : '/');
  }

  // 根据路由深度自动设置转场方向（供 usePageTransition 读取）
  // 深度相同视为同级，深度更大为"前进"，反之为"后退"
  const fromDepth = from.meta?.depth ?? 1;
  const toDepth   = to.meta?.depth   ?? 1;
  to.meta._transitionDir = toDepth >= fromDepth ? 'forward' : 'back';

  next();
});

export default router;
