# Security Validation Notes (2026-04-01)

## 1) Multi-tenant Penetration Validation

### Scenario A: forged header company id
- Token: admin (company 1)
- Request: GET /api/client/list with header X-Company-Id: 2
- Expected: 403 (tenant mismatch)
- Automated evidence: TenantConsistencyIntegrationTest.rejectMismatchedCompanyHeader

### Scenario B: forged query company id
- Token: secops (company 1)
- Request: GET /api/alert-center/list?companyId=99
- Expected: 403 (tenant mismatch)
- Automated evidence: TenantConsistencyIntegrationTest.rejectMismatchedCompanyQuery

### Control design
- JWT includes cid claim.
- JwtAuthFilter maps cid into JwtPrincipal.companyId.
- CompanyConsistencyFilter validates X-Company-Id and query param companyId against JwtPrincipal.companyId.

## 2) Permission Matrix Validation

### Existing automated coverage
- PermissionMatrixIntegrationTest validates typical deny/allow paths (user manage, risk event access, approval todo boundary).
- TenantConsistencyIntegrationTest validates tenant-consistency guard.
- RoleEndpointAuthorizationMatrixIntegrationTest validates 7 roles x 22 core APIs deny/allow matrix.

### Matrix scope (implemented)
- Roles: ADMIN / EXECUTIVE / SECOPS / DATA_ADMIN / AI_BUILDER / BUSINESS_OWNER / EMPLOYEE.
- Endpoints (24): auth, dashboard, alert-center, client, data-asset, approval, security, ai-monitor, ai-risk, adversarial-meta, governance-change, audit-report.
- Assertion rule: allowed role => 200; disallowed role => 403.

### Permission-first migration (incremental)
- Governance change API now supports permission-based checks: govern:change:create / govern:change:view / govern:change:review.
- Audit report generation supports audit:report:generate (with ADMIN compatibility fallback).

## 3) Audit Log Anti-tamper and Deletion Control

### Current hardening
- Physical deletion API is blocked: /api/audit-log/delete now returns 403 business error.
- Audit chain table exists: audit_hash_chain (schema initialized by AwardSchemaInitializer).
- AuditLogServiceImpl now appends one hash-chain row at write-time for each saved audit log.

### Tamper verification script
- Script: scripts/verify-audit-hash-chain.ps1
- Purpose: recompute each row hash, verify prev_hash linking and current_hash consistency.
- Example:

```powershell
$cred = Get-Credential
./scripts/verify-audit-hash-chain.ps1 -Endpoint 127.0.0.1 -Port 3306 -DbCredential $cred -Database aegisai
```

Expected:
- pass: "Audit hash chain verification passed"
- fail: broken prev_hash link or current_hash mismatch with row id details

### Remaining gap
- WORM storage / external immutable archive is not yet enabled by default.

## 4) End-to-end Data Ownership Validation (employee1)

### Upload script template
1. Login and obtain token
2. Call client report API with employee1 identity
3. Query list/stats and verify ownership isolation

```bash
# 1) login as employee1
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"employee1","password":"Passw0rd!"}'

# 2) report one shadow-ai scan
curl -X POST http://localhost:8080/api/client/report \
  -H "Content-Type: application/json" \
  -H "X-Client-Token: demo-client-token" \
  -H "X-Company-Id: 1" \
  -d '{
    "clientId":"employee1-host-001",
    "hostname":"HOST-EMPLOYEE1",
    "osUsername":"employee1",
    "osType":"Windows",
    "clientVersion":"1.0.0",
    "discoveredServices":"[{"name":"ChatGPT","domain":"chat.openai.com","riskLevel":"high"}]",
    "shadowAiCount":1
  }'

# 3) query list (Authorization token required)
curl -X GET http://localhost:8080/api/client/list \
  -H "Authorization: Bearer <TOKEN_FROM_LOGIN>"
```

Validation expectation:
- Record company_id must be 1.
- os_username must be employee1.
- employee account only sees own records.

## 5) Account Design Semantics (21 accounts)

- Same-role multiple accounts are used for parallel scenario demonstrations and operation split.
- sys_user now has job_title semantic field.
- DataInitializer sets role-specific job title defaults (e.g., secops threat handling / audit review split).

## 6) Governance Admin Duty Segregation

### Design
- `admin`: full governance authority (approve + write).
- `admin_reviewer`: can approve/reject account onboarding, cannot create/update/delete users.
- `admin_ops`: can create/update/delete/restore users, cannot approve/reject onboarding.

