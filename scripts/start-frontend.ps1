$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "runtime.ps1")
[void](Assert-NodeRuntime -MinimumMajor 20)
Push-Location "$projectRoot\frontend"
try {
    npm ci
    npm run dev
} finally {
    Pop-Location
}

