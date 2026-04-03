param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Token = "",
    [string]$Username = "admin",
    [string]$Password = "admin",
    [string]$Scenario = "latency-and-failure-observe",
    [string]$TargetPath = "/api/auth/registration-options",
    [string]$InjectPath = "/api/non-existent-reliability-probe",
    [int]$ProbeCount = 4,
    [string]$OutJson = ""
)

$headers = @{
    "Content-Type" = "application/json"
}

if ($Token -and $Token.Trim().Length -gt 0) {
    $headers["Authorization"] = "Bearer $Token"
} elseif ($Username -and $Password) {
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
    $loginResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginBody
    if ($loginResp.code -ne 20000 -or -not $loginResp.data.token) {
        throw "Login failed when acquiring token for reliability drill. code=$($loginResp.code) msg=$($loginResp.msg)"
    }
    $headers["Authorization"] = "Bearer $($loginResp.data.token)"
}

$payload = @{
    scenario = $Scenario
    targetPath = $TargetPath
    injectPath = $InjectPath
    probeCount = $ProbeCount
} | ConvertTo-Json

Write-Host "[reliability] Running drill against $BaseUrl ..."
$response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/award/reliability/drill/run" -Headers $headers -Body $payload

Write-Host "[reliability] Drill status:" $response.msg
$response.data | ConvertTo-Json -Depth 8

Write-Host "`n[reliability] Fetching latest drill history..."
$history = Invoke-RestMethod -Method Get -Uri "$BaseUrl/award/reliability/drill/history?limit=3" -Headers $headers
$history.data | ConvertTo-Json -Depth 6

$summary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("s")
    scenario = $Scenario
    targetPath = $TargetPath
    injectPath = $InjectPath
    probeCount = $ProbeCount
    runResponse = $response
    recentHistory = $history.data
}

if ($OutJson) {
    $outDir = Split-Path -Parent $OutJson
    if ($outDir -and -not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    }
    $summary | ConvertTo-Json -Depth 12 | Set-Content -Path $OutJson -Encoding UTF8
    Write-Host "[reliability] Evidence written: $OutJson"
}
