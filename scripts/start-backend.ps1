$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "runtime.ps1")
[void](Use-JavaRuntime -MinimumMajor 21)
[void](Assert-CommandAvailable -Name "mvn")

mvn -f "$projectRoot\backend\pom.xml" spring-boot:run "-Dspring-boot.run.profiles=local"

