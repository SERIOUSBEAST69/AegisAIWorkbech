param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$OutCsv = "./docs/account-duty-segregation-results.csv",
  [string]$OutJson = "./docs/account-duty-segregation-summary.json",
  [string]$ClientIngressToken = "",
  [string]$ClientCompanyId = "1"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ClientIngressToken)) {
  if (-not [string]::IsNullOrWhiteSpace($env:AEGIS_CLIENT_TOKEN)) {
    $ClientIngressToken = [string]$env:AEGIS_CLIENT_TOKEN
  } elseif (-not [string]::IsNullOrWhiteSpace($env:CLIENT_INGRESS_TOKEN)) {
    $ClientIngressToken = [string]$env:CLIENT_INGRESS_TOKEN
  } else {
    $ClientIngressToken = 'aegis-client-ingress-local-dev'
  }
}

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
    [hashtable]$Headers,
    $Body = $null,
    [hashtable]$ExtraHeaders = $null
  )

  $merged = @{}
  if ($Headers) { foreach ($k in $Headers.Keys) { $merged[$k] = $Headers[$k] } }
  if ($ExtraHeaders) { foreach ($k in $ExtraHeaders.Keys) { $merged[$k] = $ExtraHeaders[$k] } }

  try {
    $verb = $Method.ToUpperInvariant()
    if ($verb -in @('POST', 'PUT', 'PATCH')) {
      $payload = if ($null -eq $Body) { '{}' } else { ($Body | ConvertTo-Json -Depth 10) }
      $resp = Invoke-RestMethod -Method $Method -Uri $Uri -Headers $merged -ContentType 'application/json' -Body $payload
    } else {
      $resp = Invoke-RestMethod -Method $Method -Uri $Uri -Headers $merged
    }
    return [pscustomobject]@{
      HttpStatus = 200
      ResponseCode = if ($resp.PSObject.Properties.Name -contains 'code') { [string]$resp.code } else { '200' }
      Message = if ($resp.PSObject.Properties.Name -contains 'msg') { [string]$resp.msg } else { 'success' }
      Data = if ($resp.PSObject.Properties.Name -contains 'data') { $resp.data } else { $resp }
    }
  } catch {
    $status = 500
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) { $status = [int]$_.Exception.Response.StatusCode }
    $errorBody = Read-ErrorBody -Exception $_.Exception
    $code = if ($errorBody -and $errorBody.code) { [string]$errorBody.code } else { [string]$status }
    $msg = if ($errorBody -and $errorBody.msg) { [string]$errorBody.msg } else { [string]$_.Exception.Message }
    return [pscustomobject]@{ HttpStatus = $status; ResponseCode = $code; Message = $msg; Data = $null }
  }
}

function Get-AuthHeaders {
  param([string]$Token)
  if ([string]::IsNullOrWhiteSpace($Token)) { return @{} }
  return @{ Authorization = "Bearer $Token" }
}

function Test-AccessDenied {
  param([object]$Result)
  return ($Result.HttpStatus -eq 401 -or $Result.HttpStatus -eq 403 -or $Result.ResponseCode -eq '40100' -or $Result.ResponseCode -eq '40300')
}

function Resolve-Actual {
  param([object]$Result)
  if (Test-AccessDenied -Result $Result) { return 'deny' }
  if ($Result.ResponseCode -eq '20000') { return 'allow' }
  return 'error'
}

function Add-CheckResult {
  param(
    [string]$Category,
    [string]$Check,
    [string]$Account,
    [string]$Expected,
    [object]$Result,
    [System.Collections.Generic.List[object]]$Rows
  )

  $actual = Resolve-Actual -Result $Result
  $passed = ($Expected -eq $actual)
  $rows.Add([pscustomobject]@{
    Category = $Category
    Check = $Check
    Account = $Account
    Expected = $Expected
    Actual = $actual
    Passed = $passed
    HttpStatus = $Result.HttpStatus
    ResponseCode = $Result.ResponseCode
    Message = $Result.Message
  }) | Out-Null
}

