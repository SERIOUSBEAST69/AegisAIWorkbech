const EMPLOYEE_ROLE_CODE = 'EMPLOYEE';
const AGREEMENT_VERSION = '2026-03-employee-v1';

function normalizeRoleCode(user) {
  return String(user?.roleCode || '').trim().toUpperCase();
}

export function isEmployeeUser(user) {
  return normalizeRoleCode(user) === EMPLOYEE_ROLE_CODE;
}

function buildAgreementKey(user) {
  const identity = String(user?.username || user?.id || '').trim().toLowerCase();
  return identity ? `aegis.employee.agreement.${identity}` : '';
}

export function hasAcceptedEmployeeAgreement(user) {
  if (!isEmployeeUser(user)) {
    return true;
  }
  const key = buildAgreementKey(user);
  if (!key) {
    return false;
  }
  return localStorage.getItem(key) === AGREEMENT_VERSION;
}

export function acceptEmployeeAgreement(user) {
  const key = buildAgreementKey(user);
  if (!key) {
    return;
  }
  localStorage.setItem(key, AGREEMENT_VERSION);
}

export function isEmployeeDetectionForced(user) {
  return isEmployeeUser(user) && hasAcceptedEmployeeAgreement(user);
}
