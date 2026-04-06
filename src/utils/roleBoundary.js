import { hasPermissionByUser } from './permission';

const ROLE = {
  ADMIN: 'ADMIN',
  EXECUTIVE: 'EXECUTIVE',
  SECOPS: 'SECOPS',
  DATA_ADMIN: 'DATA_ADMIN',
  AI_BUILDER: 'AI_BUILDER',
  BUSINESS_OWNER: 'BUSINESS_OWNER',
  EMPLOYEE: 'EMPLOYEE',
};

const ROLE_FAMILY = {
  ADMIN: ['ADMIN_REVIEWER', 'ADMIN_OPS'],
  EXECUTIVE: ['EXECUTIVE_COMPLIANCE'],
  SECOPS: ['SECOPS_TRIAGE', 'SECOPS_RESPONDER'],
  DATA_ADMIN: ['DATA_ADMIN_MAINTAINER'],
  AI_BUILDER: [],
  BUSINESS_OWNER: ['BUSINESS_OWNER_APPROVER'],
  EMPLOYEE: ['EMPLOYEE_REQUESTER'],
};

function hasAnyPermissionByUser(user, codes = []) {
  return codes.some(code => hasPermissionByUser(user, code));
}

export function normalizeRoleCode(user) {
  return String(user?.roleCode || '').trim().toUpperCase();
}

export function hasRole(user, roleCode) {
  const current = normalizeRoleCode(user);
  const expected = String(roleCode || '').trim().toUpperCase();
  if (!current || !expected) return false;
  if (current === expected) return true;
  const children = ROLE_FAMILY[expected] || [];
  return children.includes(current);
}

export function hasAnyRole(user, roleCodes = []) {
  return roleCodes.some(code => hasRole(user, code));
}

export function isGovernanceAdmin(user) {
  return hasRole(user, ROLE.ADMIN);
}

export function isGovernanceOperatorAccount(user) {
  return hasRole(user, ROLE.ADMIN) || hasPermissionByUser(user, 'govern:change:create');
}

export function isGovernanceReviewerAccount(user) {
  return hasRole(user, ROLE.ADMIN) || hasAnyPermissionByUser(user, ['govern:change:review', 'approval:operate', 'approval:operate:governance']);
}

export function isGovernanceOpsAccount(user) {
  return hasRole(user, ROLE.ADMIN) || hasPermissionByUser(user, 'user:manage');
}

export function isSecOps(user) {
  return hasRole(user, ROLE.SECOPS);
}

export function normalizeUsername(user) {
  return String(user?.username || '').trim().toLowerCase();
}

export function isSecOpsCommander(user) {
  return hasRole(user, ROLE.SECOPS);
}

export function isSecOpsTriage(user) {
  return hasRole(user, ROLE.SECOPS);
}

export function isSecOpsResponder(user) {
  return hasRole(user, ROLE.SECOPS);
}

export function isExecutive(user) {
  return hasRole(user, ROLE.EXECUTIVE);
}

export function canSubmitGovernanceChange(user) {
  return isGovernanceOperatorAccount(user);
}

export function canReviewGovernanceChange(user) {
  return isGovernanceReviewerAccount(user);
}

export function canManageSodRule(user) {
  return isGovernanceOperatorAccount(user);
}

export function canManagePolicyStructure(user) {
  return isGovernanceOperatorAccount(user);
}

export function canTogglePolicyStatus(user) {
  return isGovernanceOperatorAccount(user);
}

export function canUsePrivacyOps(user) {
  return hasAnyRole(user, [ROLE.ADMIN, ROLE.SECOPS]);
}

export function canManagePrivacyConfig(user) {
  return isGovernanceAdmin(user);
}

export function canAccessThreatMonitor(user) {
  return hasAnyRole(user, [ROLE.ADMIN, ROLE.SECOPS]);
}

export function canHandleThreatEvent(user) {
  return isSecOps(user);
}

export function canManageThreatRule(user) {
  return isGovernanceAdmin(user) || isSecOpsCommander(user);
}

export function canBlockThreatEvent(user) {
  return isSecOpsCommander(user) || isSecOpsResponder(user);
}

export function canIgnoreThreatEvent(user) {
  return isSecOpsCommander(user) || isSecOpsTriage(user);
}

export function canManageRiskEvent(user) {
  return isSecOps(user);
}

export function canApproveApprovalFlow(user) {
  return isGovernanceAdmin(user) || hasAnyPermissionByUser(user, [
    'approval:operate',
    'approval:operate:data',
    'approval:operate:governance',
    'approval:operate:business',
  ]);
}

export function canRejectApprovalFlow(user) {
  return canApproveApprovalFlow(user);
}

export function canApproveShareFlow(user) {
  return hasAnyRole(user, [ROLE.ADMIN, ROLE.DATA_ADMIN]);
}

export function canRunAdversarialSimulation(user) {
  return isGovernanceAdmin(user);
}

export function canRunAnomalyCheck(user) {
  return true;
}

export function canViewAnomalyEvents(user) {
  return true;
}

export function isEmployee(user) {
  return hasRole(user, ROLE.EMPLOYEE);
}

export function isEmployeeRequesterFull(user) {
  return hasRole(user, ROLE.EMPLOYEE);
}

export function isEmployeeRequesterLimited(user) {
  return false;
}

export function isEmployeeObserver(user) {
  return false;
}

export function canCreateSubjectRequest(user) {
  if (hasAnyRole(user, [ROLE.ADMIN])) {
    return true;
  }
  return isEmployeeRequesterFull(user) || isEmployeeRequesterLimited(user);
}

export function canCreateSubjectRequestType(user, requestType) {
  if (hasAnyRole(user, [ROLE.ADMIN])) {
    return true;
  }
  if (isEmployeeRequesterFull(user)) {
    return true;
  }
  if (isEmployeeRequesterLimited(user)) {
    return String(requestType || '').trim().toLowerCase() !== 'delete';
  }
  return false;
}

export function canProcessSubjectRequest(user) {
  return hasAnyRole(user, [ROLE.ADMIN, ROLE.SECOPS]);
}

export function canDeleteSubjectRequest(user) {
  return false;
}
