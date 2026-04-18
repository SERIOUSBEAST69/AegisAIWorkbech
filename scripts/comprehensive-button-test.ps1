param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TestReportPath = "c:\Users\serio\Desktop\AegisAIWorkbech\test-report.txt"
)

$ErrorActionPreference = "Continue"

function Get-Json([string]$url, [string]$token = "") {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    return Invoke-RestMethod -Method Get -Uri $url -Headers $headers -TimeoutSec 30
}

function Post-Json([string]$url, $body, [string]$token = "") {
    $headers = @{ "Content-Type" = "application/json" }
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $payload = $body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Method Post -Uri $url -Headers $headers -Body $payload -TimeoutSec 30
}

$results = @()
$passCount = 0
$failCount = 0

function Log-Test($testName, $status, $details = "") {
    $script:passCount += if ($status -eq "PASS") { 1 } else { 0 }
    $script:failCount += if ($status -eq "FAIL") { 1 } else { 0 }
    $script:results += [PSCustomObject]@{
        TestName = $testName
        Status = $status
        Details = $details
        Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    }
    $color = if ($status -eq "PASS") { "Green" } else { "Red" }
    Write-Host "[$status] $testName" -ForegroundColor $color
    if ($details) { Write-Host "       $details" -ForegroundColor Gray }
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   AegisAIWorkbench Comprehensive Test" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ============================================
# 1. Login/Logout Loop Test
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "1. Login/Logout Loop Test" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "1. Login/Logout"; Timestamp = "" }

Write-Host "`n--- 1.1 Normal Login ---" -ForegroundColor Cyan
$loginResp = Post-Json "$BaseUrl/api/auth/login" @{username="admin";password="admin"}
if ($loginResp.code -eq 20000) {
    $adminToken = $loginResp.data.token
    $adminUserId = $loginResp.data.user.id
    Log-Test "1.1.1 Login Button - Normal" "PASS" "Login success, token obtained"
} else {
    Log-Test "1.1.1 Login Button - Normal" "FAIL" "Login failed: $($loginResp.message)"
}

Write-Host "`n--- 1.2 Wrong Password ---" -ForegroundColor Cyan
$wrongLoginResp = Post-Json "$BaseUrl/api/auth/login" @{username="admin";password="wrongpassword"}
if ($wrongLoginResp.code -ne 20000) {
    Log-Test "1.2.1 Login Button - Wrong Password" "PASS" "Wrong password correctly rejected"
} else {
    Log-Test "1.2.1 Login Button - Wrong Password" "FAIL" "Wrong password was accepted"
}

Write-Host "`n--- 1.3 Non-existent User ---" -ForegroundColor Cyan
$noUserResp = Post-Json "$BaseUrl/api/auth/login" @{username="nonexistent";password="admin"}
if ($noUserResp.code -ne 20000) {
    Log-Test "1.3.1 Login Button - Non-existent User" "PASS" "Non-existent user correctly rejected"
} else {
    Log-Test "1.3.1 Login Button - Non-existent User" "FAIL" "Non-existent user was accepted"
}

Write-Host "`n--- 1.4 Empty Password ---" -ForegroundColor Cyan
$emptyPwdResp = Post-Json "$BaseUrl/api/auth/login" @{username="admin";password=""}
if ($emptyPwdResp.code -ne 20000) {
    Log-Test "1.4.1 Login Button - Empty Password" "PASS" "Empty password correctly rejected"
} else {
    Log-Test "1.4.1 Login Button - Empty Password" "FAIL" "Empty password was accepted"
}

Write-Host "`n--- 1.5 Logout (Re-login as different user) ---" -ForegroundColor Cyan
$reviewerLogin = Post-Json "$BaseUrl/api/auth/login" @{username="admin_reviewer";password="admin"}
if ($reviewerLogin.code -eq 20000) {
    $reviewerToken = $reviewerLogin.data.token
    Log-Test "1.5.1 Logout - Re-login Different Account" "PASS" "Session switch successful"
} else {
    Log-Test "1.5.1 Logout - Re-login Different Account" "FAIL" "Cannot login with different account"
}

# ============================================
# 2. User Management CRUD Loop Test
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "2. User Management CRUD Loop Test" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "2. User CRUD"; Timestamp = "" }

