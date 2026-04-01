# Enterprise Identity & Account Operational Playbook (2026-04-01)

## 1. Purpose

This playbook is for full enterprise-level operation rehearsal across all 7 identities and all seeded accounts.

Goals:
- Verify each identity can complete its business workflow end-to-end.
- Verify each account under the same identity has differentiated duties where required.
- Verify every key action is traceable to an existing account.
- Verify web and client flows form one closed loop.
- Expose where a step cannot connect to the next step and provide immediate fix guidance.

## 2. Baseline Accounts and Passwords

Source of truth: `backend/src/main/java/com/trustai/config/DataInitializer.java`

Default passwords:
- `admin`, `admin_reviewer`, `admin_ops`: `admin`
- All other seeded demo users: `Passw0rd!`

Seeded accounts by identity:
- ADMIN: `admin`, `admin_reviewer`, `admin_ops`
- EXECUTIVE: `executive`, `executive_2`, `executive_3`
- SECOPS: `secops`, `secops_2`, `secops_3`
- DATA_ADMIN: `dataadmin`, `dataadmin_2`, `dataadmin_3`
- AI_BUILDER: `aibuilder`, `aibuilder_2`, `aibuilder_3`
- BUSINESS_OWNER: `bizowner`, `bizowner_2`, `bizowner_3`
- EMPLOYEE: `employee1`, `employee2`, `employee3`

## 3. Account Creation (Enterprise Mode)

### 3.1 Who creates the first account
- Platform onboarding or enterprise owner creates tenant and first ADMIN account.
- Not open to public self-registration.

### 3.2 How subsequent members register
- Use invite-code flow, not manually entering arbitrary company identifiers.
- API chain:
1. ADMIN creates invite code: `POST /api/auth/invite-code/create`
2. Member opens registration page and inputs invite code
3. Backend resolves tenant by invite code
4. Member selects role from allowed self-register roles
5. Registration enters pending approval or active state per policy

### 3.3 Invite lifecycle operations
- List invite codes: `GET /api/auth/invite-code/list`
- Revoke invite code: `PUT /api/auth/invite-code/{id}/revoke`
- Reactivate invite code: `PUT /api/auth/invite-code/{id}/reactivate`

### 3.4 Registration options checks
- Public registration roles: `GET /api/public/roles?companyId={id}`
- Invite-aware options: `GET /api/auth/registration-options?inviteCode={code}`

If broken:
- Symptom: invite create returns `50000` due SpEL authority evaluation.
- Fix: ensure `CurrentUserService` has `hasAuthority` / `hasAnyAuthority`.

## 4. Full Web Menu Coverage (Directory-by-Directory)

Routes source: `src/router/index.js`

All functional directories that must be tested:
1. `/` Home
2. `/ops-observability`
3. `/audit-log`
4. `/audit-report`
5. `/user-manage`
6. `/role-manage`
7. `/permission-manage`
8. `/approval-manage`
9. `/governance-change-manage`
10. `/sod-rule-manage`
11. `/policy-manage`
12. `/risk-event-manage`
13. `/threat-monitor`
14. `/operations-command`
15. `/data-asset`
16. `/subject-request`
17. `/sensitive-scan`
18. `/desense-preview`
19. `/ai/risk-rating`
20. `/ai/anomaly`
21. `/shadow-ai`
22. `/profile`
23. `/settings`

Test requirement per directory:
- One success path
- Two failure paths (authz/invalid input)
- One traceability check (actor + subject + tenant)

## 5. Identity-by-Identity Operational Guide

## 5.1 ADMIN (admin/admin_reviewer/admin_ops)

### Core workflow order
1. `/user-manage`: create pending users, approve/reject, recycle bin recovery
2. `/role-manage`: create/update custom role, bind permissions
3. `/permission-manage`: verify role-to-permission mapping
4. `/governance-change-manage`: submit/approve/reject changes
5. `/policy-manage` + `/sod-rule-manage`: policy governance
6. `/audit-log` + `/audit-report`: verify audit and anti-tamper chain
7. `/ops-observability`: verify trend and sample counts are non-zero

### Account-level duty split
- `admin`: full publishing authority
- `admin_reviewer`: approval/review focus
- `admin_ops`: daily operation, not final review authority

### Closure check
- Any action visible in ADMIN control view must be auditable with user id and tenant id.

## 5.2 SECOPS (secops/secops_2/secops_3)

### Core workflow order
1. `/threat-monitor`: event ingestion and queue
2. `/operations-command`: block/ignore workflows
3. `/risk-event-manage`: risk event lifecycle
4. `/shadow-ai`: endpoint shadow AI evidence
5. `/ops-observability`: security trend verification

### Account-level duty split
- `secops`: command + rule management
- `secops_2`: triage/ignore only
- `secops_3`: block/respond only

### Closure check
- Security actions must write back to governance_event/security_event and be visible to subject employee where applicable.

## 5.3 DATA_ADMIN (dataadmin/dataadmin_2/dataadmin_3)

### Core workflow order
1. `/data-asset`: register/upload/update/delete asset
2. `/approval-manage`: data approvals and disposition
3. `/sensitive-scan`: scan and classify
4. `/desense-preview`: validate masking output

