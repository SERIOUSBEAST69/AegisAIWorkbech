param(
    [string]$BaseUrl = "http://localhost:8080",
    [PSCredential]$AdminCredential
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost([string]$url, $body, [string]$token = "") {
    $headers = @{ "Content-Type" = "application/json" }
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $payload = $body | ConvertTo-Json -Depth 8
    return Invoke-RestMethod -Method Post -Uri $url -Headers $headers -Body $payload
}

function Invoke-JsonGet([string]$url, [string]$token = "") {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    return Invoke-RestMethod -Method Get -Uri $url -Headers $headers
}

Write-Host "[1/6] Login as admin"
if (-not $AdminCredential) {
    $AdminCredential = Get-Credential -Message "Enter ADMIN credential for walkthrough"
}
$adminUser = $AdminCredential.UserName
$adminSecret = $AdminCredential.GetNetworkCredential().Password
$loginResp = Invoke-JsonPost "$BaseUrl/api/auth/login" @{ username = $adminUser; password = $adminSecret }
if ($loginResp.code -ne 20000) { throw "Admin login failed: $($loginResp | ConvertTo-Json -Depth 6)" }
$adminToken = $loginResp.data.token

Write-Host "[2/6] Create invite code"
$inviteResp = Invoke-JsonPost "$BaseUrl/api/auth/invite-code/create" @{ expireHours = 24 } $adminToken
if ($inviteResp.code -ne 20000) { throw "Invite create failed: $($inviteResp | ConvertTo-Json -Depth 6)" }
$inviteCode = $inviteResp.data.inviteCode
Write-Host "InviteCode: $inviteCode"

Write-Host "[3/6] Fetch registration options by invite"
$optionsResp = Invoke-JsonGet "$BaseUrl/api/auth/registration-options?inviteCode=$inviteCode"
if ($optionsResp.code -ne 20000) { throw "registration-options failed: $($optionsResp | ConvertTo-Json -Depth 6)" }

$identities = @($optionsResp.data.identities)
if ($identities.Count -eq 0) { throw "No self-register identities available" }
$employeeRole = $identities | Where-Object { $_.code -eq "EMPLOYEE" } | Select-Object -First 1
if (-not $employeeRole) { $employeeRole = $identities | Select-Object -First 1 }

Write-Host "[4/6] Register a new user with invite code"
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$newUser = "walkthrough_$stamp"
$registerResp = Invoke-JsonPost "$BaseUrl/api/auth/register" @{
    username = $newUser
    password = "Passw0rd!"
    confirmPassword = "Passw0rd!"
    realName = "Walkthrough User"
    nickname = "Walkthrough"
    roleId = $employeeRole.id
    inviteCode = $inviteCode
    organizationType = "enterprise"
    department = "Demo"
    phone = "139" + (Get-Random -Minimum 10000000 -Maximum 99999999)
    email = "$newUser@test.local"
    loginType = "password"
    accountType = "real"
}
if ($registerResp.code -ne 20000) { throw "register failed: $($registerResp | ConvertTo-Json -Depth 6)" }

Write-Host "[5/6] Validate account visibility"
$usersResp = Invoke-JsonGet "$BaseUrl/api/user/list" $adminToken
if ($usersResp.code -ne 20000) { throw "user list failed: $($usersResp | ConvertTo-Json -Depth 6)" }
$found = @($usersResp.data) | Where-Object { $_.username -eq $newUser } | Select-Object -First 1
if (-not $found) { Write-Warning "New user not found in /api/user/list. It may be pending approval in your policy setup." }

Write-Host "[6/6] Smoke-check core dashboards"
$homeResp = Invoke-JsonGet "$BaseUrl/api/dashboard/workbench" $adminToken
if ($homeResp.code -ne 20000) { throw "dashboard workbench failed: $($homeResp | ConvertTo-Json -Depth 6)" }
$obsResp = Invoke-JsonGet "$BaseUrl/api/dashboard/workbench" $adminToken
if ($obsResp.code -ne 20000) { throw "observability fetch failed: $($obsResp | ConvertTo-Json -Depth 6)" }

Write-Host "Walkthrough complete."
Write-Host "newUser=$newUser"
Write-Host "inviteCode=$inviteCode"