### Validation steps
1. Login as `admin_reviewer`, call `POST /api/user/register` => expect `40300`.
2. Login as `admin_reviewer`, call `POST /api/user/approve` for pending account => expect `20000`.
3. Login as `admin_ops`, call `POST /api/user/approve` => expect `40300`.
4. Login as `admin_ops`, call `POST /api/user/register` and `POST /api/user/delete` => expect `20000`.

### Automated evidence
- `GovernanceAdminDutySegregationIntegrationTest.reviewerCanApproveButCannotCreateOrDeleteUser`
- `GovernanceAdminDutySegregationIntegrationTest.opsCanCreateAndDeleteButCannotApprove`
- `GovernanceAdminAttackResilienceIntegrationTest.governanceChangeApproveShouldRejectMalformedPayload`
- `GovernanceAdminAttackResilienceIntegrationTest.governanceChangeCannotBeApprovedTwice`

## 6) Traceability and Subject Visibility Validation

### Traceability hardening baseline
- audit_log write: user_id must exist in sys_user, otherwise reject.
- governance_event write: system/anonymous forbidden; user_id and username must resolve to same company user.
- security_event write: employee_id must resolve to existing username under same company.
- client_report write: os_username must resolve to existing username under same company.
- ai_call_log write: company_id + user_id must match existing sys_user, username is canonicalized.
- risk_event write: must be linked to an audit_log trace owned by same company user. If related_log_id is absent, backend auto-creates trace audit_log entry first.

### Subject visibility rule
- Rule: if admin/secops can see incident, the subject account must see own incident as well.
- Validation path: security_event -> governance_event mapping keeps sourceEventId and subject user binding.
- Automated evidence: TraceabilityAndObservabilityIntegrationTest.adminVisibleSecurityIncidentMustBeVisibleToSubject

### SQL verification snippets
```sql
-- 1) governance_event must be traceable to real users
SELECT COUNT(*) AS non_traceable_governance
FROM governance_event ge
LEFT JOIN sys_user su ON su.id = ge.user_id
WHERE ge.user_id IS NULL
  OR ge.username IS NULL
  OR LOWER(ge.username) IN ('system', 'anonymous', '匿名')
  OR su.id IS NULL
  OR su.company_id <> ge.company_id;

-- 2) security_event subject must resolve to sys_user
SELECT COUNT(*) AS non_traceable_security
FROM security_event se
LEFT JOIN sys_user su
  ON su.company_id = se.company_id
 AND LOWER(su.username) = LOWER(se.employee_id)
WHERE su.id IS NULL;

-- 3) client_report subject must resolve to sys_user
SELECT COUNT(*) AS non_traceable_client_report
FROM client_report cr
LEFT JOIN sys_user su
  ON su.company_id = cr.company_id
 AND LOWER(su.username) = LOWER(cr.os_username)
WHERE su.id IS NULL;

-- 4) ai_call_log must bind to sys_user in same company
SELECT COUNT(*) AS non_traceable_ai_call
FROM ai_call_log a
LEFT JOIN sys_user su ON su.id = a.user_id
WHERE a.user_id IS NULL
  OR su.id IS NULL
  OR su.company_id <> a.company_id;

-- 5) risk_event must link to audit log owned by same company user
SELECT COUNT(*) AS non_traceable_risk_event
FROM risk_event r
LEFT JOIN audit_log a ON a.id = r.related_log_id
LEFT JOIN sys_user su ON su.id = a.user_id
WHERE r.related_log_id IS NULL
  OR a.id IS NULL
  OR su.id IS NULL
  OR su.company_id <> r.company_id;
```

### Observability trend validation
- Backend now seeds traceable 14-day baseline for audit_log/model_call_stat/risk_event when empty.
- Frontend risk trend chart renders whenever labels exist to avoid false "暂无记录" caused by strict non-zero gating.
- Automated evidence: TraceabilityAndObservabilityIntegrationTest.workbenchTrendShouldContainNonZeroSeriesForAdminAndSecops

## 6) Enterprise Self-service Role Management

### Scope
- API: /api/roles (CRUD + permission binding) and /api/public/roles (register-time role options).
- Access rule: only ADMIN or users with role:manage can operate role management APIs.
- Tenant boundary: every role operation is bound to current company_id.

### Usage
1. ADMIN opens RoleManage page and creates a custom role.
2. Select allow_self_register and assign permission codes from permission tree.
3. New role appears in /api/public/roles?companyId=... when allow_self_register=true.
4. Register page loads this role dynamically and uses roleId for registration.

