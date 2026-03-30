const ROLE = {
  ADMIN: 'ADMIN',
  SECOPS: 'SECOPS',
  EXECUTIVE: 'EXECUTIVE',
  DATA_ADMIN: 'DATA_ADMIN',
  BUSINESS_OWNER: 'BUSINESS_OWNER',
  EMPLOYEE: 'EMPLOYEE',
};

export function normalizeRoleCode(user) {
  return String(user?.roleCode || '').trim().toUpperCase();
}

export function hasRole(user, roleCode) {
  return normalizeRoleCode(user) === String(roleCode || '').trim().toUpperCase();
}

export function hasAnyRole(user, roleCodes = []) {
  const currentRole = normalizeRoleCode(user);
  return roleCodes.some(code => currentRole === String(code || '').trim().toUpperCase());
}

export function isGovernanceAdmin(user) {
  return hasRole(user, ROLE.ADMIN);
}

export function isSecOps(user) {
  return hasRole(user, ROLE.SECOPS);
}

export function isExecutive(user) {
  return hasRole(user, ROLE.EXECUTIVE);
}

export function canSubmitGovernanceChange(user) {
  return isGovernanceAdmin(user);
}

export function canReviewGovernanceChange(user) {
  return isSecOps(user);
}

export function canManageSodRule(user) {
  return isGovernanceAdmin(user);
}

export function canManagePolicyStructure(user) {
  return isGovernanceAdmin(user);
}

export function canTogglePolicyStatus(user) {
  return isSecOps(user);
}

export function canUsePrivacyOps(user) {
  return isSecOps(user);
}

export function canAccessThreatMonitor(user) {
  return isSecOps(user);
}

export function canHandleThreatEvent(user) {
  return isSecOps(user);
}

export function canManageRiskEvent(user) {
  return isSecOps(user);
}

export function canApproveApprovalFlow(user) {
  return hasAnyRole(user, [ROLE.ADMIN, ROLE.DATA_ADMIN, ROLE.BUSINESS_OWNER]);
}

export function canApproveShareFlow(user) {
  return hasAnyRole(user, [ROLE.ADMIN, ROLE.DATA_ADMIN]);
}

export function canRunAdversarialSimulation(user) {
  return isGovernanceAdmin(user);
}
