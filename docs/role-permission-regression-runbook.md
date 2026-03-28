# Role Permission Regression Runbook

## Files
- Matrix: scripts/role-permission-matrix.json
- Runner: scripts/run-role-permission-regression.ps1
- CSV output: docs/role-permission-e2e-results.csv
- JSON summary: docs/role-permission-e2e-summary.json

## Quick Start
From workspace root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-role-permission-regression.ps1
```

## Run Against Custom Backend URL

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-role-permission-regression.ps1 -BaseUrl "http://localhost:18080"
```

## What Counts as Access Denied
The runner treats a check as denied when any of these is true:
- HTTP status is 401 or 403
- Response body code is 40100 or 40300

Any other response is treated as "auth passed" (even if business validation fails).

## Expected Use After Deployment
1. Deploy backend from current repository commit.
2. Ensure database has all seven roles and demo users.
3. Run the script.
4. Open docs/role-permission-e2e-summary.json and confirm mismatchCount is 0.
5. If mismatchCount > 0, inspect rows in docs/role-permission-e2e-results.csv.

## Updating Matrix
Edit scripts/role-permission-matrix.json:
- accounts: role login credentials
- tests: endpoint/method/path and allowed role list

No code change required for normal matrix updates.
