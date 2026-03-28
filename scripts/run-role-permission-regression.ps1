param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$MatrixFile = "./scripts/role-permission-matrix.json",
  [string]$OutCsv = "./docs/role-permission-e2e-results.csv",
  [string]$OutJson = "./docs/role-permission-e2e-summary.json"
)

$ErrorActionPreference = "Stop"

function Get-JsonCode {
  param([object]$Obj)
  if ($null -eq $Obj) { return $null }
  if ($Obj.PSObject.Properties.Name -contains "code") { return [string]$Obj.code }
  return $null
}

function Invoke-ApiSafe {
  param(
    [string]$Method,
    [string]$Uri,
    [hashtable]$Headers
  )

  try {
    $verb = ([string]$Method).ToUpperInvariant()
    if ($verb -in @('POST', 'PUT', 'PATCH')) {
      $resp = Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body "{}"
    } else {
      $resp = Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers
    }
    return [pscustomobject]@{
      HttpStatus = 200
      BodyCode = Get-JsonCode -Obj $resp
      Message = if ($resp.PSObject.Properties.Name -contains "msg") { [string]$resp.msg } else { "" }
      Raw = $resp
    }
  } catch {
    $status = 500
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $status = [int]$_.Exception.Response.StatusCode
    }
    return [pscustomobject]@{
      HttpStatus = $status
      BodyCode = [string]$status
      Message = $_.Exception.Message
      Raw = $null
    }
  }
}

if (-not (Test-Path $MatrixFile)) {
  throw "Matrix file not found: $MatrixFile"
}

$matrix = Get-Content $MatrixFile -Raw | ConvertFrom-Json
$accounts = @($matrix.accounts)
$tests = @($matrix.tests)

$tokens = @{}
foreach ($acc in $accounts) {
  $loginPayload = @{ username = $acc.username; password = $acc.password } | ConvertTo-Json
  try {
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $loginPayload
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
foreach ($t in $tests) {
  foreach ($acc in $accounts) {
    $expectedAllowed = @($t.allow) -contains [string]$acc.role
    $httpStatus = 401
    $bodyCode = "40100"
    $msg = "login_failed"

    if ($tokens[$acc.role]) {
      $headers = @{ Authorization = "Bearer $($tokens[$acc.role])" }
      $result = Invoke-ApiSafe -Method ([string]$t.method) -Uri "$BaseUrl$($t.path)" -Headers $headers
      $httpStatus = $result.HttpStatus
      $bodyCode = $result.BodyCode
      $msg = $result.Message
    }

    $isDenied = ($httpStatus -eq 401 -or $httpStatus -eq 403 -or $bodyCode -eq "40100" -or $bodyCode -eq "40300")
    $actualAllowed = -not $isDenied

    $rows.Add([pscustomobject]@{
      Endpoint = [string]$t.name
      Method = [string]$t.method
      Path = [string]$t.path
      Role = [string]$acc.role
      Username = [string]$acc.username
      ExpectedAllowed = $expectedAllowed
      ActualAllowed = $actualAllowed
      HttpStatus = $httpStatus
      ResponseCode = $bodyCode
      Note = $msg
    }) | Out-Null
  }
}

$mismatch = $rows | Where-Object { $_.ExpectedAllowed -ne $_.ActualAllowed }
$loginFailedRoles = $accounts | Where-Object { -not $tokens[$_.role] } | ForEach-Object { $_.role }

$summary = [pscustomobject]@{
  generatedAt = (Get-Date).ToString("s")
  baseUrl = $BaseUrl
  totalChecks = $rows.Count
  mismatchCount = @($mismatch).Count
  loginFailedRoles = @($loginFailedRoles)
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
