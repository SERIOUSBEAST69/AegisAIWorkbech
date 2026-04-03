param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [PSCredential]$Credential = $null,
    [string]$OutputPath = "./docs/governance-readiness-acceptance.json"
)

$target = Get-ChildItem -Path $PSScriptRoot -File -Filter "*readiness-acceptance.ps1" |
    Where-Object { $_.Name -ne $MyInvocation.MyCommand.Name } |
    Select-Object -First 1

if (-not $target) {
    throw "No readiness acceptance script found."
}

& $target.FullName -BaseUrl $BaseUrl -Username $Username -Credential $Credential -OutputPath $OutputPath