Write-Host "`n--- 2.1 Get User List ---" -ForegroundColor Cyan
$listResp = Get-Json "$BaseUrl/api/user/list" $adminToken
if ($listResp.code -eq 20000) {
    $userCount = $listResp.data.Count
    Log-Test "2.1.1 User List - Load Button" "PASS" "Successfully loaded $userCount users"
} else {
    Log-Test "2.1.1 User List - Load Button" "FAIL" "Load failed: $($listResp.message)"
}

Write-Host "`n--- 2.2 User Pagination ---" -ForegroundColor Cyan
$pageResp = Get-Json "$BaseUrl/api/user/page?page=1&pageSize=10" $adminToken
if ($pageResp.code -eq 20000 -and $pageResp.data.total -gt 0) {
    Log-Test "2.2.1 Pagination - First Page Button" "PASS" "Total records: $($pageResp.data.total)"
} else {
    Log-Test "2.2.1 Pagination - First Page Button" "FAIL" "Pagination query failed"
}

Write-Host "`n--- 2.3 User Search ---" -ForegroundColor Cyan
$searchResp = Get-Json "$BaseUrl/api/user/page?page=1&pageSize=10&username=admin" $adminToken
if ($searchResp.code -eq 20000) {
    $found = $searchResp.data.list | Where-Object { $_.username -like "*admin*" }
    Log-Test "2.3.1 Search Button - By Username" "PASS" "Found $($found.Count) results"
} else {
    Log-Test "2.3.1 Search Button - By Username" "FAIL" "Search failed"
}

Write-Host "`n--- 2.4 Add New User ---" -ForegroundColor Cyan
$newUser = @{
    username = "testuser_$([guid]::NewGuid().ToString('N').Substring(0,8))"
    password = "Test@123456"
    realName = "Test User"
    department = "Test Dept"
    accountType = "real"
    accountStatus = "active"
}
$addResp = Post-Json "$BaseUrl/api/user/add" $newUser $adminToken
if ($addResp.code -eq 20000) {
    $newUserId = $addResp.data
    Log-Test "2.4.1 Add Button - Normal Add" "PASS" "New user ID: $newUserId"
} else {
    Log-Test "2.4.1 Add Button - Normal Add" "FAIL" "Add failed: $($addResp.message)"
    $newUserId = $null
}

Write-Host "`n--- 2.5 Duplicate Username Detection ---" -ForegroundColor Cyan
$dupResp = Post-Json "$BaseUrl/api/user/add" @{username="admin";password="test";realName="Duplicate"} $adminToken
if ($dupResp.code -ne 20000) {
    Log-Test "2.5.1 Add Button - Duplicate Username" "PASS" "Duplicate username correctly rejected"
} else {
    Log-Test "2.5.1 Add Button - Duplicate Username" "FAIL" "Duplicate username not detected"
}

Write-Host "`n--- 2.6 User Edit ---" -ForegroundColor Cyan
if ($newUserId) {
    $updateResp = Post-Json "$BaseUrl/api/user/update" @{
        id = $newUserId
        userId = $newUserId
        username = $newUser.username
        realName = "Modified Test User"
        department = "Modified Dept"
        accountStatus = "active"
    } $adminToken
    if ($updateResp.code -eq 20000) {
        Log-Test "2.6.1 Edit Button - Normal Edit" "PASS" "User updated successfully"
    } else {
        Log-Test "2.6.1 Edit Button - Normal Edit" "FAIL" "User update failed: $($updateResp.message)"
    }
} else {
    Write-Host "Skip edit test (no new user)" -ForegroundColor Gray
}

Write-Host "`n--- 2.7 Governance Change - User Update ---" -ForegroundColor Cyan
if ($newUserId) {
    $gcSubmitResp = Post-Json "$BaseUrl/api/governance-change/submit" @{
        module = "USER"
        action = "UPDATE"
        targetId = $newUserId
        payloadJson = (@{id=$newUserId;userId=$newUserId;username=$newUser.username;realName="Governance Update"} | ConvertTo-Json)
        confirmPassword = "admin"
    } $adminToken
    if ($gcSubmitResp.code -eq 20000) {
        $gcRequestId = $gcSubmitResp.data.id
        Log-Test "2.7.1 Submit Button - Governance Submit" "PASS" "Submit success, RequestID: $gcRequestId"

        $gcApproveResp = Post-Json "$BaseUrl/api/governance-change/approve" @{
            requestId = $gcRequestId
            approve = $true
            note = "Test approval"
            confirmPassword = "admin"
        } $reviewerToken
        if ($gcApproveResp.code -eq 20000) {
            Log-Test "2.7.2 Approve Button - Governance Approve" "PASS" "Approval success"
        } else {
            Log-Test "2.7.2 Approve Button - Governance Approve" "FAIL" "Approval failed: $($gcApproveResp.message)"
        }
    } else {
        Log-Test "2.7.1 Submit Button - Governance Submit" "FAIL" "Submit failed: $($gcSubmitResp.message)"
    }
}