$accounts = @(
  @{ name = 'admin'; pwd = 'admin' },
  @{ name = 'admin_reviewer'; pwd = 'admin' },
  @{ name = 'admin_ops'; pwd = 'admin' },
  @{ name = 'secops'; pwd = 'Passw0rd!' },
  @{ name = 'secops_2'; pwd = 'Passw0rd!' },
  @{ name = 'secops_3'; pwd = 'Passw0rd!' },
  @{ name = 'dataadmin'; pwd = 'Passw0rd!' },
  @{ name = 'dataadmin_2'; pwd = 'Passw0rd!' },
  @{ name = 'dataadmin_3'; pwd = 'Passw0rd!' },
  @{ name = 'bizowner'; pwd = 'Passw0rd!' },
  @{ name = 'bizowner_2'; pwd = 'Passw0rd!' },
  @{ name = 'bizowner_3'; pwd = 'Passw0rd!' },
  @{ name = 'aibuilder'; pwd = 'Passw0rd!' },
  @{ name = 'aibuilder_2'; pwd = 'Passw0rd!' },
  @{ name = 'aibuilder_3'; pwd = 'Passw0rd!' },
  @{ name = 'employee1'; pwd = 'Passw0rd!' },
  @{ name = 'employee2'; pwd = 'Passw0rd!' },
  @{ name = 'employee3'; pwd = 'Passw0rd!' }
)

$tokens = @{}
foreach ($acc in $accounts) {
  $login = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/auth/login" -Headers @{} -Body @{ username = $acc.name; password = $acc.pwd }
  if ($login.ResponseCode -eq '20000' -and $login.Data -and $login.Data.token) {
    $tokens[$acc.name] = [string]$login.Data.token
  } else {
    $tokens[$acc.name] = $null
  }
}

$rows = New-Object System.Collections.Generic.List[object]
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$statusApprove = 'approved'
$statusReject = 'rejected'

# Discover a valid role id to avoid false-positive validation errors.
$adminHeaders = Get-AuthHeaders -Token $tokens['admin']
$roleProbe = Invoke-ApiSafe -Method 'GET' -Uri "$BaseUrl/api/user/list" -Headers $adminHeaders
$validRoleId = 1
$existingEmployeeUsername = 'employee'
if ($roleProbe.ResponseCode -eq '20000' -and $roleProbe.Data) {
  $employeeRef = @($roleProbe.Data) | Where-Object { $_.username -eq 'employee1' -or $_.username -eq 'employee' } | Select-Object -First 1
  if ($employeeRef) {
    if ($employeeRef.roleId) { $validRoleId = $employeeRef.roleId }
    if ($employeeRef.username) { $existingEmployeeUsername = [string]$employeeRef.username }
  }
}

# 1) Governance admin duty segregation
$invite = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/auth/invite-code/create" -Headers $adminHeaders -Body @{ expireHours = 24 }
$inviteCode = if ($invite.ResponseCode -eq '20000') { [string]$invite.Data.inviteCode } else { '' }

$pendingUserA = "duty_pending_a_$stamp"
$pendingUserB = "duty_pending_b_$stamp"
if (-not [string]::IsNullOrWhiteSpace($inviteCode)) {
  foreach ($u in @($pendingUserA, $pendingUserB)) {
    [void](Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/auth/register" -Headers @{} -Body @{
      username = $u
      password = 'Passw0rd!'
      confirmPassword = 'Passw0rd!'
      realName = 'Duty Pending User'
      nickname = 'DutyPending'
      roleCode = 'EMPLOYEE'
      inviteCode = $inviteCode
      organizationType = 'enterprise'
      department = 'DutyTest'
      phone = '139' + (Get-Random -Minimum 10000000 -Maximum 99999999)
      email = "$u@test.local"
      loginType = 'password'
      accountType = 'real'
    })
  }
}

$userList = Invoke-ApiSafe -Method 'GET' -Uri "$BaseUrl/api/user/list" -Headers $adminHeaders
$pendingAId = $null
$pendingBId = $null
if ($userList.ResponseCode -eq '20000' -and $userList.Data) {
  $targetA = @($userList.Data) | Where-Object { $_.username -eq $pendingUserA } | Select-Object -First 1
  $targetB = @($userList.Data) | Where-Object { $_.username -eq $pendingUserB } | Select-Object -First 1
  $pendingAId = if ($targetA) { $targetA.id } else { $null }
  $pendingBId = if ($targetB) { $targetB.id } else { $null }
}

$reviewerApprove = if ($pendingAId) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/user/approve" -Headers (Get-AuthHeaders $tokens['admin_reviewer']) -Body @{ id = $pendingAId }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: pending user A missing'; Data = $null }
}
Add-CheckResult -Category 'governance-admin' -Check 'reviewer_can_approve' -Account 'admin_reviewer' -Expected 'allow' -Result $reviewerApprove -Rows $rows

$opsApprove = if ($pendingBId) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/user/approve" -Headers (Get-AuthHeaders $tokens['admin_ops']) -Body @{ id = $pendingBId }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: pending user B missing'; Data = $null }
}
Add-CheckResult -Category 'governance-admin' -Check 'ops_cannot_approve' -Account 'admin_ops' -Expected 'deny' -Result $opsApprove -Rows $rows

