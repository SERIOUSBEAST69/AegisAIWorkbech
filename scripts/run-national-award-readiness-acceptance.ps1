param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [PSCredential]$Credential = $null,
    [string]$OutputPath = "./docs/governance-readiness-acceptance.json"
)

$ErrorActionPreference = "Stop"

function Read-ErrorResponseBody {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)

    if (-not $ErrorRecord.Exception -or -not $ErrorRecord.Exception.Response) {
        return $null
    }

    try {
        $stream = $ErrorRecord.Exception.Response.GetResponseStream()
        if (-not $stream) {
            return $null
        }

        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        $reader.Close()
        return $body
    } catch {
        return $null
    }
}

function Get-RecursivePropertyValue {
    param(
        [object]$Object,
        [string[]]$CandidateNames,
        [int]$MaxDepth = 6
    )

    if ($null -eq $Object -or $MaxDepth -lt 0) {
        return $null
    }

    if ($Object -is [System.Array]) {
        foreach ($item in $Object) {
            $value = Get-RecursivePropertyValue -Object $item -CandidateNames $CandidateNames -MaxDepth ($MaxDepth - 1)
            if ($null -ne $value) {
                return $value
            }
        }
        return $null
    }

    $props = @()
    if ($Object -is [hashtable]) {
        $props = @($Object.Keys)
        foreach ($name in $CandidateNames) {
            foreach ($key in $props) {
                if ([string]::Equals([string]$key, $name, [System.StringComparison]::OrdinalIgnoreCase)) {
                    return $Object[$key]
                }
            }
        }

        foreach ($key in $props) {
            $nested = $Object[$key]
            $value = Get-RecursivePropertyValue -Object $nested -CandidateNames $CandidateNames -MaxDepth ($MaxDepth - 1)
            if ($null -ne $value) {
                return $value
            }
        }
        return $null
    }

    if ($Object.PSObject -and $Object.PSObject.Properties) {
        $props = @($Object.PSObject.Properties)
        foreach ($name in $CandidateNames) {
            foreach ($prop in $props) {
                if ([string]::Equals([string]$prop.Name, $name, [System.StringComparison]::OrdinalIgnoreCase)) {
                    return $prop.Value
                }
            }
        }

        foreach ($prop in $props) {
            $nested = $prop.Value
            $value = Get-RecursivePropertyValue -Object $nested -CandidateNames $CandidateNames -MaxDepth ($MaxDepth - 1)
            if ($null -ne $value) {
                return $value
            }
        }
    }

    return $null
}

$outputDir = Split-Path -Parent $OutputPath
if ($outputDir -and -not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$plainPassword = "admin"
if ($Credential) {
    $Username = $Credential.UserName
    $plainPassword = $Credential.GetNetworkCredential().Password
}
$loginPayload = @{ username = $Username; password = $plainPassword } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $loginPayload
if ($loginResp.code -ne 20000 -or -not $loginResp.data.token) {
    throw "Login failed: code=$($loginResp.code) msg=$($loginResp.msg)"
}

$token = [string]$loginResp.data.token
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }

$checks = @()

