param(
	[string]$BaseUrl = "http://localhost:8080/api",
	[string]$Username = "admin",
	[string]$Password = "admin",
	[string]$DbEndpoint = "127.0.0.1",
	[int]$DbPort = 3307,
	[string]$Database = "aegisai",
	[string]$DbUser = "root",
	[string]$DbPassword = "root",
	[string]$CompanyId = "1",
	[string]$OutputDir = "./docs/governance-readiness-evidence",
	[switch]$RunRegressions
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $OutputDir)) {
	New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$stamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
$runDir = Join-Path $OutputDir "run-$stamp"
New-Item -ItemType Directory -Path $runDir -Force | Out-Null

$loginPayload = @{ username = $Username; password = $Password } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginPayload
if ($loginResp.code -ne 20000 -or -not $loginResp.data.token) {
	throw "Login failed: code=$($loginResp.code) msg=$($loginResp.msg)"
}
$token = [string]$loginResp.data.token
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }

Write-Host "[award-evidence] 1/5 Running reliability drill"
$reliabilityPath = Join-Path $runDir "reliability-drill.json"
& .\scripts\run-reliability-drill.ps1 -BaseUrl $BaseUrl -Token $token -OutJson $reliabilityPath

Write-Host "[award-evidence] 2/5 Building audit hash chain"
$today = Get-Date
$from = $today.AddDays(-6).ToString("yyyy-MM-dd")
$to = $today.ToString("yyyy-MM-dd")
$hashBuildBody = @{ from = $from; to = $to } | ConvertTo-Json
$hashBuildResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/award/audit-hash-chain/build" -Headers $headers -Body $hashBuildBody
$hashBuildPath = Join-Path $runDir "audit-hash-chain-build.json"
$hashBuildResp | ConvertTo-Json -Depth 12 | Set-Content -Path $hashBuildPath -Encoding UTF8

Write-Host "[award-evidence] 3/5 Verifying audit hash chain"
$hashVerifyPath = Join-Path $runDir "audit-hash-chain-verify.json"
& .\scripts\verify-audit-hash-chain.ps1 -UseDockerCompose -Endpoint $DbEndpoint -Port $DbPort -Database $Database -DbUser $DbUser -DbPassword $DbPassword -CompanyId $CompanyId -OutJson $hashVerifyPath -NoExit
$hashVerifyReport = $null
if (Test-Path $hashVerifyPath) {
	$hashVerifyReport = Get-Content -Raw -Path $hashVerifyPath | ConvertFrom-Json
}
$hashVerifyPassed = ($hashVerifyReport -and $hashVerifyReport.passed -eq $true)
$hashVerifyExitCode = if ($hashVerifyPassed) { 0 } else { 2 }
if (-not $hashVerifyPassed) {
	Write-Warning "Audit hash-chain verification did not pass (exit=$hashVerifyExitCode). See: $hashVerifyPath"
}

Write-Host "[award-evidence] 4/5 Exporting award review package"
$packageDir = Join-Path $runDir "award-package"
& .\scripts\generate-award-review-package.ps1 -BaseUrl $BaseUrl -Token $token -OutputDir $packageDir -From $from -To $to

$identitySummaryPath = ""
$dutySummaryPath = ""
if ($RunRegressions.IsPresent) {
	Write-Host "[award-evidence] 5/5 Running regression gates"
	& .\scripts\run-identity-issue-regression.ps1
	& .\scripts\run-account-duty-segregation-regression.ps1
	$identitySummaryPath = ".\docs\identity-issue-regression-summary.json"
	$dutySummaryPath = ".\docs\account-duty-segregation-summary.json"
} else {
	Write-Host "[award-evidence] 5/5 Skipping regression gates (use -RunRegressions to enable)"
}

$finalSummary = [pscustomobject]@{
	generatedAt = (Get-Date).ToString("s")
	operator = $Username
	baseUrl = $BaseUrl
	runDirectory = $runDir
	reliabilityEvidence = $reliabilityPath
	auditHashChainBuildEvidence = $hashBuildPath
	auditHashChainVerifyEvidence = $hashVerifyPath
	auditHashChainVerifyPassed = $hashVerifyPassed
	auditHashChainVerifyExitCode = $hashVerifyExitCode
	packageDirectory = $packageDir
	identityRegressionSummary = $identitySummaryPath
	dutySegregationSummary = $dutySummaryPath
	status = if ($hashVerifyPassed) { "completed" } else { "completed_with_findings" }
}

$summaryPath = Join-Path $runDir "governance-readiness-evidence-summary.json"
$finalSummary | ConvertTo-Json -Depth 8 | Set-Content -Path $summaryPath -Encoding UTF8

Write-Host "[award-evidence] Completed. Summary: $summaryPath"