Write-Host "`n--- 2.8 Governance Change - User Delete ---" -ForegroundColor Cyan
if ($newUserId) {
    $gcDelResp = Post-Json "$BaseUrl/api/governance-change/submit" @{
        module = "USER"
        action = "DELETE"
        targetId = $newUserId
        payloadJson = (@{id=$newUserId;userId=$newUserId;username=$newUser.username;deleteReason="Test delete"} | ConvertTo-Json)
        confirmPassword = "admin"
    } $adminToken
    if ($gcDelResp.code -eq 20000) {
        $gcDelRequestId = $gcDelResp.data.id
        Log-Test "2.8.1 Delete Button - Submit Delete Request" "PASS" "Delete request submitted"

        $gcDelApproveResp = Post-Json "$BaseUrl/api/governance-change/approve" @{
            requestId = $gcDelRequestId
            approve = $true
            note = "Test approval"
            confirmPassword = "admin"
        } $reviewerToken
        if ($gcDelApproveResp.code -eq 20000) {
            Log-Test "2.8.2 Delete Button - Approval Success" "PASS" "Delete approval success"
        } else {
            Log-Test "2.8.2 Delete Button - Approval Success" "FAIL" "Delete approval failed: $($gcDelApproveResp.message)"
        }
    } else {
        Log-Test "2.8.1 Delete Button - Submit Delete Request" "FAIL" "Delete request failed: $($gcDelResp.message)"
    }
}

# ============================================
# 3. Role Management Loop Test
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "3. Role Management Loop Test" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "3. Role Management"; Timestamp = "" }

Write-Host "`n--- 3.1 Get Role List ---" -ForegroundColor Cyan
$roleResp = Get-Json "$BaseUrl/api/role/list" $adminToken
if ($roleResp.code -eq 20000) {
    $roleCount = $roleResp.data.Count
    Log-Test "3.1.1 Role List - Load Button" "PASS" "Successfully loaded $roleCount roles"
} else {
    Log-Test "3.1.1 Role List - Load Button" "FAIL" "Role list load failed"
}

Write-Host "`n--- 3.2 Role Pagination ---" -ForegroundColor Cyan
$rolePageResp = Get-Json "$BaseUrl/api/role/page?page=1&pageSize=10" $adminToken
if ($rolePageResp.code -eq 20000) {
    Log-Test "3.2.1 Role Pagination - Page Button" "PASS" "Role pagination loaded"
} else {
    Log-Test "3.2.1 Role Pagination - Page Button" "FAIL" "Role pagination failed"
}

Write-Host "`n--- 3.3 Create Test Role ---" -ForegroundColor Cyan
$testRoleCode = "TEST_ROLE_$([guid]::NewGuid().ToString('N').Substring(0,6))"
$createRoleResp = Post-Json "$BaseUrl/api/role/add" @{
    name = "Test Role"
    code = $testRoleCode
    description = "Auto test created role"
    status = "active"
} $adminToken
if ($createRoleResp.code -eq 20000) {
    $testRoleId = $createRoleResp.data
    Log-Test "3.3.1 Create Button - Add Role" "PASS" "Role created, ID: $testRoleId"
} else {
    Log-Test "3.3.1 Create Button - Add Role" "FAIL" "Role creation failed: $($createRoleResp.message)"
    $testRoleId = $null
}

Write-Host "`n--- 3.4 Update Role ---" -ForegroundColor Cyan
if ($testRoleId) {
    $updateRoleResp = Post-Json "$BaseUrl/api/role/update" @{
        id = $testRoleId
        name = "Modified Test Role"
        code = $testRoleCode
        description = "Modified description"
    } $adminToken
    if ($updateRoleResp.code -eq 20000) {
        Log-Test "3.4.1 Edit Button - Update Role" "PASS" "Role updated successfully"
    } else {
        Log-Test "3.4.1 Edit Button - Update Role" "FAIL" "Role update failed: $($updateRoleResp.message)"
    }
}