function Invoke-Check {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [int]$ExpectedCode = 20000
    )

    $uri = "$BaseUrl$Path"
    $startedAt = Get-Date
    $bodyJson = $null
    if ($null -ne $Body) {
        $bodyJson = $Body | ConvertTo-Json -Depth 12
    }

    try {
        if ($null -ne $bodyJson) {
            $resp = Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $bodyJson -ContentType "application/json"
        } else {
            $resp = Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
        }

        $durationMs = [int]((Get-Date) - $startedAt).TotalMilliseconds
        $ok = ($resp.code -eq $ExpectedCode)

        $extract = [ordered]@{}
        if ($Name -eq "readiness_report" -and $resp.data) {
            $extract.implemented = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("implemented", "implementedCount", "implementedItems")
            $extract.total = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("total", "totalCount", "all", "dimensionTotal")
            $extract.score = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("score", "readinessScore", "readiness", "percent")
            $extract.status = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("status", "level", "readinessLevel")
            $extract.errorBudget = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("errorBudget", "errorBudgetStatus")

            if ($null -eq $extract.implemented -or $null -eq $extract.total) {
                $items = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("items", "dimensions", "checks", "gaps", "modules")
                if ($items -is [System.Array]) {
                    if ($null -eq $extract.total) {
                        $extract.total = @($items).Count
                    }
                    if ($null -eq $extract.implemented) {
                        $implementedCount = 0
                        foreach ($item in $items) {
                            $flag = Get-RecursivePropertyValue -Object $item -CandidateNames @("implemented", "done", "passed", "closed")
                            $status = Get-RecursivePropertyValue -Object $item -CandidateNames @("status", "state")
                            if ($flag -eq $true) {
                                $implementedCount++
                                continue
                            }
                            if ($status) {
                                $statusText = [string]$status
                                if ($statusText -match "implemented|done|pass|passed|closed|ready") {
                                    $implementedCount++
                                }
                            }
                        }
                        $extract.implemented = $implementedCount
                    }
                }
            }

            if ($null -eq $extract.implemented -or $null -eq $extract.total) {
                $checklist = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("gapChecklist", "checklist")
                if ($checklist -is [hashtable] -or $checklist -is [pscustomobject]) {
                    $entries = @($checklist.PSObject.Properties)
                    if ($null -eq $extract.total) {
                        $extract.total = @($entries).Count
                    }
                    if ($null -eq $extract.implemented) {
                        $implementedFromChecklist = 0
                        foreach ($entry in $entries) {
                            $status = Get-RecursivePropertyValue -Object $entry.Value -CandidateNames @("status", "state")
                            if ($status -and ([string]$status).ToLower() -eq "implemented") {
                                $implementedFromChecklist++
                            }
                        }
                        $extract.implemented = $implementedFromChecklist
                    }
                }
            }

            if ($null -eq $extract.implemented) {
                $extract.implemented = 0
            }
            if ($null -eq $extract.total) {
                $extract.total = 0
            }

            if ($null -eq $extract.implemented -or $null -eq $extract.total) {
                $extract.availableKeys = @($resp.data.PSObject.Properties.Name | Select-Object -First 20)
            }
        }
        if ($Name -eq "auto_remediate_dry_run" -and $resp.data) {
            $actions = @()
            if ($resp.data.actions) {
                $actions = @($resp.data.actions)
            }
            $extract.runId = $resp.data.runId
            $extract.dryRun = $resp.data.dryRun
            $extract.actionsCount = $actions.Count
            $extract.actionsSample = @($actions | Select-Object -First 5)
            $extract.blockersCount = if ($resp.data.blockers) { @($resp.data.blockers).Count } else { 0 }
        }
        if ($Name -eq "auto_remediate_last" -and $resp.data) {
            $extract.runId = $resp.data.runId
            $extract.status = $resp.data.status
            $extract.dryRun = $resp.data.dryRun
            $extract.startedAt = $resp.data.startedAt
        }
        if ($Name -eq "model_release_traffic_stats" -and $resp.data) {
            $extract.totalRequests = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("totalRequests", "total", "requestTotal", "count")
            $buckets = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("buckets", "bucketStats", "variants", "abBuckets")
            if ($buckets) {
                $extract.bucketCount = @($buckets).Count
                $extract.bucketSample = @($buckets | Select-Object -First 3)

                if ($null -eq $extract.totalRequests) {
                    $sum = 0
                    foreach ($bucket in $buckets) {
                        $bucketCount = Get-RecursivePropertyValue -Object $bucket -CandidateNames @("count", "requests", "total", "requestCount")
                        if ($bucketCount -ne $null) {
                            try {
                                $sum += [int]$bucketCount
                            } catch {
                                # ignore non-numeric bucket count values
                            }
                        }
                    }
                    if ($sum -gt 0) {
                        $extract.totalRequests = $sum
                    }
                }
            }

            if ($null -eq $extract.totalRequests) {
                $extract.availableKeys = @($resp.data.PSObject.Properties.Name | Select-Object -First 20)
                $extract.totalRequests = 0
            }
        }
        if ($Name -eq "model_explainability" -and $resp.data) {
            $features = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("featureImportance", "globalFeatureImportance", "topFeatures", "features")
            if ($features) {
                $extract.featureCount = @($features).Count
                $extract.featureSample = @($features | Select-Object -First 5)
            }
            $fairness = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("fairness", "fairnessSummary", "fairnessDisparity")
            if ($fairness) {
                $extract.fairness = $fairness
            }
            $segmentCount = Get-RecursivePropertyValue -Object $resp.data -CandidateNames @("segmentCount", "segments", "segmentStats")
            if ($segmentCount -is [System.Array]) {
                $extract.segmentCount = @($segmentCount).Count
            } elseif ($segmentCount -ne $null) {
                $extract.segmentCount = $segmentCount
            }
        }
        if ($Name -eq "ai_risk_list" -and $resp.data) {
            $extract.total = $resp.data.total
        }

        return [pscustomobject]@{
            name = $Name
            method = $Method
            path = $Path
            ok = $ok
            httpStatus = 200
            code = $resp.code
            msg = $resp.msg
            durationMs = $durationMs
            extract = [pscustomobject]$extract
        }
    } catch {
        $durationMs = [int]((Get-Date) - $startedAt).TotalMilliseconds
        $statusCode = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }

        $errorBody = Read-ErrorResponseBody -ErrorRecord $_
        $errorCode = $null
        $errorMsg = $_.Exception.Message

        if ($errorBody) {
            try {
                $errorJson = $errorBody | ConvertFrom-Json
                if ($errorJson.code) {
                    $errorCode = $errorJson.code
                }
                if ($errorJson.msg) {
                    $errorMsg = $errorJson.msg
                }
            } catch {
                $errorMsg = $errorBody
            }
        }

        return [pscustomobject]@{
            name = $Name
            method = $Method
            path = $Path
            ok = $false
            httpStatus = $statusCode
            code = $errorCode
            msg = $errorMsg
            durationMs = $durationMs
            extract = $null
        }
    }
}

$checks += Invoke-Check -Name "readiness_report" -Method "GET" -Path "/api/award/readiness/report"
$checks += Invoke-Check -Name "auto_remediate_dry_run" -Method "POST" -Path "/api/award/readiness/auto-remediate" -Body @{ dryRun = $true }
$checks += Invoke-Check -Name "auto_remediate_last" -Method "GET" -Path "/api/award/readiness/auto-remediate/last"
$checks += Invoke-Check -Name "model_explainability" -Method "GET" -Path "/api/ai/model-explainability"
$checks += Invoke-Check -Name "model_release_traffic_stats" -Method "GET" -Path "/api/ai/model-release/traffic-stats"
$checks += Invoke-Check -Name "ai_risk_list" -Method "GET" -Path "/api/ai-risk/list"

$failed = @($checks | Where-Object { -not $_.ok })
$passed = @($checks | Where-Object { $_.ok })

$report = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("s")
    baseUrl = $BaseUrl
    operator = $Username
    totalChecks = $checks.Count
    passedChecks = $passed.Count
    failedChecks = $failed.Count
    passed = ($failed.Count -eq 0)
    checks = $checks
}

$report | ConvertTo-Json -Depth 12 | Set-Content -Path $OutputPath -Encoding UTF8
$report | ConvertTo-Json -Depth 12
