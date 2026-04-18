$ErrorActionPreference = "Stop"
$BaseUrl = "http://localhost:8080"
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$outJson = "./docs/client-full-check-$ts.json"

function Read-ErrorJson {
  param($Exception)
  try {
    if (-not $Exception.Response) { return $null }
    $reader = New-Object System.IO.StreamReader($Exception.Response.GetResponseStream())
    $raw = $reader.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
    return $raw | ConvertFrom-Json
  } catch {
    return $null
  }
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    [string]$Token = $null,
    [object]$Body = $null
  )

  $headers = @{}
  if ($Token) { $headers.Authorization = "Bearer $Token" }
  $uri = "$BaseUrl$Path"

  try {
    if ($null -ne $Body) {
      $payload = $Body | ConvertTo-Json -Depth 20
      $resp = Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body $payload
    } else {
      $resp = Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
    }
    return [pscustomobject]@{
      ok = $true
      httpStatus = 200
      code = [string]$resp.code
      msg = [string]$resp.msg
      data = $resp.data
    }
  } catch {
    $status = 500
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $status = [int]$_.Exception.Response.StatusCode
    }
    $err = Read-ErrorJson -Exception $_.Exception
    return [pscustomobject]@{
      ok = $false
      httpStatus = $status
      code = if ($err -and $err.code) { [string]$err.code } else { [string]$status }
      msg = if ($err -and $err.msg) { [string]$err.msg } else { [string]$_.Exception.Message }
      data = if ($err) { $err.data } else { $null }
    }
  }
}

$profiles = @(
  @{ role = "ADMIN"; candidates = @("admin"); passwords = @("admin") },
  @{ role = "EXECUTIVE"; candidates = @("exec.demo", "executive"); passwords = @("Passw0rd!", "admin") },
  @{ role = "SECOPS"; candidates = @("sec01", "secops.demo", "secops"); passwords = @("Passw0rd!", "admin") },
  @{ role = "DATA_ADMIN"; candidates = @("data01", "data.demo", "dataadmin"); passwords = @("Passw0rd!", "admin") },
  @{ role = "AUDIT"; candidates = @("audit01", "audit"); passwords = @("auditpass", "Passw0rd!", "admin") }
)

$loginResults = @()
$tokensByRole = @{}
$usersByRole = @{}

foreach ($p in $profiles) {
  $success = $false
  foreach ($u in $p.candidates) {
    if ($success) { break }
    foreach ($pw in $p.passwords) {
      $resp = Invoke-Api -Method "POST" -Path "/api/auth/login" -Body @{ username = $u; password = $pw }
      if ($resp.code -eq "20000" -and $resp.data -and $resp.data.token) {
        $tokensByRole[$p.role] = [string]$resp.data.token
        $usersByRole[$p.role] = [string]$u
        $loginResults += [pscustomobject]@{ role = $p.role; username = $u; passwordUsed = $pw; ok = $true; code = $resp.code; msg = $resp.msg }
        $success = $true
        break
      }
    }
  }
  if (-not $success) {
    $tokensByRole[$p.role] = $null
    $usersByRole[$p.role] = $null
    $loginResults += [pscustomobject]@{ role = $p.role; username = $null; passwordUsed = $null; ok = $false; code = "LOGIN_FAILED"; msg = "all credential attempts failed" }
  }
}

$meChecks = @()
foreach ($p in $profiles) {
  $token = $tokensByRole[$p.role]
  if (-not $token) {
    $meChecks += [pscustomobject]@{ role = $p.role; username = $null; ok = $false; code = "SKIPPED"; msg = "login failed"; roleCode = $null; companyId = $null }
    continue
  }
  $me = Invoke-Api -Method "GET" -Path "/api/auth/me" -Token $token
  $roleCode = $null
  $companyId = $null
  if ($me.data -and $me.data.user) {
    $roleCode = [string]$me.data.user.roleCode
    $companyId = [string]$me.data.user.companyId
  }
  $meChecks += [pscustomobject]@{
    role = $p.role
    username = $usersByRole[$p.role]
    ok = ($me.code -eq "20000")
    code = $me.code
    msg = $me.msg
    roleCode = $roleCode
    companyId = $companyId
  }
}