$opsRegister = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/user/register" -Headers (Get-AuthHeaders $tokens['admin_ops']) -Body @{
  username = "duty_ops_write_$stamp"
  password = 'Passw0rd!'
  roleId = $validRoleId
  accountType = 'real'
  accountStatus = 'active'
  department = 'DutyTest'
}
Add-CheckResult -Category 'governance-admin' -Check 'ops_can_write' -Account 'admin_ops' -Expected 'allow' -Result $opsRegister -Rows $rows

$reviewerRegister = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/user/register" -Headers (Get-AuthHeaders $tokens['admin_reviewer']) -Body @{
  username = "duty_reviewer_write_$stamp"
  password = 'Passw0rd!'
  roleId = $validRoleId
  accountType = 'real'
  accountStatus = 'active'
  department = 'DutyTest'
}
Add-CheckResult -Category 'governance-admin' -Check 'reviewer_cannot_write' -Account 'admin_reviewer' -Expected 'deny' -Result $reviewerRegister -Rows $rows

# 2) SecOps duty segregation
function New-SecurityEventId {
  param([string]$Employee)
  $ingressHeaders = @{ 'X-Client-Token' = $ClientIngressToken }
  if (-not [string]::IsNullOrWhiteSpace($ClientCompanyId)) {
    $ingressHeaders['X-Company-Id'] = $ClientCompanyId
  }
  $report = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/security/events/report" -Headers @{} -Body @{
    eventType = 'EXFILTRATION'
    filePath = '/tmp/duty.txt'
    targetAddr = '203.0.113.10'
    employeeId = $Employee
    hostname = 'duty-host'
    fileSize = 1024
    severity = 'high'
    status = 'pending'
    source = 'agent'
  } -ExtraHeaders $ingressHeaders
  if ($report.ResponseCode -eq '20000' -and $report.Data) { return $report.Data.id }
  return $null
}

$eventForSecops2 = New-SecurityEventId -Employee $existingEmployeeUsername
$blockDenied = if ($eventForSecops2) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/security/block" -Headers (Get-AuthHeaders $tokens['secops_2']) -Body @{ id = $eventForSecops2 }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: secops_2 event missing'; Data = $null }
}
Add-CheckResult -Category 'secops' -Check 'secops2_cannot_block' -Account 'secops_2' -Expected 'deny' -Result $blockDenied -Rows $rows

$ignoreAllowed = if ($eventForSecops2) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/security/ignore" -Headers (Get-AuthHeaders $tokens['secops_2']) -Body @{ id = $eventForSecops2 }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: secops_2 event missing'; Data = $null }
}
Add-CheckResult -Category 'secops' -Check 'secops2_can_ignore' -Account 'secops_2' -Expected 'allow' -Result $ignoreAllowed -Rows $rows

$eventForSecops3 = New-SecurityEventId -Employee $existingEmployeeUsername
$ignoreDenied = if ($eventForSecops3) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/security/ignore" -Headers (Get-AuthHeaders $tokens['secops_3']) -Body @{ id = $eventForSecops3 }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: secops_3 event missing'; Data = $null }
}
Add-CheckResult -Category 'secops' -Check 'secops3_cannot_ignore' -Account 'secops_3' -Expected 'deny' -Result $ignoreDenied -Rows $rows

$blockAllowed = if ($eventForSecops3) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/security/block" -Headers (Get-AuthHeaders $tokens['secops_3']) -Body @{ id = $eventForSecops3 }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: secops_3 event missing'; Data = $null }
}
Add-CheckResult -Category 'secops' -Check 'secops3_can_block' -Account 'secops_3' -Expected 'allow' -Result $blockAllowed -Rows $rows

# 3) Data admin duty segregation
$assetName = "duty_asset_$stamp"
[void](Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/data-asset/register" -Headers (Get-AuthHeaders $tokens['dataadmin']) -Body @{
  name = $assetName
  type = 'table'
  sensitivityLevel = 'medium'
  location = '/tmp/duty_asset.csv'
  description = 'duty segregation asset'
})

$assetList = Invoke-ApiSafe -Method 'GET' -Uri "$BaseUrl/api/data-asset/list?name=$assetName" -Headers (Get-AuthHeaders $tokens['dataadmin'])
$assetId = $null
if ($assetList.ResponseCode -eq '20000' -and $assetList.Data) {
  $asset = @($assetList.Data) | Where-Object { $_.name -eq $assetName } | Select-Object -First 1
  $assetId = if ($asset) { $asset.id } else { $null }
}

