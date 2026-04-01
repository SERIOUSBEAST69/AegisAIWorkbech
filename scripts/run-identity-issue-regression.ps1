param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$MatrixFile = "./scripts/role-permission-matrix.json",
  [string]$OutCsv = "./docs/identity-issue-regression-results.csv",
  [string]$OutJson = "./docs/identity-issue-regression-summary.json"
)

$ErrorActionPreference = "Stop"

function Read-ErrorBody {
  param($Exception)
  try {
    $resp = $Exception.Response
    if (-not $resp) { return $null }
    $reader = New-Object IO.StreamReader($resp.GetResponseStream())
    $raw = $reader.ReadToEnd()
    if (-not $raw) { return $null }
    return $raw | ConvertFrom-Json
  } catch {
    return $null
  }
}

function Invoke-ApiSafe {
  param(
    [string]$Method,
    [string]$Uri,
    [hashtable]$Headers
  )

  try {
    if ($Method -eq "POST") {
      $resp = Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType "application/json" -Body "{}"
    } else {
      $resp = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers
    }
    return [pscustomobject]@{
      HttpStatus = 200
      ResponseCode = [string]$resp.code
      Message = [string]$resp.msg
    }
  } catch {
    $status = 500
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $status = [int]$_.Exception.Response.StatusCode
    }
    $errBody = Read-ErrorBody -Exception $_.Exception
    $code = if ($errBody -and $errBody.code) { [string]$errBody.code } else { [string]$status }
    $msg = if ($errBody -and $errBody.msg) { [string]$errBody.msg } else { [string]$_.Exception.Message }
    return [pscustomobject]@{
      HttpStatus = $status
      ResponseCode = $code
      Message = $msg
    }
  }
}

if (-not (Test-Path $MatrixFile)) {
  throw "Matrix file not found: $MatrixFile"
}

$matrix = Get-Content $MatrixFile -Raw | ConvertFrom-Json
$accounts = @($matrix.accounts)

$checks = @(
  @{ name = "OBS_WORKBENCH"; method = "GET"; path = "/api/dashboard/workbench"; allow = @("ADMIN","EXECUTIVE","SECOPS","DATA_ADMIN","AI_BUILDER","BUSINESS_OWNER","EMPLOYEE") },
  @{ name = "OBS_ALERT_STATS"; method = "GET"; path = "/api/alert-center/stats"; allow = @("ADMIN","EXECUTIVE","SECOPS","DATA_ADMIN","AI_BUILDER","BUSINESS_OWNER","EMPLOYEE") },
  @{ name = "OBS_AI_SUMMARY"; method = "GET"; path = "/api/ai/monitor/summary"; allow = @("ADMIN","EXECUTIVE","SECOPS","DATA_ADMIN","AI_BUILDER","BUSINESS_OWNER","EMPLOYEE") },
  @{ name = "AI_RISK_LIST"; method = "GET"; path = "/api/ai-risk/list"; allow = @("ADMIN","EXECUTIVE","SECOPS","DATA_ADMIN","AI_BUILDER","BUSINESS_OWNER","EMPLOYEE") },
  @{ name = "SENSITIVE_SCAN_LIST"; method = "GET"; path = "/api/sensitive-scan/list"; allow = @("ADMIN","SECOPS","DATA_ADMIN") },
  @{ name = "APPROVAL_PAGE"; method = "GET"; path = "/api/approval/page?page=1&pageSize=5"; allow = @("ADMIN","DATA_ADMIN","BUSINESS_OWNER","EMPLOYEE") },
  @{ name = "SUBJECT_REQUEST_LIST"; method = "GET"; path = "/api/subject-request/list"; allow = @("ADMIN","DATA_ADMIN","BUSINESS_OWNER","EMPLOYEE") },
  @{ name = "SECURITY_EVENTS"; method = "GET"; path = "/api/security/events?page=1&pageSize=5"; allow = @("ADMIN","SECOPS") },
  @{ name = "ROLE_PERM_TREE"; method = "GET"; path = "/api/permissions/tree"; allow = @("ADMIN") },
  @{ name = "ROLES_LIST"; method = "GET"; path = "/api/roles?page=1&pageSize=10"; allow = @("ADMIN") }
)