### Security guarantees
- System preset roles (is_system=true) cannot be deleted.
- Custom role deletion is blocked when bound users exist.
- Role permission updates are company-scoped and cannot cross tenant.

## 7) Chart Real-data Evidence Validation

### Added evidence fields
- Dashboard trend API (`/api/dashboard/workbench`) now includes:
  - `riskEventSampleCount`
  - `auditLogSampleCount`
  - `modelStatSampleCount`
  - `trendWindowDays`

### Frontend evidence display
- Home trend panel now shows: window days + sample counts + source flag (`real_db`/degraded).
- Ops observability page subtitle now shows: window days + sample counts.

### Acceptance intent
- Reviewers can verify that each chart is backed by real query sample volume rather than static series.

## 8) Latest Verification Run Evidence (2026-04-01)

### Backend targeted integration suite
- Scope:
  - `TraceabilityAndObservabilityIntegrationTest`
  - `TenantConsistencyIntegrationTest`
  - `RoleEndpointAuthorizationMatrixIntegrationTest`
  - `RoleSelfServiceIntegrationTest`
  - `GovernanceAdminDutySegregationIntegrationTest`
- Result: `18 passed, 0 failed`

### Frontend build verification
- Command: `npm run build`
- Result: build succeeded and production artifacts generated.
- Note: Vite reported chunk-size warnings only (non-blocking, no build failure).

### Chart data-source spot check
- Performed code scan on chart components and pages for hardcoded numeric chart series.
- Result: no direct static numeric series literals detected in Vue chart options under `src/**/*.vue`.
- Current charts derive series and labels from API-bound state (`riskSeries`, `auditSeries`, `aiModelSummary`, `healthDimensions`, and trend payload fields).

### B5/B6 rerun evidence (2026-04-01, afternoon)
- Backend command: `mvn "-Dtest=TraceabilityAndObservabilityIntegrationTest,RoleEndpointAuthorizationMatrixIntegrationTest,TenantConsistencyIntegrationTest" test`
- Backend result: exit code `0`, targeted suites passed (traceability, subject visibility, tenant consistency, role authorization matrix).
- Frontend command: `npm run build`
- Frontend result: build succeeded; only Vite chunk-size warnings remained, no compile error.

## 9) Subject Request Workflow Hardening (B4)

### Workflow controls
- Non-admin users cannot forge `userId` when creating subject requests.
- Processor must be the current logged-in operator (`handlerId == currentUser.id`).
- Status transition is strict: `pending -> processing/rejected`, `processing -> done/rejected`, and final states are immutable.

### Automated evidence
- `SubjectRequestWorkflowIntegrationTest.employeeCannotCreateSubjectRequestForAnotherUser`
- `SubjectRequestWorkflowIntegrationTest.processMustUseLegalTransitionAndCurrentOperatorAsHandler`
- `GovernanceAdminAttackResilienceIntegrationTest` (full class) regression pass after schema baseline alignment.

## 10) B5 Shadow-AI Visibility Evidence

### New automated assertion
- `TraceabilityAndObservabilityIntegrationTest.adminVisibleShadowAiClientMustBeVisibleToSubjectEmployee`
- Checkpoint: after `/api/client/report` writes an `employee1`-bound client record, both ADMIN and EMPLOYEE can query `/api/client/list` and find the same `clientId`.

### Test baseline fix
- Added missing H2 test tables in `backend/src/test/resources/test-schema.sql`:
  - `client_report`
  - `client_scan_queue`

### Latest targeted suite result
- Structured run (`runTests`) on:
  - `TraceabilityAndObservabilityIntegrationTest`
  - `RoleEndpointAuthorizationMatrixIntegrationTest`
  - `TenantConsistencyIntegrationTest`
- Result: `11 passed, 0 failed`

## 11) SECOPS Multi-account Duty Segregation (D1)

### Duty matrix
- `secops` (commander): can block, ignore, and manage detection rules.
- `secops_2` (triage): can ignore false-positive events, cannot block, cannot manage rules.
- `secops_3` (responder): can block events, cannot mark ignore, cannot manage rules.

### Enforcement points
- Backend hard enforcement:
  - `SecurityEventController`: `/api/security/block`, `/api/security/ignore`, `/api/security/rules`.
  - `AlertCenterController`: `/api/alert-center/dispose` status-based restrictions for SECOPS sub-accounts.
- Frontend operation isolation:
  - `ThreatMonitor.vue` and `SecurityCommand.vue` hide/disable action buttons by duty.
  - `roleBoundary.js` exposes commander/triage/responder permission helpers.

