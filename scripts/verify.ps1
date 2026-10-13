$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkCandidates = @(
    "C:\Program Files\Java\jdk-26.0.2",
    "C:\Program Files\Java\jdk-21",
    "C:\Program Files\Microsoft\jdk-21*"
)

if ((java -version 2>&1 | Select-String 'version "(2[1-9]|[3-9][0-9])') -eq $null) {
    $jdkHome = $jdkCandidates | ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } | Select-Object -First 1
    if ($null -eq $jdkHome) { throw "未找到 Java 21 或更高版本。" }
    $env:JAVA_HOME = $jdkHome.FullName
    $env:PATH = "$($env:JAVA_HOME)\bin;$($env:PATH)"
}

mvn -f "$projectRoot\backend\pom.xml" --batch-mode verify
Push-Location "$projectRoot\frontend"
try {
    npm install
    npm run build
} finally {
    Pop-Location
}

