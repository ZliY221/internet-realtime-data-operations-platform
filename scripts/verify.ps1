[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkCandidates = @(
    "C:\Program Files\Java\jdk-26.0.2",
    "C:\Program Files\Java\jdk-21",
    "C:\Program Files\Microsoft\jdk-21*"
)

$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    $javaVersion = & java -version 2>&1 | Out-String
}
catch {
    $javaVersion = ""
}
finally {
    $ErrorActionPreference = $previousPreference
}
if ($javaVersion -notmatch 'version "(2[1-9]|[3-9][0-9])') {
    $jdkHome = $jdkCandidates |
        ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } |
        Select-Object -First 1
    if ($null -eq $jdkHome) { throw "Java 21 or newer was not found." }
    $env:JAVA_HOME = $jdkHome.FullName
    $env:PATH = "$($env:JAVA_HOME)\bin;$($env:PATH)"
}

if (-not (Get-Command "mvn" -ErrorAction SilentlyContinue)) {
    throw "Maven was not found."
}
if (-not (Get-Command "npm" -ErrorAction SilentlyContinue)) {
    throw "Node.js and npm were not found."
}

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

