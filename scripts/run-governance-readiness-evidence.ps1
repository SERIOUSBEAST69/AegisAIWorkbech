param()

$target = Get-ChildItem -Path $PSScriptRoot -File -Filter "*readiness-evidence.ps1" |
    Where-Object { $_.Name -ne $MyInvocation.MyCommand.Name } |
    Select-Object -First 1

if (-not $target) {
    throw "No readiness evidence script found."
}

& $target.FullName @args
