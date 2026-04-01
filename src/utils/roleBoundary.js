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

export function normalizeUsername(user) {
  return String(user?.username || '').trim().toLowerCase();
}

export function isSecOpsCommander(user) {
  return isSecOps(user) && normalizeUsername(user) === 'secops';
}

export function isSecOpsTriage(user) {
  return isSecOps(user) && normalizeUsername(user) === 'secops_2';
}

export function isSecOpsResponder(user) {
  return isSecOps(user) && normalizeUsername(user) === 'secops_3';
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
  return isGovernanceAdmin(user);
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
  if (isGovernanceAdmin(user)) {
    return true;
  }
  const username = normalizeUsername(user);
  if (hasRole(user, ROLE.DATA_ADMIN)) {
    return username !== 'dataadmin_2';
  }
  if (hasRole(user, ROLE.BUSINESS_OWNER)) {
    return username !== 'bizowner_3';
  }
  return false;
}

export function canRejectApprovalFlow(user) {
  if (isGovernanceAdmin(user)) {
    return true;
  }
  const username = normalizeUsername(user);
  if (hasRole(user, ROLE.DATA_ADMIN)) {
    return username !== 'dataadmin_2';
  }
  if (hasRole(user, ROLE.BUSINESS_OWNER)) {
    return username !== 'bizowner_2';
  }
  return false;
}

export function canApproveShareFlow(user) {
  return hasAnyRole(user, [ROLE.ADMIN, ROLE.DATA_ADMIN]);
}

export function canRunAdversarialSimulation(user) {
  return isGovernanceAdmin(user);
}

export function canRunAnomalyCheck(user) {
  if (!hasRole(user, 'AI_BUILDER')) {
    return true;
  }
  return normalizeUsername(user) !== 'aibuilder_3';
}

export function canViewAnomalyEvents(user) {
  if (!hasRole(user, 'AI_BUILDER')) {
    return true;
  }
  return normalizeUsername(user) !== 'aibuilder_2';
}

export function isEmployee(user) {
  return hasRole(user, ROLE.EMPLOYEE);
}

export function isEmployeeRequesterFull(user) {
  return isEmployee(user) && normalizeUsername(user) === 'employee1';
}

export function isEmployeeRequesterLimited(user) {
  return isEmployee(user) && normalizeUsername(user) === 'employee2';
}

export function isEmployeeObserver(user) {
  return isEmployee(user) && normalizeUsername(user) === 'employee3';
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
  return hasAnyRole(user, [ROLE.ADMIN]);
}

export function canDeleteSubjectRequest(user) {
  return canProcessSubjectRequest(user);
}
