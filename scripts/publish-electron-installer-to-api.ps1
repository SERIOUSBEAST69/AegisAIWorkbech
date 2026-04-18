$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot

$candidateDirs = @(
  (Join-Path $repoRoot 'electron/dist'),
  (Join-Path $repoRoot 'dist')
)

$installer = $null
foreach ($dir in $candidateDirs) {
  if (-not (Test-Path $dir)) {
    continue
  }

  $found = Get-ChildItem -Path $dir -Filter 'Aegis Workbench Setup *.exe' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

  if ($found) {
    $installer = $found
    break
  }
}

if (-not $installer) {
  throw 'No installer found. Run npm run dist first and ensure an EXE exists in electron/dist or dist.'
}

$targetDir = Join-Path $repoRoot 'backend/src/main/resources/clients'
$primaryTargetFile = Join-Path $targetDir $installer.Name
$legacyTargetFile = Join-Path $targetDir 'AegisClient-Setup-1.0.0-x64.exe'

if (-not (Test-Path $targetDir)) {
  throw "Target directory not found: $targetDir"
}

Copy-Item -Path $installer.FullName -Destination $targetFile -Force
Copy-Item -Path $installer.FullName -Destination $primaryTargetFile -Force
Copy-Item -Path $installer.FullName -Destination $legacyTargetFile -Force

$sourceHash = (Get-FileHash -Path $installer.FullName -Algorithm SHA256).Hash
$primaryHash = (Get-FileHash -Path $primaryTargetFile -Algorithm SHA256).Hash
$legacyHash = (Get-FileHash -Path $legacyTargetFile -Algorithm SHA256).Hash

Write-Output "Published installer to API resources"
Write-Output "SOURCE_FILE=$($installer.FullName)"
Write-Output "PRIMARY_TARGET_FILE=$primaryTargetFile"
Write-Output "LEGACY_TARGET_FILE=$legacyTargetFile"
Write-Output "SIZE_BYTES=$($installer.Length)"
Write-Output "SOURCE_SHA256=$sourceHash"
Write-Output "PRIMARY_TARGET_SHA256=$primaryHash"
Write-Output "LEGACY_TARGET_SHA256=$legacyHash"
Write-Output "PRIMARY_HASH_MATCH=$($sourceHash -eq $primaryHash)"
Write-Output "LEGACY_HASH_MATCH=$($sourceHash -eq $legacyHash)"