$permChecks = @(
  @{ name = "DASHBOARD_WORKBENCH"; method = "GET"; path = "/api/dashboard/workbench"; allow = @("ADMIN","EXECUTIVE","SECOPS","DATA_ADMIN","AUDIT") },
  @{ name = "USER_LIST"; method = "GET"; path = "/api/user/list"; allow = @("ADMIN") },
  @{ name = "DATA_ASSET_LIST"; method = "GET"; path = "/api/data-asset/list"; allow = @("ADMIN","DATA_ADMIN") },
  @{ name = "AUDIT_LOG_SEARCH"; method = "GET"; path = "/api/audit-log/search"; allow = @("ADMIN","AUDIT") },
  @{ name = "SECURITY_EVENTS"; method = "GET"; path = "/api/security/events?page=1&pageSize=5"; allow = @("ADMIN","SECOPS") },
  @{ name = "AI_ADVERSARIAL_META"; method = "GET"; path = "/api/ai/adversarial/meta"; allow = @("ADMIN","SECOPS") },
  @{ name = "PRIVACY_CONFIG_GET"; method = "GET"; path = "/api/privacy/config"; allow = @("ADMIN","SECOPS") },
  @{ name = "PRIVACY_EVENTS"; method = "GET"; path = "/api/privacy/events?page=1&pageSize=5"; allow = @("ADMIN","SECOPS","AUDIT") },
  @{ name = "ANOMALY_STATUS"; method = "GET"; path = "/api/anomaly/status"; allow = @("ADMIN","SECOPS","AUDIT") }
)

$permissionRows = @()
foreach ($check in $permChecks) {
  foreach ($p in $profiles) {
    $token = $tokensByRole[$p.role]
    $expected = @($check.allow) -contains $p.role
    if (-not $token) {
      $permissionRows += [pscustomobject]@{
        endpoint = $check.name; method = $check.method; path = $check.path; role = $p.role; username = $usersByRole[$p.role]
        expectedAllowed = $expected; actualAllowed = $false; httpStatus = 401; responseCode = "LOGIN_FAILED"; message = "login failed"
      }
      continue
    }
    $resp = Invoke-Api -Method $check.method -Path $check.path -Token $token
    $denied = ($resp.httpStatus -eq 401 -or $resp.httpStatus -eq 403 -or $resp.code -eq "40100" -or $resp.code -eq "40300")
    $actual = -not $denied
    $permissionRows += [pscustomobject]@{
      endpoint = $check.name; method = $check.method; path = $check.path; role = $p.role; username = $usersByRole[$p.role]
      expectedAllowed = $expected; actualAllowed = $actual; httpStatus = $resp.httpStatus; responseCode = $resp.code; message = $resp.msg
    }
  }
}

$adminToken = $tokensByRole["ADMIN"]
$secopsToken = $tokensByRole["SECOPS"]
$executiveToken = $tokensByRole["EXECUTIVE"]