### Account-level duty split
- `dataadmin`: full data governance
- `dataadmin_2`: maintenance only (no approval/delete high-risk)
- `dataadmin_3`: approval/review only

### Closure check
- Asset access and approvals must be tenant-scoped and user-traceable.

## 5.4 BUSINESS_OWNER (bizowner/bizowner_2/bizowner_3)

### Core workflow order
1. `/approval-manage`: business approval pipeline
2. `/subject-request`: business-side request coordination
3. `/home`: KPI and pending workload review

### Account-level duty split
- `bizowner`: approve and reject
- `bizowner_2`: approve only
- `bizowner_3`: reject only

### Closure check
- Approval actions must record approver id and become visible in audit timeline.

## 5.5 EXECUTIVE (executive/executive_2/executive_3)

### Core workflow order
1. `/` Home: governance pulse and KPI summary
2. `/ops-observability`: trends, incident volume, sample evidence
3. `/audit-report`: periodic governance report export

### Account-level behavior
- Primarily read/oversight profile; no low-level operation expected.

### Closure check
- Figures must map to real backend sample counts and trend windows.

## 5.6 AI_BUILDER (aibuilder/aibuilder_2/aibuilder_3)

### Core workflow order
1. `/ai/anomaly`: run and review anomaly checks
2. `/ai/risk-rating`: model risk detail and score dimensions
3. `/shadow-ai`: detect model usage drift and shadow services

### Account-level duty split
- `aibuilder`: detect + review
- `aibuilder_2`: detect only
- `aibuilder_3`: review only

### Closure check
- Detection outputs must map to auditable events, not front-end mock data.

## 5.7 EMPLOYEE (employee1/employee2/employee3)

### Core workflow order
1. `/subject-request`: request create/track
2. `/shadow-ai`: own endpoint findings
3. `/profile` + `/settings`: account preferences

### Account-level duty split
- `employee1`: full request rights
- `employee2`: limited request rights (no delete-type requests)
- `employee3`: observer/read-only profile

### Closure check
- If admin/secops can see employee issue, employee must also see related record in own scope.

## 6. Client Operational Guide (Desktop/Electron)

Client docs source: `electron/README.md`

### 6.1 Install and configure
1. Install client package
2. Configure server URL in client config
3. Log in with existing user account

### 6.2 Runtime data capture
- Shadow AI scanner uses real machine context (host/process/network/browser signals)
- Clip-board monitor reports only under login-bound identity
- Device fingerprint is returned and persisted in server responses

### 6.3 Reliability requirements
- Register handshake before report submission
- Offline queue and replay on reconnect
- Replay upper bound and dedupe controls

### 6.4 End-to-end closure checks
1. Client report API returns `id`, `governanceEventId`, `subjectUserId`
2. Admin sees event in governance/security views
3. Subject employee sees corresponding event in personal view
4. Audit trail includes actor/subject/tenant

## 7. New Custom Role Validation (Enterprise Realism)

When enterprise adds a new role:
1. Create role in `/role-manage`
2. Bind permission codes in role permission tree
3. If role is high-risk self-register, enforce review workflow before effect
4. Verify role appears only under same company in registration options
5. Create a test account via invite code and assign the new role
6. Validate menu/path authorization equals permission mapping

Expected parity:
- New role account should behave consistently with demo roles based on permission codes, not hardcoded role names.

## 8. Breakpoint-to-Fix Matrix (When Step Cannot Continue)

1. Registration broken after invite input
- Check invite code status (`active`), expiration, tenant binding.

2. Role APIs return 403 for admin-like account
- Check `role:manage`/`user:manage` permission mappings and role assignment.

3. Public role list empty
- Check `allow_self_register=true` and company scope in role table.

4. Security event visible to admin but not subject
- Check employee_id/subject_user_id mapping and company scope filters.

5. Trend charts are zero while data exists
- Check backend aggregation sample windows and frontend empty-state guards.

6. Test context fails due Elasticsearch connection
- Mock ES repositories in integration tests for offline test profile.

## 9. Enterprise Acceptance Checklist (Final)

1. Identity coverage
- All 7 identities and 21 seeded accounts complete login + one full flow each.

2. Menu coverage
- All 23 web directories tested with success/failure/traceability checks.

3. Tenant isolation
- Cross-tenant forged requests rejected (403).

4. Traceability
- All governance-related writes bind to existing user and tenant.

5. Subject visibility
- Admin-visible issue is also visible in subject account where applicable.

6. Client closure
- Real machine event -> server write -> admin view -> subject view -> audit chain.

7. Build and regressions
- Backend targeted regression suites green.
- Frontend build passes.

## 10. Recommended Next Hardening (for national-level excellence)

1. Add MFA and enterprise SSO for all privileged identities.
2. Add invite code throttling, one-time invite option, and IP/device constraints.
3. Enforce two-person approval for high-risk role publication and policy changes.
4. Add periodic certification workflow (role recertification, dormant account cleanup).
5. Add objective KPI dashboard (MTTR, false-positive rate, unauthorized-block rate, replay success rate).