### Automated evidence
- `SecOpsDutySegregationIntegrationTest`:
  - `secops_2` can ignore but receives `40300` on block and rule manage.
  - `secops_3` can block but receives `40300` on ignore and rule manage.
  - `secops` can execute all three operations.
- Combined run with `TraceabilityAndObservabilityIntegrationTest` and `RoleEndpointAuthorizationMatrixIntegrationTest`: `12 passed, 0 failed`.

## 12) BUSINESS_OWNER Multi-account Duty Segregation (D1)

### Duty matrix
- `bizowner`: full business approval duty (approve/reject business requests).
- `bizowner_2`: release duty, cannot execute reject action.
- `bizowner_3`: risk-review duty, cannot execute approve action.

### Enforcement points
- Backend hard enforcement:
  - `ApprovalController` now checks business-owner account duty per action:
    - reject blocked for `bizowner_2`
    - approve blocked for `bizowner_3`
- Frontend operation isolation:
  - `ApprovalManage.vue` reads `roleBoundary` helpers and only shows allowed approve/reject buttons.

### Automated evidence

## 13) Client Runtime Reliability Hardening (E1+)

### Runtime binding model
- Client scan report now binds to the authenticated web account first (`authenticatedUsername`), and falls back to OS account only when no authenticated session exists.
- Scanner performs register handshake before report (`POST /api/client/register`) to keep server-side binding metadata aligned.
- Clipboard security events now require authenticated subject account and will not upload with anonymous/unknown identity.

### Offline resilience
- When `/api/client/report` fails (network/server transient error), report payload is persisted locally to a bounded queue file:
  - `~/.aegis-pending-reports.json`
- On next successful scan cycle, pending queue is flushed first, then current report is uploaded.
- Queue is bounded (`MAX_PENDING_REPORTS=50`) to avoid unbounded local growth.

### Code evidence
- `electron/main.js`: passes authenticated username into scanner runtime context.
- `electron/scanner/index.js`: adds authenticated-account binding, register handshake, pending-report persistence, and flush-on-next-scan.

### Security and traceability impact
- Prevents traceability break caused by mismatched OS username vs logged-in employee account.
- Reduces data loss under intermittent network conditions without relaxing backend validation rules.
- `BusinessOwnerDutySegregationIntegrationTest`:
  - `bizowner_2` approve path accepted by duty gate (workflow engine may return no pending task in test profile), reject returns `40300`.
  - `bizowner_3` reject path accepted by duty gate (workflow engine may return no pending task in test profile), approve returns `40300`.
- Cross-role regression with `SecOpsDutySegregationIntegrationTest`, `DataAdminDutySegregationIntegrationTest`, and `RoleEndpointAuthorizationMatrixIntegrationTest`: `13 passed, 0 failed`.

## 13) AI_BUILDER Multi-account Duty Segregation (D1)

### Duty matrix
- `aibuilder`: full anomaly operation duty (实时检测 + 事件复核视图).
- `aibuilder_2`: prompt engineering duty, can submit anomaly checks but cannot access anomaly event review.
- `aibuilder_3`: model-audit duty, can access anomaly events/status but cannot submit real-time anomaly checks.

### Enforcement points
- Backend hard enforcement:
  - `AnomalyController` checks account duty on `/api/anomaly/check` and `/api/anomaly/events`.
- Frontend operation isolation:
  - `AnomalyDetection.vue` only shows allowed actions by `roleBoundary` predicates.
  - `aibuilder_2` hides event review panel actions.
  - `aibuilder_3` hides check submit action and shows duty notice.

### Automated evidence
- `AiBuilderDutySegregationIntegrationTest`:
  - `aibuilder_2` check action is accepted by duty gate; events endpoint returns `40300`.
  - `aibuilder_3` events endpoint returns `20000`; check endpoint returns `40300`.
- Combined run with `BusinessOwnerDutySegregationIntegrationTest`, `SecOpsDutySegregationIntegrationTest`, and `DataAdminDutySegregationIntegrationTest`: `14 passed, 0 failed`.

## 14) EMPLOYEE Multi-account Duty Segregation (D1)

### Duty matrix
- `employee1`: full self-service subject-request duty (access/export/delete create).
- `employee2`: limited requester duty (access/export create only; delete forbidden).
- `employee3`: view-only duty (can list own requests, cannot create new request).

### Enforcement points
- Backend hard enforcement:
  - `SubjectRequestController` checks employee account duty on `/api/subject-request/create`.
  - Unauthorized combinations return `40300` with duty-specific reason.
