# Governance-Admin 8-Issue Closure Report (2026-04-01)

## Scope
This report closes the 8 governance-admin complaints raised in the latest review cycle, with direct code evidence and post-change validation outcomes.

## Overall Result
- Closed: 8 / 8
- Open: 0 / 8
- Regression status:
  - Identity issue matrix: total 70, mismatch 0, serverError 0
  - Account duty segregation matrix: total 22, mismatch 0, serverError 0

Evidence artifacts:
- `docs/identity-issue-regression-summary.json`
- `docs/identity-issue-regression-results.csv`
- `docs/account-duty-segregation-summary.json`
- `docs/account-duty-segregation-results.csv`

## Issue-by-Issue Closure

### 1) Observability page cannot distinguish "no data" vs "module unavailable"
- Status: Closed
- Fix:
  - Added module-level failure tracking and descriptive empty-state text for risk trend, alert stats, and AI usage.
  - Added warning toast when partial modules fail while keeping available panels visible.
- Code:
  - `src/views/OpsObservability.vue`

### 2) Governance change page lacks requester/reviewer identity traceability
- Status: Closed
- Fix:
  - Added requester/reviewer trace columns: account, role, department, position, company, device/IP.
  - Added parser supporting both JSON payload trace and legacy TRACE text fallback.
  - Added user-directory resolution from `/user/list` to enrich display.
- Code:
  - `src/views/GovernanceChangeManage.vue`
  - `backend/src/main/java/com/trustai/controller/GovernanceChangeController.java`

### 3) Risk event handling page lacks operator identity depth
- Status: Closed
- Fix:
  - Added handler trace columns: account, role, department, position, company.
  - Added `/user/list`-based directory mapping for stable display.
- Code:
  - `src/views/RiskEventManage.vue`

### 4) Employee AI behavior monitoring lacks account-level traceability and has garble readability issues
- Status: Closed
- Fix:
  - Added anomaly/privacy event columns for account name/id, role, position, department, device/IP.
  - Added user lookup by id/username fallback.
  - Added sanitizeText rule for repeated question-mark corruption fragments.
- Code:
  - `src/views/EmployeeAiBehaviorMonitor.vue`

### 5) Subject request workflow lacks requester/handler trace depth
- Status: Closed
- Fix:
  - Added requester/handler trace columns in UI.
  - Added TRACE append on create to preserve actor snapshot (username/userId/role/department/position/company/device).
- Code:
  - `src/views/SubjectRequest.vue`
  - `backend/src/main/java/com/trustai/controller/SubjectRequestController.java`

### 6) Approval workflow traceability insufficient for applicant/approver identities
- Status: Closed
- Fix:
  - Added applicant/approver trace columns in approval UI.
  - Added normalized decision parsing (`approve/approved/pass`, `reject/rejected/deny`) and stable status constants.
  - Added TRACE snapshot in apply reason for source-side accountability.
- Code:
  - `src/views/ApprovalManage.vue`
  - `backend/src/main/java/com/trustai/controller/ApprovalController.java`

### 7) Permission management page looked like flat DB listing, not operational governance view
- Status: Closed
- Fix:
  - Refactored into three tabs:
    - Permission list maintenance
    - Company permission tree (`/permissions/tree`)
    - Role-permission matrix (`/permission/matrix`)
  - Added readable permission tags and lazy tab data loading.
- Code:
  - `src/views/PermissionManage.vue`

### 8) Governance-admin role ownership boundaries needed to be reflected explicitly in menus/routes and runtime behavior
- Status: Closed
- Fix:
  - Expanded/adjusted menu audiences and extra-route audiences for governance-admin visibility where agreed.
  - Added role-access and permission checks alignment in router.
  - Backend role authorization expanded for observability/risk APIs where role-level access was approved.
- Code:
  - `src/utils/persona.js`
  - `src/router/index.js`
  - `backend/src/main/java/com/trustai/controller/AiCallController.java`
  - `backend/src/main/java/com/trustai/controller/AiRiskRatingController.java`

## Validation Summary
- Build/compile:
  - Frontend build passed.
  - Backend compile passed.
