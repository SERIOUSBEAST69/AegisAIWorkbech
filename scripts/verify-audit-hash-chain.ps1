param(
    [string]$Endpoint = "127.0.0.1",
    [int]$Port = 3306,
    [PSCredential]$DbCredential,
    [string]$Database = "aegisai"
)

$ErrorActionPreference = "Stop"

function Get-Sha256Hex([string]$inputText) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($inputText)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash($bytes)
        return ($hash | ForEach-Object { $_.ToString("x2") }) -join ""
    } finally {
        $sha.Dispose()
    }
}

function Coalesce([object]$v) {
    if ($null -eq $v) { return "" }
    return [string]$v
}

$mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysqlCmd) {
    Write-Error "mysql command not found in PATH. Please install MySQL client or add it to PATH."
}

if (-not $DbCredential) {
    $DbCredential = Get-Credential -Message "Enter DB credential for audit hash chain verification"
}

$dbUser = $DbCredential.UserName
$dbSecretText = $DbCredential.GetNetworkCredential().Password

$query = @"
SELECT
  c.id,
  c.company_id,
  c.audit_log_id,
  COALESCE(c.prev_hash, ''),
  COALESCE(c.current_hash, ''),
  COALESCE(l.user_id, ''),
  COALESCE(l.operation, ''),
  COALESCE(UNIX_TIMESTAMP(l.operation_time) * 1000, ''),
  COALESCE(l.input_overview, ''),
  COALESCE(l.output_overview, ''),
  COALESCE(l.result, '')
FROM audit_hash_chain c
JOIN audit_log l ON l.id = c.audit_log_id
ORDER BY c.company_id, c.id;
"@

$rows = & mysql `
    --batch --raw --skip-column-names `
    "--host=$Endpoint" "--port=$Port" "--user=$dbUser" "--password=$dbSecretText" $Database `
    -e $query

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to query DB. Check connection parameters."
}

if (-not $rows) {
    Write-Host "No rows found in audit_hash_chain."
    exit 0
}

$prevHashByCompany = @{}
$violations = New-Object System.Collections.Generic.List[object]

foreach ($line in $rows) {
    $parts = $line -split "`t", 11
    if ($parts.Count -lt 11) {
        $violations.Add("Invalid row format: $line")
        continue
    }

    $rowId = $parts[0]
    $companyId = $parts[1]
    $auditLogId = $parts[2]
    $storedPrevHash = Coalesce $parts[3]
    $storedCurrentHash = Coalesce $parts[4]
    $userId = Coalesce $parts[5]
    $operation = Coalesce $parts[6]
    $operationTimeEpochMs = Coalesce $parts[7]
    $inputOverview = Coalesce $parts[8]
    $outputOverview = Coalesce $parts[9]
    $result = Coalesce $parts[10]

    $expectedPrevHash = ""
    if ($prevHashByCompany.ContainsKey($companyId)) {
        $expectedPrevHash = $prevHashByCompany[$companyId]
    }

    if ($storedPrevHash -ne $expectedPrevHash) {
        $violations.Add("Row $rowId company=$companyId has broken prev_hash link. expected=$expectedPrevHash actual=$storedPrevHash")
    }

    $payload = @(
        Coalesce $companyId,
        Coalesce $auditLogId,
        Coalesce $userId,
        Coalesce $operation,
        Coalesce $operationTimeEpochMs,
        Coalesce $inputOverview,
        Coalesce $outputOverview,
        Coalesce $result,
        Coalesce $storedPrevHash
    ) -join "|"

    $expectedCurrentHash = Get-Sha256Hex $payload
    if ($storedCurrentHash -ne $expectedCurrentHash) {
        $violations.Add("Row $rowId company=$companyId hash mismatch. expected=$expectedCurrentHash actual=$storedCurrentHash")
    }

    $prevHashByCompany[$companyId] = $storedCurrentHash
}

if ($violations.Count -gt 0) {
    Write-Host "Audit hash chain verification failed: $($violations.Count) issue(s)."
    $violations | ForEach-Object { Write-Host $_ }
    exit 2
}

Write-Host "Audit hash chain verification passed. Checked $($rows.Count) row(s)."
exit 0
