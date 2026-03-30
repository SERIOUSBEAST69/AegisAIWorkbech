param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Token = "",
    [string]$OutputDir = "./docs/award-package",
    [string]$From = "",
    [string]$To = ""
)

$headers = @{ "Content-Type" = "application/json" }
if ($Token -and $Token.Trim().Length -gt 0) {
    $headers["Authorization"] = "Bearer $Token"
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

if (-not $To) {
    $To = (Get-Date).ToString("yyyy-MM-dd")
}
if (-not $From) {
    $From = (Get-Date).AddDays(-6).ToString("yyyy-MM-dd")
}

$stamp = (Get-Date).ToString("yyyyMMdd-HHmmss")

Write-Host "[award] fetching fixed evaluation package..."
$fixed = Invoke-RestMethod -Method Get -Uri "$BaseUrl/award/evaluation/fixed-package" -Headers $headers
$fixedPath = Join-Path $OutputDir "fixed-evaluation-$stamp.json"
$fixed | ConvertTo-Json -Depth 12 | Out-File -FilePath $fixedPath -Encoding utf8

Write-Host "[award] exporting evidence package..."
$exportPayload = @{ from = $From; to = $To; includePdf = $true; includeJson = $true } | ConvertTo-Json
$export = Invoke-RestMethod -Method Post -Uri "$BaseUrl/award/export" -Headers $headers -Body $exportPayload
$exportPath = Join-Path $OutputDir "evidence-export-result-$stamp.json"
$export | ConvertTo-Json -Depth 12 | Out-File -FilePath $exportPath -Encoding utf8

Write-Host "[award] querying external anchors..."
$anchors = Invoke-RestMethod -Method Get -Uri "$BaseUrl/award/external-anchor/latest?limit=20" -Headers $headers
$anchorPath = Join-Path $OutputDir "external-anchors-$stamp.json"
$anchors | ConvertTo-Json -Depth 12 | Out-File -FilePath $anchorPath -Encoding utf8

Write-Host "[award] done"
Write-Host "  fixed package : $fixedPath"
Write-Host "  export result : $exportPath"
Write-Host "  anchors       : $anchorPath"
