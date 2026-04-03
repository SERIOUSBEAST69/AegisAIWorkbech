param(
    [string]$Endpoint = "127.0.0.1",
    [int]$Port = 3306,
    [PSCredential]$DbCredential,
    [string]$Database = "aegisai",
    [string]$DbUser = "",
    [string]$DbPassword = "",
    [string]$OperationTimeZone = "+08:00",
    [switch]$UseDockerCompose,
    [string]$CompanyId = "",
    [string]$OutJson = "",
    [int]$MaxViolationDetails = 200,
    [switch]$NoExit
)

$ErrorActionPreference = "Stop"

if (-not $DbUser) {
    if ($DbCredential) {
        $DbUser = $DbCredential.UserName
    } elseif ($env:AEGIS_DB_USER) {
        $DbUser = $env:AEGIS_DB_USER
    } elseif ($env:MYSQL_USER) {
        $DbUser = $env:MYSQL_USER
    }
}

if (-not $DbPassword) {
    if ($DbCredential) {
        $DbPassword = $DbCredential.GetNetworkCredential().Password
    } elseif ($env:AEGIS_DB_PASSWORD) {
        $DbPassword = $env:AEGIS_DB_PASSWORD
    } elseif ($env:MYSQL_PASSWORD) {
        $DbPassword = $env:MYSQL_PASSWORD
    }
}

if (-not $DbUser -or -not $DbPassword) {
    if (-not $DbCredential) {
        $DbCredential = Get-Credential -Message "Enter DB credential for audit hash chain verification"
    }
    $DbUser = $DbCredential.UserName
    $DbPassword = $DbCredential.GetNetworkCredential().Password
}

$companyFilter = ""
if ($CompanyId -and $CompanyId.Trim().Length -gt 0) {
        $companyFilter = " WHERE c.company_id = $CompanyId"
}

$totalRowsQuery = @"
SELECT COUNT(1)
FROM audit_hash_chain c
$companyFilter;
"@

$mismatchCountQuery = @"
WITH chain AS (
    SELECT
        c.id,
        c.company_id,
        COALESCE(c.prev_hash, '') AS prev_hash,
        COALESCE(c.current_hash, '') AS current_hash,
        COALESCE(LAG(c.current_hash) OVER (PARTITION BY c.company_id ORDER BY c.id), '') AS expected_prev_hash,
        LOWER(SHA2(CONCAT_WS('|',
            COALESCE(CAST(c.company_id AS CHAR), ''),
            COALESCE(CAST(c.audit_log_id AS CHAR), ''),
            COALESCE(CAST(l.user_id AS CHAR), ''),
            COALESCE(l.operation, ''),
            COALESCE(CAST(FLOOR(UNIX_TIMESTAMP(CONVERT_TZ(l.operation_time, '$OperationTimeZone', @@session.time_zone))) * 1000 AS CHAR), ''),
            COALESCE(l.input_overview, ''),
            COALESCE(l.output_overview, ''),
            COALESCE(l.result, ''),
            COALESCE(c.prev_hash, '')
        ), 256)) AS expected_current_hash
    FROM audit_hash_chain c
    JOIN audit_log l ON l.id = c.audit_log_id
    $companyFilter
)
SELECT COUNT(1)
FROM chain
WHERE prev_hash <> expected_prev_hash OR current_hash <> expected_current_hash;
"@

$mismatchDetailsQuery = @"
WITH chain AS (
    SELECT
        c.id,
        c.company_id,
        COALESCE(c.prev_hash, '') AS prev_hash,
        COALESCE(c.current_hash, '') AS current_hash,
        COALESCE(LAG(c.current_hash) OVER (PARTITION BY c.company_id ORDER BY c.id), '') AS expected_prev_hash,
        LOWER(SHA2(CONCAT_WS('|',
            COALESCE(CAST(c.company_id AS CHAR), ''),
            COALESCE(CAST(c.audit_log_id AS CHAR), ''),
            COALESCE(CAST(l.user_id AS CHAR), ''),
            COALESCE(l.operation, ''),
            COALESCE(CAST(FLOOR(UNIX_TIMESTAMP(CONVERT_TZ(l.operation_time, '$OperationTimeZone', @@session.time_zone))) * 1000 AS CHAR), ''),
            COALESCE(l.input_overview, ''),
            COALESCE(l.output_overview, ''),
            COALESCE(l.result, ''),
            COALESCE(c.prev_hash, '')
        ), 256)) AS expected_current_hash
    FROM audit_hash_chain c
    JOIN audit_log l ON l.id = c.audit_log_id
    $companyFilter
)
SELECT JSON_OBJECT(
    'id', id,
    'company_id', company_id,
    'prev_hash', prev_hash,
    'expected_prev_hash', expected_prev_hash,
    'current_hash', current_hash,
    'expected_current_hash', expected_current_hash,
    'prev_mismatch', IF(prev_hash <> expected_prev_hash, 1, 0),
    'hash_mismatch', IF(current_hash <> expected_current_hash, 1, 0)
)
FROM chain
WHERE prev_hash <> expected_prev_hash OR current_hash <> expected_current_hash
ORDER BY company_id, id
LIMIT $MaxViolationDetails;
"@