Write-Host "`n--- 3.5 Governance Change - Role Update ---" -ForegroundColor Cyan
if ($testRoleId) {
    $gcRoleSubmit = Post-Json "$BaseUrl/api/governance-change/submit" @{
        module = "ROLE"
        action = "UPDATE"
        targetId = $testRoleId
        payloadJson = (@{id=$testRoleId;name="Governance Update Role"} | ConvertTo-Json)
        confirmPassword = "admin"
    } $adminToken
    if ($gcRoleSubmit.code -eq 20000) {
        $gcRoleRequestId = $gcRoleSubmit.data.id
        Log-Test "3.5.1 Submit Button - Role Change Submit" "PASS" "Role change request submitted"

        $gcRoleApprove = Post-Json "$BaseUrl/api/governance-change/approve" @{
            requestId = $gcRoleRequestId
            approve = $true
            note = "Test approval"
            confirmPassword = "admin"
        } $reviewerToken
        if ($gcRoleApprove.code -eq 20000) {
            Log-Test "3.5.2 Approve Button - Role Change Approve" "PASS" "Role change approval success"
        } else {
            Log-Test "3.5.2 Approve Button - Role Change Approve" "FAIL" "Role change approval failed"
        }
    } else {
        Log-Test "3.5.1 Submit Button - Role Change Submit" "FAIL" "Role change submit failed"
    }
}

# ============================================
# 4. Governance Change Center Loop Test
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "4. Governance Change Center Loop Test" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "4. Governance Change Center"; Timestamp = "" }

Write-Host "`n--- 4.1 Pending Request List ---" -ForegroundColor Cyan
$todoResp = Get-Json "$BaseUrl/api/governance-change/todo-page?page=1&pageSize=10" $adminToken
if ($todoResp.code -eq 20000) {
    $todoCount = $todoResp.data.total
    Log-Test "4.1.1 Pending List - Load Button" "PASS" "Pending requests: $todoCount"
} else {
    Log-Test "4.1.1 Pending List - Load Button" "FAIL" "Pending list load failed"
}

Write-Host "`n--- 4.2 All Request List ---" -ForegroundColor Cyan
$allResp = Get-Json "$BaseUrl/api/governance-change/page?page=1&pageSize=10" $adminToken
if ($allResp.code -eq 20000) {
    $allCount = $allResp.data.total
    Log-Test "4.2.1 All Requests - Load Button" "PASS" "Total requests: $allCount"
} else {
    Log-Test "4.2.1 All Requests - Load Button" "FAIL" "All requests load failed"
}

Write-Host "`n--- 4.3 Filter by Status ---" -ForegroundColor Cyan
$pendingResp = Get-Json "$BaseUrl/api/governance-change/page?page=1&pageSize=10&status=pending" $adminToken
if ($pendingResp.code -eq 20000) {
    Log-Test "4.3.1 Filter Button - By Status" "PASS" "Status filter success"
} else {
    Log-Test "4.3.1 Filter Button - By Status" "FAIL" "Status filter failed"
}

Write-Host "`n--- 4.4 Filter by Module ---" -ForegroundColor Cyan
$moduleResp = Get-Json "$BaseUrl/api/governance-change/page?page=1&pageSize=10&module=USER" $adminToken
if ($moduleResp.code -eq 20000) {
    Log-Test "4.4.1 Filter Button - By Module" "PASS" "Module filter success"
} else {
    Log-Test "4.4.1 Filter Button - By Module" "FAIL" "Module filter failed"
}

# ============================================
# 5. Audit Center Loop Test
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "5. Audit Center Loop Test" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "5. Audit Center"; Timestamp = "" }

Write-Host "`n--- 5.1 Audit Log List ---" -ForegroundColor Cyan
$auditResp = Get-Json "$BaseUrl/api/audit/page?page=1&pageSize=10" $adminToken
if ($auditResp.code -eq 20000) {
    $auditCount = $auditResp.data.total
    Log-Test "5.1.1 Audit Log - Load Button" "PASS" "Audit log total: $auditCount"
} else {
    Log-Test "5.1.1 Audit Log - Load Button" "FAIL" "Audit log load failed"
}

Write-Host "`n--- 5.2 Audit Log Filter ---" -ForegroundColor Cyan
$auditFilterResp = Get-Json "$BaseUrl/api/audit/page?page=1&pageSize=10&module=USER" $adminToken
if ($auditFilterResp.code -eq 20000) {
    Log-Test "5.2.1 Filter Button - Audit Log Filter" "PASS" "Audit log filter success"
} else {
    Log-Test "5.2.1 Filter Button - Audit Log Filter" "FAIL" "Audit log filter failed"
}