$detectionCases = @()
if ($adminToken) {
  $clientListResp = Invoke-Api -Method "GET" -Path "/api/client/list?page=1&pageSize=5" -Token $adminToken
  $firstClientId = $null
  if ($clientListResp.code -eq "20000" -and $clientListResp.data) {
    $firstClient = @($clientListResp.data) | Select-Object -First 1
    if ($firstClient) { $firstClientId = [string]$firstClient.clientId }
  }

  $detectionCases += [pscustomobject]@{ name = "AI_MONITOR_SUMMARY"; resp = (Invoke-Api -Method "GET" -Path "/api/ai/monitor/summary" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "AI_MONITOR_TREND"; resp = (Invoke-Api -Method "GET" -Path "/api/ai/monitor/trend?days=7" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "AI_MONITOR_BOOTSTRAP_TRACE"; resp = (Invoke-Api -Method "POST" -Path "/api/ai/monitor/bootstrap-trace" -Token $adminToken -Body @{ sampleSize = 12 }) }
  $detectionCases += [pscustomobject]@{ name = "AI_MONITOR_VERIFY_CHAIN"; resp = (Invoke-Api -Method "GET" -Path "/api/ai/monitor/logs/verify-chain" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "AI_RISK_LIST"; resp = (Invoke-Api -Method "GET" -Path "/api/ai-risk/list" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "AI_ADVERSARIAL_META_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/ai/adversarial/meta" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "AI_ADVERSARIAL_RUN_ADMIN"; resp = (Invoke-Api -Method "POST" -Path "/api/ai/adversarial/run" -Token $adminToken -Body @{ scenario = "real-threat-check"; rounds = 6 }) }
  $detectionCases += [pscustomobject]@{ name = "ANOMALY_STATUS_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/anomaly/status" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "ANOMALY_CHECK_ADMIN"; resp = (Invoke-Api -Method "POST" -Path "/api/anomaly/check" -Token $adminToken -Body @{ employee_id = "admin"; department = "治理"; ai_service = "ChatGPT"; hour_of_day = 10; day_of_week = 2; message_length = 420; topic_code = 0; session_duration_min = 22; is_new_service = 0 }) }
  $detectionCases += [pscustomobject]@{ name = "ANOMALY_EVENTS_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/anomaly/events?page=1&pageSize=5" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "PRIVACY_EVENTS_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/privacy/events?page=1&pageSize=5" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "PRIVACY_CONFIG_GET_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/privacy/config" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "PRIVACY_CONFIG_UPDATE_ADMIN"; resp = (Invoke-Api -Method "POST" -Path "/api/privacy/config" -Token $adminToken -Body @{ syncIntervalSec = 60; enabled = $true }) }
  $detectionCases += [pscustomobject]@{ name = "SECURITY_EVENTS_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/security/events?page=1&pageSize=5" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "SECURITY_STATS_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/security/stats" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "SECURITY_RULES_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/security/rules" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "CLIENT_LIST_ADMIN"; resp = $clientListResp }
  if ($firstClientId) {
    $detectionCases += [pscustomobject]@{ name = "CLIENT_HISTORY_ADMIN"; resp = (Invoke-Api -Method "GET" -Path ("/api/client/history?clientId=" + $firstClientId) -Token $adminToken) }
  } else {
    $detectionCases += [pscustomobject]@{ name = "CLIENT_HISTORY_ADMIN"; resp = [pscustomobject]@{ ok = $false; httpStatus = 500; code = "NO_CLIENT_ID"; msg = "client list empty"; data = $null } }
  }
  $detectionCases += [pscustomobject]@{ name = "CLIENT_STATS_ADMIN"; resp = (Invoke-Api -Method "GET" -Path "/api/client/stats?days=7" -Token $adminToken) }
  $detectionCases += [pscustomobject]@{ name = "RELIABILITY_DRILL_ADMIN"; resp = (Invoke-Api -Method "POST" -Path "/api/award/reliability/drill/run" -Token $adminToken -Body @{ scenario = "latency-and-failure-observe"; targetPath = "/api/auth/registration-options"; injectPath = "/api/non-existent-reliability-probe"; probeCount = 4 }) }
}
if ($secopsToken) {
  $detectionCases += [pscustomobject]@{ name = "AI_ADVERSARIAL_META_SECOPS"; resp = (Invoke-Api -Method "GET" -Path "/api/ai/adversarial/meta" -Token $secopsToken) }
}
if ($executiveToken) {
  $detectionCases += [pscustomobject]@{ name = "AI_ADVERSARIAL_META_EXECUTIVE_EXPECT_DENY"; resp = (Invoke-Api -Method "GET" -Path "/api/ai/adversarial/meta" -Token $executiveToken) }
}

$detectionRows = foreach ($d in $detectionCases) {
  $expectedDeny = ($d.name -eq "AI_ADVERSARIAL_META_EXECUTIVE_EXPECT_DENY")
  $isDenied = ($d.resp.httpStatus -eq 401 -or $d.resp.httpStatus -eq 403 -or $d.resp.code -eq "40100" -or $d.resp.code -eq "40300")
  $passed = if ($expectedDeny) { $isDenied } else { $d.resp.code -eq "20000" }
  [pscustomobject]@{
    name = $d.name
    ok = $passed
    httpStatus = $d.resp.httpStatus
    responseCode = $d.resp.code
    message = $d.resp.msg
  }
}

$permissionMismatch = @($permissionRows | Where-Object { $_.expectedAllowed -ne $_.actualAllowed })
$failedLogins = @($loginResults | Where-Object { -not $_.ok })
$failedDetections = @($detectionRows | Where-Object { -not $_.ok })

$summary = [pscustomobject]@{
  generatedAt = (Get-Date).ToString("s")
  baseUrl = $BaseUrl
  identitiesUnderTest = @($profiles.role)
  login = [pscustomobject]@{
    total = $loginResults.Count
    success = @($loginResults | Where-Object { $_.ok }).Count
    failed = $failedLogins.Count
  }
  permission = [pscustomobject]@{
    totalChecks = $permissionRows.Count
    mismatchCount = $permissionMismatch.Count
  }
  detection = [pscustomobject]@{
    totalCases = $detectionRows.Count
    passed = @($detectionRows | Where-Object { $_.ok }).Count
    failed = $failedDetections.Count
  }
}

$report = [pscustomobject]@{
  summary = $summary
  loginResults = $loginResults
  meChecks = $meChecks
  permissionChecks = $permissionRows
  permissionMismatches = $permissionMismatch
  detectionChecks = $detectionRows
  detectionFailures = $failedDetections
}

$report | ConvertTo-Json -Depth 20 | Set-Content -Path $outJson -Encoding utf8
Write-Output ("REPORT_FILE=" + $outJson)
$summary | ConvertTo-Json -Depth 10