if ($assetId) {
  $deleteDenied = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/data-asset/delete" -Headers (Get-AuthHeaders $tokens['dataadmin_2']) -Body @{ id = $assetId }
  Add-CheckResult -Category 'data-admin' -Check 'dataadmin2_cannot_delete' -Account 'dataadmin_2' -Expected 'deny' -Result $deleteDenied -Rows $rows

  $updateAllowed = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/data-asset/update" -Headers (Get-AuthHeaders $tokens['dataadmin_2']) -Body @{
    id = $assetId
    name = "$assetName-updated"
    type = 'table'
    sensitivityLevel = 'medium'
    location = '/tmp/duty_asset.csv'
    description = 'updated by dataadmin_2'
  }
  Add-CheckResult -Category 'data-admin' -Check 'dataadmin2_can_update' -Account 'dataadmin_2' -Expected 'allow' -Result $updateAllowed -Rows $rows
}

$dataadmin3WriteDenied = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/data-asset/register" -Headers (Get-AuthHeaders $tokens['dataadmin_3']) -Body @{
  name = "duty_asset_denied_$stamp"
  type = 'table'
  sensitivityLevel = 'medium'
  location = '/tmp/duty_asset_denied.csv'
  description = 'should deny'
}
Add-CheckResult -Category 'data-admin' -Check 'dataadmin3_cannot_write' -Account 'dataadmin_3' -Expected 'deny' -Result $dataadmin3WriteDenied -Rows $rows

# 4) Business owner duty segregation
function New-BusinessApprovalId {
  param([string]$who)
  $apply = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/approval/apply" -Headers (Get-AuthHeaders $tokens[$who]) -Body @{ assetId = $null; reason = "[BUSINESS] duty approval $stamp" }
  if ($apply.ResponseCode -ne '20000') { return $null }
  $list = Invoke-ApiSafe -Method 'GET' -Uri "$BaseUrl/api/approval/page?page=1&pageSize=10" -Headers (Get-AuthHeaders $tokens[$who])
  if ($list.ResponseCode -ne '20000' -or -not $list.Data) { return $null }
  $item = @($list.Data.list) | Where-Object { $_.reason -like '*duty approval*' } | Sort-Object id -Descending | Select-Object -First 1
  if ($item) { return $item.id }
  return $null
}

$approvalForBz2 = New-BusinessApprovalId -who 'bizowner'
$rejectDenied = if ($approvalForBz2) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/approval/reject" -Headers (Get-AuthHeaders $tokens['bizowner_2']) -Body @{ requestId = $approvalForBz2; status = $statusReject }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: bizowner request missing'; Data = $null }
}
Add-CheckResult -Category 'business-owner' -Check 'bizowner2_cannot_reject' -Account 'bizowner_2' -Expected 'deny' -Result $rejectDenied -Rows $rows

$approveAllowed = if ($approvalForBz2) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/approval/approve" -Headers (Get-AuthHeaders $tokens['bizowner_2']) -Body @{ requestId = $approvalForBz2; status = $statusApprove }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: bizowner request missing'; Data = $null }
}
Add-CheckResult -Category 'business-owner' -Check 'bizowner2_can_approve' -Account 'bizowner_2' -Expected 'allow' -Result $approveAllowed -Rows $rows

$approvalForBz3 = New-BusinessApprovalId -who 'bizowner'
$approveDenied = if ($approvalForBz3) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/approval/approve" -Headers (Get-AuthHeaders $tokens['bizowner_3']) -Body @{ requestId = $approvalForBz3; status = $statusApprove }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: bizowner request missing'; Data = $null }
}
Add-CheckResult -Category 'business-owner' -Check 'bizowner3_cannot_approve' -Account 'bizowner_3' -Expected 'deny' -Result $approveDenied -Rows $rows

$rejectAllowed = if ($approvalForBz3) {
  Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/approval/reject" -Headers (Get-AuthHeaders $tokens['bizowner_3']) -Body @{ requestId = $approvalForBz3; status = $statusReject }
} else {
  [pscustomobject]@{ HttpStatus = 500; ResponseCode = '500'; Message = 'setup_failed: bizowner request missing'; Data = $null }
}
Add-CheckResult -Category 'business-owner' -Check 'bizowner3_can_reject' -Account 'bizowner_3' -Expected 'allow' -Result $rejectAllowed -Rows $rows

# 5) AI builder duty segregation
$anomalyPayload = @{ employee_id = 'employee1'; department = '研发'; ai_service = 'ChatGPT'; hour_of_day = 10; day_of_week = 2; message_length = 180; topic_code = 1; session_duration_min = 8; is_new_service = 0 }