# ============================================
# 6. Policy Management Loop Test
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "6. Policy Management Loop Test" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "6. Policy Management"; Timestamp = "" }

Write-Host "`n--- 6.1 Policy List ---" -ForegroundColor Cyan
$policyResp = Get-Json "$BaseUrl/api/policy/page?page=1&pageSize=10" $adminToken
if ($policyResp.code -eq 20000) {
    $policyCount = $policyResp.data.total
    Log-Test "6.1.1 Policy List - Load Button" "PASS" "Policy total: $policyCount"
} else {
    Log-Test "6.1.1 Policy List - Load Button" "FAIL" "Policy list load failed"
}

# ============================================
# 7. Exception Scenario Tests
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "7. Exception Scenario Tests" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "7. Exception Scenarios"; Timestamp = "" }

Write-Host "`n--- 7.1 Invalid Token Access ---" -ForegroundColor Cyan
$invalidTokenResp = Get-Json "$BaseUrl/api/user/list" "invalid_token_12345"
if ($invalidTokenResp.code -ne 20000) {
    Log-Test "7.1.1 Invalid Token - Correctly Rejected" "PASS" "Invalid token correctly rejected"
} else {
    Log-Test "7.1.1 Invalid Token - Correctly Rejected" "FAIL" "Invalid token was not rejected"
}

Write-Host "`n--- 7.2 Missing Required Parameters ---" -ForegroundColor Cyan
$missingPwdResp = Post-Json "$BaseUrl/api/user/add" @{username="test"} $adminToken
if ($missingPwdResp.code -ne 20000) {
    Log-Test "7.2.1 Missing Password - Correct Validation" "PASS" "Missing password correctly rejected"
} else {
    Log-Test "7.2.1 Missing Password - Correct Validation" "FAIL" "Missing password was not rejected"
}

Write-Host "`n--- 7.3 SQL Injection Test ---" -ForegroundColor Cyan
$sqlInjectResp = Post-Json "$BaseUrl/api/auth/login" @{username="admin' OR '1'='1";password="any"}
if ($sqlInjectResp.code -ne 20000) {
    Log-Test "7.3.1 SQL Injection - Correct Protection" "PASS" "SQL injection correctly protected"
} else {
    Log-Test "7.3.1 SQL Injection - Correct Protection" "FAIL" "SQL injection was not protected"
}

# ============================================
# 8. Permission Control Test
# ============================================
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "8. Permission Control Test" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

$results += [PSCustomObject]@{ Section = "8. Permission Control"; Timestamp = "" }

Write-Host "`n--- 8.1 Normal User Permission Verification ---" -ForegroundColor Cyan
$normalLogin = Post-Json "$BaseUrl/api/auth/login" @{username="employee_2";password="admin"}
if ($normalLogin.code -eq 20000) {
    $normalToken = $normalLogin.data.token

    $normalUserManage = Get-Json "$BaseUrl/api/user/page?page=1&pageSize=10" $normalToken
    if ($normalUserManage.code -eq 20000) {
        Log-Test "8.1.1 Employee Account - User Management Access" "PASS" "Employee can access user management"
    } else {
        Log-Test "8.1.1 Employee Account - User Management Access" "FAIL" "Employee cannot access user management"
    }

    $normalCreateResp = Post-Json "$BaseUrl/api/user/add" @{username="hacker";password="test"} $normalToken
    if ($normalCreateResp.code -ne 20000) {
        Log-Test "8.1.2 Employee Account - Create User Permission" "PASS" "Employee cannot create user (correct)"
    } else {
        Log-Test "8.1.2 Employee Account - Create User Permission" "FAIL" "Employee can actually create user"
    }
} else {
    Log-Test "8.1.1 Employee Account - Login" "FAIL" "Employee account login failed"
}

# ============================================
# Test Summary
# ============================================
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "            Test Summary" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "PASS: $passCount" -ForegroundColor Green
Write-Host "FAIL: $failCount" -ForegroundColor Red
Write-Host "TOTAL: $($passCount + $failCount)" -ForegroundColor Cyan
Write-Host ""

$results += [PSCustomObject]@{ Section = "Test End"; PassCount = $passCount; FailCount = $failCount; Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss" }

$results | Format-Table -AutoSize | Out-File -FilePath $TestReportPath -Encoding UTF8
Write-Host "Detailed report saved to: $TestReportPath" -ForegroundColor Cyan

if ($failCount -eq 0) {
    Write-Host "`nAll tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`nSome tests failed, please check." -ForegroundColor Red
    exit 1
}