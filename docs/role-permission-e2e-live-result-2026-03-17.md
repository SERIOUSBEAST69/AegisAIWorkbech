# Role Permission E2E Live Result (2026-03-17)

## Scope
- Environment under test: Docker backend on http://localhost:8080.
- Token source: demo seed accounts via /api/auth/login.
- Roles tested: ADMIN, EXECUTIVE, SECOPS, DATA_ADMIN, AI_BUILDER, BUSINESS_OWNER, EMPLOYEE.
- Permission matrix size: 25 endpoints x 7 roles = 175 checks.
- Extra smoke scope: privacy shield endpoints + anomaly check role behavior.

## Summary
- Total checks: 175
- Mismatches: 0
- Login failures: none
- Matrix summary: docs/role-permission-e2e-summary.json
- Matrix details: docs/role-permission-e2e-results.csv

## Runtime Alignment Status
1. Docker backend image rebuilt and container recreated on 2026-03-17 21:51 local time.
2. Initial immediate run showed full login failure (startup warm-up timing); rerun after readiness passed with 0 mismatches.
3. Method-security denials now map correctly in API body as 40300/40100 via global exception mapping.

## Targeted Privacy Shield Smoke Validation
Evidence file: docs/privacy-shield-smoke-2026-03-17.json

1. Public config read (no token)
- GET /api/privacy/config/public => code 20000

2. Event reporting (no token)
- POST /api/privacy/events => code 20000 (employee.demo sample)
- POST /api/privacy/events => code 20000 (biz.demo sample)

3. Role-scoped event visibility
- EMPLOYEE GET /api/privacy/events => summaryOnly false, firstUserId employee.demo
- BUSINESS_OWNER GET /api/privacy/events => summaryOnly false, firstUserId biz.demo
- EXECUTIVE GET /api/privacy/events => summaryOnly true, listCount 0
- ADMIN GET /api/privacy/events => summaryOnly false, listCount 4

4. Config management access
- ADMIN GET /api/privacy/config => 20000
- SECOPS GET /api/privacy/config => 20000
- EXECUTIVE GET /api/privacy/config => 40300

5. Anomaly check role gate
- EXECUTIVE POST /api/anomaly/check => 40300
- EMPLOYEE POST /api/anomaly/check => 50000 with "请求体不能为空" (expected auth pass + business validation fail)

## Remaining Manual Desktop Checks
The following items require interactive desktop operations and cannot be fully automated in this CLI-only run:

Checklist file: docs/privacy-shield-desktop-checklist-2026-03-17.md

1. Word/Notepad copy scenario
- Expectation: no popup warning; audit-only event allowed.

2. AI active-window copy scenario (ChatGPT/豆包/文心一言 window focused)
- Expectation: system notification shown and event uploaded.

3. Browser extension banner interaction
- Expectation: sensitive input shows floating banner, one-click desensitize rewrites input, ignore action writes event.

## Final Verdict
- Role-permission enforcement target is met (mismatchCount = 0 on live 8080).
- Privacy shield API and role-view logic are validated via targeted smoke tests.
- Desktop interaction checks are documented and ready for operator execution.