$tokens = @{}
foreach ($acc in $accounts) {
  try {
    $loginPayload = @{ username = $acc.username; password = $acc.password } | ConvertTo-Json
    $loginResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $loginPayload
    if ([string]$loginResp.code -eq "20000" -and $loginResp.data.token) {
      $tokens[$acc.role] = [string]$loginResp.data.token
    } else {
      $tokens[$acc.role] = $null
    }
  } catch {
    $tokens[$acc.role] = $null
  }
}

$rows = New-Object System.Collections.Generic.List[object]
foreach ($check in $checks) {
  foreach ($acc in $accounts) {
    $expectedAllowed = @($check.allow) -contains [string]$acc.role
    $httpStatus = 401
    $responseCode = "40100"
    $message = "login_failed"

    if ($tokens[$acc.role]) {
      $headers = @{ Authorization = "Bearer $($tokens[$acc.role])" }
      $result = Invoke-ApiSafe -Method ([string]$check.method) -Uri "$BaseUrl$($check.path)" -Headers $headers
      $httpStatus = $result.HttpStatus
      $responseCode = $result.ResponseCode
      $message = $result.Message
    }

    $denied = ($httpStatus -eq 401 -or $httpStatus -eq 403 -or $responseCode -eq "40100" -or $responseCode -eq "40300")
    $actualAllowed = -not $denied

    $rows.Add([pscustomobject]@{
      Endpoint = [string]$check.name
      Method = [string]$check.method
      Path = [string]$check.path
      Role = [string]$acc.role
      Username = [string]$acc.username
      ExpectedAllowed = $expectedAllowed
      ActualAllowed = $actualAllowed
      HttpStatus = $httpStatus
      ResponseCode = [string]$responseCode
      Message = [string]$message
    }) | Out-Null
  }
}

$mismatch = $rows | Where-Object { $_.ExpectedAllowed -ne $_.ActualAllowed }
$serverError = $rows | Where-Object { $_.HttpStatus -ge 500 -or $_.ResponseCode -eq "50000" }

$summary = [pscustomobject]@{
  generatedAt = (Get-Date).ToString("s")
  baseUrl = $BaseUrl
  totalChecks = $rows.Count
  mismatchCount = @($mismatch).Count
  serverErrorCount = @($serverError).Count
  loginFailedRoles = @($accounts | Where-Object { -not $tokens[$_.role] } | ForEach-Object { $_.role })
}

$csvDir = Split-Path -Parent $OutCsv
$jsonDir = Split-Path -Parent $OutJson
if ($csvDir -and -not (Test-Path $csvDir)) { New-Item -ItemType Directory -Path $csvDir -Force | Out-Null }
if ($jsonDir -and -not (Test-Path $jsonDir)) { New-Item -ItemType Directory -Path $jsonDir -Force | Out-Null }

$rows | Export-Csv -Path $OutCsv -NoTypeInformation -Encoding UTF8
$summary | ConvertTo-Json -Depth 6 | Set-Content -Path $OutJson -Encoding UTF8

Write-Output "=== SUMMARY ==="
$summary | ConvertTo-Json -Depth 6
Write-Output "=== MISMATCH ==="
if (@($mismatch).Count -eq 0) {
  Write-Output "NONE"
} else {
  $mismatch | Select-Object Endpoint,Role,ExpectedAllowed,ActualAllowed,HttpStatus,ResponseCode | Format-Table | Out-String | Write-Output
}
Write-Output "=== SERVER_ERRORS ==="
if (@($serverError).Count -eq 0) {
  Write-Output "NONE"
} else {
  $serverError | Select-Object Endpoint,Role,HttpStatus,ResponseCode,Message | Format-Table | Out-String | Write-Output
}
