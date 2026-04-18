param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [PSCredential]$Credential = $null,
    [string]$OutputPath = "./docs/governance-readiness-acceptance.json"
)

$target = Join-Path $PSScriptRoot "run-national-award-readiness-acceptance.ps1"

if (-not (Test-Path $target)) {
    throw "No readiness acceptance script found."
}

& $target -BaseUrl $BaseUrl -Username $Username -Credential $Credential -OutputPath $OutputPath
