param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Token = "",
    [string]$Scenario = "latency-and-failure-observe",
    [string]$TargetPath = "/api/auth/registration-options",
    [string]$InjectPath = "/api/non-existent-reliability-probe",
    [int]$ProbeCount = 4
)

$headers = @{
    "Content-Type" = "application/json"
}

if ($Token -and $Token.Trim().Length -gt 0) {
    $headers["Authorization"] = "Bearer $Token"
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
