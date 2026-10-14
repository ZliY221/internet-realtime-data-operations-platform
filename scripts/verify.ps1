[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "runtime.ps1")
[void](Use-JavaRuntime -MinimumMajor 21)
[void](Assert-CommandAvailable -Name "mvn")
[void](Assert-NodeRuntime -MinimumMajor 20)

& (Join-Path $PSScriptRoot "project-doctor.ps1") -Profile Local -JsonOutput "build/doctor/local.json"
if ($LASTEXITCODE -ne 0) { throw "Local environment inspection failed." }

& mvn -f (Join-Path $projectRoot "backend\pom.xml") --batch-mode verify
if ($LASTEXITCODE -ne 0) { throw "Backend verification failed." }

Push-Location (Join-Path $projectRoot "frontend")
try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw "Frontend dependency installation failed." }
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend production build failed." }
}
finally {
    Pop-Location
}