function Get-MysqlRows {
    param(
        [string]$Sql,
        [string]$DbHost,
        [int]$DbPort,
        [string]$User,
        [string]$Password,
        [string]$DbName,
        [bool]$ForceDocker
    )

    $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $ForceDocker -and $mysqlCmd) {
        $rows = & mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names "--host=$DbHost" "--port=$DbPort" "--user=$User" "--password=$Password" $DbName -e $Sql
        if ($LASTEXITCODE -eq 0) {
            return $rows
        }
    }

    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCmd) {
        Write-Error "Neither mysql client nor docker command is available to query DB."
    }

    $containerRows = & docker compose exec -T mysql mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names "-u$User" "-p$Password" $DbName -e $Sql
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to query DB via docker mysql container. Check credentials and compose status."
    }
    return $containerRows
}

function Get-FirstInt {
    param(
        [object]$Rows
    )

    if ($null -eq $Rows) {
        return 0
    }

    if ($Rows -is [System.Array]) {
        foreach ($item in $Rows) {
            $s = [string]$item
            if ($s -match '-?\d+') {
                return [int]$Matches[0]
            }
        }
        return 0
    }

    $text = [string]$Rows
    if ($text -match '-?\d+') {
        return [int]$Matches[0]
    }
    return 0
}

$forceDocker = $UseDockerCompose.IsPresent

$totalRowsRaw = Get-MysqlRows -Sql $totalRowsQuery -DbHost $Endpoint -DbPort $Port -User $DbUser -Password $DbPassword -DbName $Database -ForceDocker $forceDocker
$totalRows = Get-FirstInt -Rows $totalRowsRaw

$mismatchCountRaw = Get-MysqlRows -Sql $mismatchCountQuery -DbHost $Endpoint -DbPort $Port -User $DbUser -Password $DbPassword -DbName $Database -ForceDocker $forceDocker
$totalViolationCount = Get-FirstInt -Rows $mismatchCountRaw

$detailRows = @()
if ($totalViolationCount -gt 0) {
    $detailRows = Get-MysqlRows -Sql $mismatchDetailsQuery -DbHost $Endpoint -DbPort $Port -User $DbUser -Password $DbPassword -DbName $Database -ForceDocker $forceDocker
}

if ($totalRows -eq 0) {
    $emptySummary = [pscustomobject]@{
        generatedAt = (Get-Date).ToString("s")
        endpoint = $Endpoint
        port = $Port
        database = $Database
        companyId = if ($CompanyId) { $CompanyId } else { $null }
        totalRows = 0
        violationCount = 0
        passed = $true
        message = "No rows found in audit_hash_chain."
        violations = @()
    }
    if ($OutJson) {
        $outDir = Split-Path -Parent $OutJson
        if ($outDir -and -not (Test-Path $outDir)) {
            New-Item -ItemType Directory -Path $outDir -Force | Out-Null
        }
        $emptySummary | ConvertTo-Json -Depth 8 | Set-Content -Path $OutJson -Encoding UTF8
    }
    Write-Host "No rows found in audit_hash_chain."
    if ($NoExit.IsPresent) {
        return
    }
    exit 0
}

$violations = New-Object System.Collections.Generic.List[object]

foreach ($line in $detailRows) {
    if (-not $line) {
        continue
    }
    $detail = $null
    try {
        $detail = $line | ConvertFrom-Json
    } catch {
        continue
    }
    if (-not $detail) {
        continue
    }

    $rowId = [string]$detail.id
    $rowCompanyId = [string]$detail.company_id
    $prevHash = [string]$detail.prev_hash
    $expectedPrev = [string]$detail.expected_prev_hash
    $currentHash = [string]$detail.current_hash
    $expectedCurrent = [string]$detail.expected_current_hash
    $prevMismatch = [string]$detail.prev_mismatch
    $hashMismatch = [string]$detail.hash_mismatch

    if ($prevMismatch -eq "1") {
        $violations.Add("Row $rowId company=$rowCompanyId prev_hash mismatch. expected=$expectedPrev actual=$prevHash")
    }
    if ($hashMismatch -eq "1") {
        $violations.Add("Row $rowId company=$rowCompanyId current_hash mismatch. expected=$expectedCurrent actual=$currentHash")
    }
}

$summaryCompanyId = $null
if ($CompanyId) {
    $summaryCompanyId = $CompanyId
}

$summaryMessage = "Audit hash chain verification failed."
if ($totalViolationCount -eq 0) {
    $summaryMessage = "Audit hash chain verification passed."
}

$summary = [ordered]@{}
$summary["generatedAt"] = (Get-Date).ToString("s")
$summary["endpoint"] = $Endpoint
$summary["port"] = $Port
$summary["database"] = $Database
$summary["operationTimeZone"] = $OperationTimeZone
$summary["companyId"] = $summaryCompanyId
$summary["totalRows"] = $totalRows
$summary["violationCount"] = $totalViolationCount
$summary["passed"] = ($totalViolationCount -eq 0)
$summary["message"] = $summaryMessage
$summary["violations"] = @($violations | ForEach-Object { [string]$_ })
$summary["maxViolationDetails"] = $MaxViolationDetails
$summary["omittedViolationDetails"] = [Math]::Max(0, $totalViolationCount - $violations.Count)

if ($OutJson) {
    $outDir = Split-Path -Parent $OutJson
    if ($outDir -and -not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content -Path $OutJson -Encoding UTF8
}

if ($totalViolationCount -gt 0) {
    Write-Host "Audit hash chain verification failed: $totalViolationCount issue(s), showing $($violations.Count)."
    $violations | ForEach-Object { Write-Host $_ }
    if ($NoExit.IsPresent) {
        return
    }
    exit 2
}

Write-Host "Audit hash chain verification passed. Checked $totalRows row(s)."
if ($NoExit.IsPresent) {
    return
}
exit 0