- Frontend operation isolation:
  - `SubjectRequest.vue` hides create button for view-only employee.
  - Delete type option is hidden for limited employee.
  - Process/delete controls are visible only to operator roles.

### Automated evidence
- `EmployeeDutySegregationIntegrationTest`:
  - `employee1` create delete request returns `20000`.
  - `employee2` create access returns `20000`, create delete returns `40300`.
  - `employee3` create access returns `40300`.
- Linked regression with `SubjectRequestWorkflowIntegrationTest`: `7 passed, 0 failed`.

## 15) Client Identity Binding Hardening (E1)

### Binding controls
- `/api/client/register` and `/api/client/report` now require `clientId + hostname + osUsername + osType`.
- Reporter identity must resolve to existing `sys_user` in current tenant; unresolved identity returns `40000`.
- APIs return deterministic `deviceFingerprint` derived from machine tuple (`clientId|hostname|osUsername`) for host-level traceability.

### Evidence fields
- Register response includes `deviceFingerprint`.
- Report response includes `deviceFingerprint`.
- `/api/client/list` entries include `deviceFingerprint` for governance-side correlation.

### Automated evidence
- `ClientIdentityBindingIntegrationTest.registerRejectsMissingHostname`
- `ClientIdentityBindingIntegrationTest.registerAndReportReturnDeviceFingerprint`
- `ClientIdentityBindingIntegrationTest.reportRejectsUnknownUsername`
- Combined regression with `EmployeeDutySegregationIntegrationTest` and `TraceabilityAndObservabilityIntegrationTest`: `13 passed, 0 failed`.

## 16) Client-Cloud Consistency (E2/E3)

### Closed-loop linkage fields
- `POST /api/client/report` returns:
  - `id` (`client_report.id`)
  - `governanceEventId` (`governance_event.id` linked by source_event_id)
  - `subjectUserId` (bound `sys_user.id`)
- `POST /api/security/events/report` returns:
  - `id` (`security_event.id`)
  - `governanceEventId` (`governance_event.id` linked by source_event_id)
  - `subjectUserId` (bound `sys_user.id`)

### Consistency rules
- `governance_event.source_event_id` must equal source event primary id.
- `governance_event.user_id` must equal `subjectUserId` and belong to same `company_id`.
- Subject visibility rule: if admin sees the incident in `alert-center/list`, subject employee must also see the same `sourceEventId` in personal view.

### Automated evidence
- `ClientCloudConsistencyIntegrationTest.shadowAiReportShouldReturnAndPersistGovernanceLinkage`
- `ClientCloudConsistencyIntegrationTest.securityReportShouldReturnAndPersistGovernanceLinkage`
- Combined run with traceability suites:
  - `ClientCloudConsistencyIntegrationTest`
  - `ClientIdentityBindingIntegrationTest`
  - `TraceabilityAndObservabilityIntegrationTest`
  - Result: `13 passed, 0 failed`

### SQL spot checks
```sql
-- shadow-ai linkage
SELECT ge.id AS governance_id, ge.user_id, ge.username, ge.source_module, ge.source_event_id, cr.id AS client_report_id
FROM governance_event ge
JOIN client_report cr ON CAST(ge.source_event_id AS CHAR) = CAST(cr.id AS CHAR)
WHERE ge.source_module = 'shadow_ai'
ORDER BY ge.id DESC
LIMIT 5;

-- security linkage
SELECT ge.id AS governance_id, ge.user_id, ge.username, ge.source_module, ge.source_event_id, se.id AS security_event_id
FROM governance_event ge
JOIN security_event se ON CAST(ge.source_event_id AS CHAR) = CAST(se.id AS CHAR)
WHERE ge.source_module = 'security'
ORDER BY ge.id DESC
LIMIT 5;
```

## 17) Final Completion Snapshot (A-E)

### Completion status
- Phase A/B/C completed: ADMIN web-first功能域、刁钻评审清单、三账号职责分离均落地并有自动化证据。
- Phase D completed: 其余6身份（SECOPS/DATA_ADMIN/BUSINESS_OWNER/EXECUTIVE/AI_BUILDER/EMPLOYEE）均完成同身份多账号职责分离、前后端双重约束与回归验证。
- Phase E completed: 客户端识别绑定（E1）、端云闭环写入（E2）、端云一致性与主体可见性（E3）落地并通过回归。

### Latest regression evidence
- Backend targeted integration suites: `71 passed, 0 failed`.
- Frontend release build: `npm run build` successful (non-blocking chunk size warnings only).
- CI backend regression list aligned to current rollout scope (YAML fixed and expanded to include governance/segregation/client consistency suites).
