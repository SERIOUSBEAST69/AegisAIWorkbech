$ErrorActionPreference='Stop'
$BaseUrl='http://localhost:8080'
$accounts = @(
 @{ role='ADMIN'; name='治理管理员'; username='admin'; password='admin' },
 @{ role='ADMIN_REVIEWER'; name='治理复核员'; username='admin_reviewer'; password='admin' },
 @{ role='SECOPS'; name='安全运维'; username='secops'; password='Passw0rd!' },
 @{ role='BUSINESS_OWNER'; name='业务负责人'; username='bizowner'; password='Passw0rd!' },
 @{ role='AUDIT'; name='审计员'; username='audit01'; password='auditpass' }
)
$tests = @(
 @{name='DASHBOARD'; method='GET'; path='/api/dashboard/workbench'; allow=@('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')},
 @{name='DATA_ASSET_LIST'; method='GET'; path='/api/data-asset/list'; allow=@('ADMIN')},
 @{name='AUDIT_LOG_SEARCH'; method='GET'; path='/api/audit-log/search'; allow=@('ADMIN','ADMIN_REVIEWER','AUDIT')},
 @{name='SECURITY_EVENTS'; method='GET'; path='/api/security/events?page=1&pageSize=5'; allow=@('ADMIN','SECOPS')},
 @{name='PRIVACY_CONFIG'; method='GET'; path='/api/privacy/config'; allow=@('ADMIN','SECOPS')},
 @{name='PRIVACY_EVENTS'; method='GET'; path='/api/privacy/events?page=1&pageSize=5'; allow=@('ADMIN','SECOPS','BUSINESS_OWNER','AUDIT','ADMIN_REVIEWER')},
 @{name='ANOMALY_EVENTS'; method='GET'; path='/api/anomaly/events?page=1&pageSize=5'; allow=@('ADMIN','SECOPS','BUSINESS_OWNER','AUDIT','ADMIN_REVIEWER')},
 @{name='ADVERSARIAL_META'; method='GET'; path='/api/ai/adversarial/meta'; allow=@('ADMIN','SECOPS')},
 @{name='APPROVAL_LIST'; method='GET'; path='/api/governance-change/page?page=1&pageSize=5'; allow=@('ADMIN','BUSINESS_OWNER','ADMIN_REVIEWER')}
)

function Invoke-Safe($method,$url,$headers){
 try {
  $resp = Invoke-RestMethod -Method $method -Uri $url -Headers $headers
  return @{http=200; code=[string]$resp.code; msg=[string]$resp.msg}
 } catch {
  $status = 500
  if ($_.Exception.Response -and $_.Exception.Response.StatusCode){ $status=[int]$_.Exception.Response.StatusCode }
  return @{http=$status; code=[string]$status; msg=$_.Exception.Message}
 }
}

$tokens=@{}
$rows=@()
foreach($a in $accounts){
  $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType 'application/json' -Body (@{username=$a.username;password=$a.password}|ConvertTo-Json)
  $tokens[$a.role] = $login.data.token
}
foreach($t in $tests){
 foreach($a in $accounts){
   $expected = $t.allow -contains $a.role
   $headers=@{Authorization=('Bearer '+$tokens[$a.role])}
   $res = Invoke-Safe $t.method "$BaseUrl$($t.path)" $headers
   $denied = ($res.http -in 401,403 -or $res.code -in @('40100','40300'))
   $actual = -not $denied
   $rows += [pscustomobject]@{endpoint=$t.name;role=$a.role;name=$a.name;expectedAllowed=$expected;actualAllowed=$actual;http=$res.http;code=$res.code}
 }
}
$mismatch = @($rows | Where-Object { $_.expectedAllowed -ne $_.actualAllowed })
$out = [pscustomobject]@{generatedAt=(Get-Date).ToString('s'); total=$rows.Count; mismatchCount=$mismatch.Count; mismatch=$mismatch; rows=$rows }
$path='./docs/five-role-permission-check-2026-04-16.json'
$out | ConvertTo-Json -Depth 10 | Set-Content -Path $path -Encoding UTF8
Write-Output ('REPORT=' + $path)
$out | ConvertTo-Json -Depth 6
