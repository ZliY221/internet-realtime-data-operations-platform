$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location "$projectRoot\frontend"
try {
    npm install
    npm run dev
} finally {
    Pop-Location
}