- Regression:
  - Identity issue regression: pass (70/70 checks, mismatch 0).
  - Account duty segregation regression: pass (22/22 checks, mismatch 0).

## Residual Risk
- Historical records created before trace snapshot rollout may still rely on fallback parsing and user-directory enrichment.
- Device/IP trace fields are partially dependent on upstream payload completeness for some legacy event paths.

## Conclusion
All 8 complaints are closed with concrete code changes and post-change matrix verification. The current state is acceptable for governance-admin closure with no observed permission regression in the two guard matrices.

---

## Addendum: 6 Additional Rectifications (2026-04-02)

This addendum records the follow-up implementation for the 6 additional issues raised after initial closure.

### A1) Privacy-shield alert detail belongs to Employee AI Behavior Monitor
- Status: Implemented
- Change:
  - Removed privacy-shield trace table from Ops Observability and added explicit guidance to Employee AI Behavior Monitor.
  - Kept Ops Observability focused on trend/stat layers.
- Code:
  - `src/views/OpsObservability.vue`

### A2) Ops Observability three modules still empty
- Status: Implemented
- Change:
  - Strengthened baseline seeding with account-traceable records for risk/audit/model stats.
  - Added `ai_call_log` + `governance_event` trace seed entries and multi-burst day-level samples.
  - Added fallback logic in AI monitor summary endpoint (derive from `model_call_stat` when call log is sparse).
- Code:
  - `backend/src/main/java/com/trustai/config/DataInitializer.java`
  - `backend/src/main/java/com/trustai/controller/AiCallController.java`

### A3) ID-card desensitization output incorrect
- Status: Implemented
- Change:
  - Unified to first-6 + masked-8 + last-4 rule in backend default config.
  - Frontend explanation aligned to actual masking behavior.
- Code:
  - `backend/src/main/java/com/trustai/service/impl/PrivacyShieldConfigServiceImpl.java`
  - `src/views/DesensePreview.vue`

### A4) Shadow-AI endpoint list only had employee accounts
- Status: Implemented
- Change:
  - Expanded shadow-AI list/history/stats endpoint visibility to all roles.
  - Scope rule: `ADMIN/SECOPS` can view company-wide; others view own account scope.
  - Added multi-role client baseline seeding.
- Code:
  - `backend/src/main/java/com/trustai/controller/ClientReportController.java`
  - `backend/src/main/java/com/trustai/config/DataInitializer.java`

### A5) Company AI whitelist governance model
- Status: Implemented
- Change:
  - Added company-scoped whitelist API workflow:
    - Data Admin submits pending request.
    - Governance Admin reviews approve/reject.
    - Others are read-only.
  - Added frontend whitelist governance panel in AI Risk Rating page.
- Code:
  - `backend/src/main/java/com/trustai/controller/AiRiskRatingController.java`
  - `src/views/AiRiskRating.vue`

### A6) Purpose-driven permissions + more traceable records for Sensitive Scan and Subject Request
- Status: Implemented
- Change:
  - Sensitive Scan: conservative split (ADMIN + DATA_ADMIN), DATA_ADMIN scoped to own submitted tasks.
  - Added task trace fields (`companyId`, `userId`, `traceJson`) and UI display.
  - Subject Request: conservative split (employee submits, governance admin processes/deletes).
  - Added purpose guidance text in both pages.
- Code:
  - `backend/src/main/java/com/trustai/controller/SensitiveScanController.java`
  - `backend/src/main/java/com/trustai/entity/SensitiveScanTask.java`
  - `backend/src/main/java/com/trustai/config/CompanySchemaInitializer.java`
  - `backend/src/main/java/com/trustai/controller/SubjectRequestController.java`
  - `src/views/SensitiveScan.vue`
  - `src/views/SubjectRequest.vue`
  - `src/utils/roleBoundary.js`

### Build Validation (Addendum)
- Backend compile: passed
- Frontend build: passed

### Runtime Note
- Some newly added APIs/data-seeding effects require backend runtime refresh (new image/container restart) to be observed in live environment.
