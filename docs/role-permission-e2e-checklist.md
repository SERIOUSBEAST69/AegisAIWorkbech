# AegisAI Role Permission E2E Checklist

## 1. Purpose
Validate route guard, menu visibility, and backend authorization are aligned for all roles after matrix hardening.

## 2. Test Data and Accounts
Prepare one valid account per role:
- ADMIN
- EXECUTIVE
- SECOPS
- DATA_ADMIN
- AI_BUILDER
- BUSINESS_OWNER
- EMPLOYEE

Use real JWT login from /api/auth/login and keep one token per role.

## 3. Global Checks for Every Role
1. Login succeeds and Home page loads.
2. Hidden menu entries are not rendered.
3. Direct URL navigation to forbidden routes is redirected to / (or /ai/anomaly for EMPLOYEE).
4. Calling forbidden APIs returns 40300.
5. Calling allowed APIs returns 20000.

## 4. Route and Menu Regression by Role

### ADMIN
Must be visible and accessible:
- /
- /operations-command
- /global-search
- /data-asset
- /ai-model-manage
- /model-cost
- /desense-preview
- /shadow-ai
- /threat-monitor
- /ai/risk-rating
- /ai/anomaly
- /alerts
- /audit-log
- /audit-report
- /sensitive-scan
- /approval-manage
- /data-share
- /risk-event-manage
- /subject-request
- /policy-manage
- /user-manage
- /role-manage
- /permission-manage

### EXECUTIVE
Must be visible and accessible:
- /
- /operations-command
- /global-search
- /model-cost
- /ai/anomaly
- /audit-report

Must be forbidden:
- /data-asset
- /ai-model-manage
- /desense-preview
- /shadow-ai
- /threat-monitor
- /alerts
- /audit-log
- /approval-manage
- /data-share
- /risk-event-manage
- /subject-request
- /policy-manage
- /user-manage
- /role-manage
- /permission-manage

### SECOPS
Must be visible and accessible:
- /
- /global-search
- /ai-model-manage
- /desense-preview
- /shadow-ai
- /threat-monitor
- /ai/risk-rating
- /ai/anomaly
- /alerts
- /audit-log
- /audit-report
- /sensitive-scan
- /risk-event-manage
- /policy-manage

Must be forbidden:
- /operations-command
- /data-asset
- /approval-manage
- /data-share
- /subject-request
- /user-manage
- /role-manage
- /permission-manage

### DATA_ADMIN
Must be visible and accessible:
- /
- /global-search
- /data-asset
- /desense-preview
- /ai/anomaly
- /sensitive-scan
- /approval-manage
- /data-share
- /policy-manage

Must be forbidden:
- /operations-command
- /shadow-ai
- /threat-monitor
- /alerts
- /audit-log
- /risk-event-manage
- /subject-request
- /user-manage
- /role-manage
- /permission-manage

### AI_BUILDER
Must be visible and accessible:
- /
- /global-search
- /ai-model-manage
- /model-cost
- /desense-preview
- /shadow-ai
- /ai/risk-rating
- /ai/anomaly
- /policy-manage

Must be forbidden:
- /operations-command
- /data-asset
- /threat-monitor
- /alerts
- /audit-log
- /sensitive-scan
- /approval-manage
- /data-share
- /risk-event-manage
- /subject-request
- /user-manage
- /role-manage
- /permission-manage

### BUSINESS_OWNER
Must be visible and accessible:
- /
- /global-search
- /model-cost
- /ai/anomaly
- /approval-manage
- /data-share

Must be forbidden:
- /operations-command
- /data-asset
- /ai-model-manage
- /desense-preview
- /shadow-ai
- /threat-monitor
- /alerts
- /audit-log
- /risk-event-manage
- /subject-request
- /policy-manage
- /user-manage
- /role-manage
- /permission-manage

### EMPLOYEE
Must be visible and accessible:
- /
- /global-search
- /ai/anomaly
- /approval-manage
- /data-share
- /profile
- /settings

Must be forbidden:
- /operations-command
- /data-asset
- /ai-model-manage
- /model-cost
- /desense-preview
- /shadow-ai
- /threat-monitor
- /alerts
- /audit-log
- /audit-report
- /sensitive-scan
- /risk-event-manage
- /subject-request
- /policy-manage
- /user-manage
- /role-manage
- /permission-manage

## 5. Backend API Authorization Smoke Cases
Use the same payload for all roles and assert status code.

### Expected Allow by Role
- ADMIN:
  - GET /api/ai/adversarial/meta
  - POST /api/ai/adversarial/run
  - GET /api/user/list
  - POST /api/role/add
  - POST /api/permission/add
  - POST /api/risk-event/add
  - POST /api/data-share/approve
  - POST /api/desense/save
  - POST /api/subject-request/process

- EXECUTIVE:
  - GET /api/dashboard/workbench
  - GET /api/search/global
  - GET /api/audit-report/compare
  - GET /api/anomaly/events

- SECOPS:
  - GET /api/alerts/list
  - GET /api/audit-log/search
  - GET /api/security/events
  - POST /api/risk-event/update
  - POST /api/ai/quota/reset/{modelCode}
  - GET /api/desense/rules

- DATA_ADMIN:
  - GET /api/data-asset/list
  - POST /api/data-asset/upload
  - GET /api/sensitive-scan/list
  - POST /api/data-share/approve
  - GET /api/desense/rules

- AI_BUILDER:
  - GET /api/ai-model/list
  - POST /api/ai-model/update
  - GET /api/ai/monitor/summary
  - POST /api/ai/quota/reset/{modelCode}
  - POST /api/desense/recommend

- BUSINESS_OWNER:
  - GET /api/approval/list
  - POST /api/approval/apply
  - POST /api/approval/approve
  - GET /api/data-share/list
  - POST /api/data-share/apply

- EMPLOYEE:
  - GET /api/search/global
  - GET /api/anomaly/events
  - GET /api/approval/list
  - POST /api/approval/apply
  - GET /api/data-share/list
  - POST /api/data-share/apply

### Expected Deny Samples
- EXECUTIVE should get 403 on:
  - GET /api/alerts/list
  - GET /api/data-asset/list

- EMPLOYEE should get 403 on:
  - GET /api/risk-event/list
  - GET /api/alerts/list
  - GET /api/desense/rules
  - GET /api/user/list

- BUSINESS_OWNER should get 403 on:
  - POST /api/data-share/approve
  - GET /api/risk-event/list

- AI_BUILDER should get 403 on:
  - GET /api/data-asset/list
  - GET /api/audit-log/search

## 6. Focused Data-Scope Checks
1. Login as EMPLOYEE and call GET /api/search/global with a shared keyword.
Expected: approvals and personal-scope data only; no model list leak.
2. Login as EMPLOYEE and call GET /api/anomaly/events.
Expected: only events mapped to current employee identity.
3. Login as DATA_ADMIN and call GET /api/approval/list.
Expected: only [DATA] approvals are operable.
4. Login as BUSINESS_OWNER and call GET /api/approval/list.
Expected: only [BUSINESS] approvals are operable.

## 7. Regression Notes in This Round
Fixed during this verification round:
- Added role-level protection for all desensitization APIs under /api/desense/*.
- Restricted POST /api/ai/quota/reset/{modelCode} to ADMIN, SECOPS, AI_BUILDER.

## 8. Exit Criteria
Mark as PASS only if all three conditions hold:
1. Route/menu visibility matches role matrix.
2. Backend API allow/deny behavior matches matrix.
3. Sensitive data scope behavior matches personal-view constraints.
