[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Compose configuration validation failed." }
    docker compose up --build --detach --wait
    if ($LASTEXITCODE -ne 0) { throw "Container environment did not become ready." }

    $health = Invoke-RestMethod -Uri "http://127.0.0.1:8088/actuator/health" -TimeoutSec 10
    if ($health.status -ne "UP") { throw "Backend health endpoint is not UP." }
    $page = Invoke-WebRequest -Uri "http://127.0.0.1:8088/" -TimeoutSec 10 -UseBasicParsing
    if ($page.StatusCode -ne 200) { throw "Frontend did not return HTTP 200." }
}
finally {
    docker compose down --volumes --remove-orphans
    Pop-Location
}