$aibuilder2EventsDenied = Invoke-ApiSafe -Method 'GET' -Uri "$BaseUrl/api/anomaly/events" -Headers (Get-AuthHeaders $tokens['aibuilder_2'])
Add-CheckResult -Category 'ai-builder' -Check 'aibuilder2_cannot_events' -Account 'aibuilder_2' -Expected 'deny' -Result $aibuilder2EventsDenied -Rows $rows

$aibuilder2CheckAllowed = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/anomaly/check" -Headers (Get-AuthHeaders $tokens['aibuilder_2']) -Body $anomalyPayload
Add-CheckResult -Category 'ai-builder' -Check 'aibuilder2_can_check' -Account 'aibuilder_2' -Expected 'allow' -Result $aibuilder2CheckAllowed -Rows $rows

$aibuilder3CheckDenied = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/anomaly/check" -Headers (Get-AuthHeaders $tokens['aibuilder_3']) -Body $anomalyPayload
Add-CheckResult -Category 'ai-builder' -Check 'aibuilder3_cannot_check' -Account 'aibuilder_3' -Expected 'deny' -Result $aibuilder3CheckDenied -Rows $rows

$aibuilder3EventsAllowed = Invoke-ApiSafe -Method 'GET' -Uri "$BaseUrl/api/anomaly/events" -Headers (Get-AuthHeaders $tokens['aibuilder_3'])
Add-CheckResult -Category 'ai-builder' -Check 'aibuilder3_can_events' -Account 'aibuilder_3' -Expected 'allow' -Result $aibuilder3EventsAllowed -Rows $rows

# 6) Employee duty segregation
$employee1DeleteAllowed = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/subject-request/create" -Headers (Get-AuthHeaders $tokens['employee1']) -Body @{ type = 'delete'; comment = 'employee1 delete request' }
Add-CheckResult -Category 'employee' -Check 'employee1_can_delete_request' -Account 'employee1' -Expected 'allow' -Result $employee1DeleteAllowed -Rows $rows

$employee2DeleteDenied = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/subject-request/create" -Headers (Get-AuthHeaders $tokens['employee2']) -Body @{ type = 'delete'; comment = 'employee2 delete request' }
Add-CheckResult -Category 'employee' -Check 'employee2_cannot_delete_request' -Account 'employee2' -Expected 'deny' -Result $employee2DeleteDenied -Rows $rows

$employee3AnyDenied = Invoke-ApiSafe -Method 'POST' -Uri "$BaseUrl/api/subject-request/create" -Headers (Get-AuthHeaders $tokens['employee3']) -Body @{ type = 'access'; comment = 'employee3 access request' }
Add-CheckResult -Category 'employee' -Check 'employee3_view_only' -Account 'employee3' -Expected 'deny' -Result $employee3AnyDenied -Rows $rows

# Output summary
$mismatch = @($rows | Where-Object { -not $_.Passed })
$serverError = @($rows | Where-Object { $_.HttpStatus -ge 500 -or $_.ResponseCode -eq '50000' })
$loginFailed = @($tokens.Keys | Where-Object { -not $tokens[$_] })

$summary = [pscustomobject]@{
  generatedAt = (Get-Date).ToString('s')
  baseUrl = $BaseUrl
  totalChecks = $rows.Count
  mismatchCount = $mismatch.Count
  serverErrorCount = $serverError.Count
  loginFailedAccounts = $loginFailed
}

$csvDir = Split-Path -Parent $OutCsv
$jsonDir = Split-Path -Parent $OutJson
if ($csvDir -and -not (Test-Path $csvDir)) { New-Item -ItemType Directory -Path $csvDir -Force | Out-Null }
if ($jsonDir -and -not (Test-Path $jsonDir)) { New-Item -ItemType Directory -Path $jsonDir -Force | Out-Null }

$rows | Export-Csv -Path $OutCsv -NoTypeInformation -Encoding UTF8
$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $OutJson -Encoding UTF8

Write-Output '=== SUMMARY ==='
$summary | ConvertTo-Json -Depth 8
Write-Output '=== MISMATCH ==='
if ($mismatch.Count -eq 0) {
  Write-Output 'NONE'
} else {
  $mismatch | Select-Object Category,Check,Account,Expected,Actual,HttpStatus,ResponseCode | Format-Table | Out-String | Write-Output
}
Write-Output '=== SERVER_ERRORS ==='
if ($serverError.Count -eq 0) {
  Write-Output 'NONE'
} else {
  $serverError | Select-Object Category,Check,Account,HttpStatus,ResponseCode,Message | Format-Table | Out-String | Write-Output
}
