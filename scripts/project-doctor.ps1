[CmdletBinding()]
param(
    [ValidateSet("Local", "Container")][string]$Profile = "Local",
    [string]$JsonOutput
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "runtime.ps1")
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param([string]$Name, [string]$Status, [string]$Detail)
    $checks.Add([PSCustomObject]@{ name = $Name; status = $Status; detail = $Detail })
}

try {
    $java = Use-JavaRuntime -MinimumMajor 21
    Add-Check "JDK" "PASS" $java.Version
}
catch { Add-Check "JDK" "FAIL" $_.Exception.Message }

try {
    $maven = Assert-CommandAvailable -Name "mvn"
    Add-Check "Maven" "PASS" (Get-VersionText -Executable $maven).Split("`n")[0]
}
catch { Add-Check "Maven" "FAIL" $_.Exception.Message }

try { Add-Check "Node.js" "PASS" (Assert-NodeRuntime -MinimumMajor 20) }
catch { Add-Check "Node.js" "FAIL" $_.Exception.Message }

if ($Profile -eq "Container") {
    try {
        [void](Assert-CommandAvailable -Name "docker")
        $composeVersion = (& docker compose version 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw "Docker Compose is unavailable." }
        & docker compose -f (Join-Path $projectRoot "compose.yaml") config --quiet
        if ($LASTEXITCODE -ne 0) { throw "compose.yaml is invalid." }
        Add-Check "Docker Compose" "PASS" $composeVersion
    }
    catch { Add-Check "Docker Compose" "FAIL" $_.Exception.Message }
}

$failed = @($checks | Where-Object status -EQ "FAIL").Count
$report = [ordered]@{
    project = "internet-realtime-data-operations-platform"
    profile = $Profile.ToLowerInvariant()
    status = if ($failed -eq 0) { "READY" } else { "BLOCKED" }
    checks = $checks
}

if ($JsonOutput) {
    $target = if ([System.IO.Path]::IsPathRooted($JsonOutput)) { $JsonOutput } else { Join-Path $projectRoot $JsonOutput }
    $parent = Split-Path -Parent $target
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    $report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $target -Encoding UTF8
}

$checks | ForEach-Object { Write-Output "[$($_.status)] $($_.name): $($_.detail)" }
Write-Output "Environment status: $($report.status)"
if ($failed -ne 0) { exit 1 }
