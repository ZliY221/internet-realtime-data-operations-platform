function Get-VersionText {
    param([Parameter(Mandatory = $true)][string]$Executable, [string[]]$Arguments = @("--version"))

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        return (& $Executable @Arguments 2>&1 | Out-String).Trim()
    }
    catch {
        return ""
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Use-JavaRuntime {
    param([int]$MinimumMajor = 21)

    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }

    $currentJava = Get-Command java -ErrorAction SilentlyContinue
    if ($currentJava -and $currentJava.Source) {
        $candidates.Add((Split-Path -Parent (Split-Path -Parent $currentJava.Source)))
    }

    if ($env:OS -eq "Windows_NT") {
        foreach ($root in @(
            (Join-Path $env:ProgramFiles "Java"),
            (Join-Path $env:ProgramFiles "Microsoft"),
            (Join-Path $env:ProgramFiles "Eclipse Adoptium")
        )) {
            if (Test-Path -LiteralPath $root -PathType Container) {
                Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
                    Where-Object Name -Like "jdk-*" |
                    Sort-Object Name -Descending |
                    ForEach-Object { $candidates.Add($_.FullName) }
            }
        }
    }

    $seen = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)
    foreach ($candidateHome in $candidates) {
        if (-not $candidateHome -or -not $seen.Add($candidateHome)) { continue }
        $javaName = if ($env:OS -eq "Windows_NT") { "java.exe" } else { "java" }
        $javaPath = Join-Path $candidateHome "bin\$javaName"
        if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) { continue }
        $version = Get-VersionText -Executable $javaPath -Arguments @("-version")
        if ($version -match 'version "(?<major>\d+)') {
            $major = [int]$Matches.major
            if ($major -ge $MinimumMajor) {
                $env:JAVA_HOME = $candidateHome
                $env:PATH = (Join-Path $candidateHome "bin") + [System.IO.Path]::PathSeparator + $env:PATH
                return [PSCustomObject]@{ Home = $candidateHome; Major = $major; Version = $version.Split("`n")[0] }
            }
        }
    }
    throw "JDK $MinimumMajor or newer was not found. Install a supported JDK or set JAVA_HOME."
}

function Assert-CommandAvailable {
    param([Parameter(Mandatory = $true)][string]$Name)
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) { throw "$Name was not found on PATH." }
    return $command.Source
}

function Assert-NodeRuntime {
    param([int]$MinimumMajor = 20)
    $node = Assert-CommandAvailable -Name "node"
    $version = Get-VersionText -Executable $node
    if ($version -notmatch 'v(?<major>\d+)' -or [int]$Matches.major -lt $MinimumMajor) {
        throw "Node.js $MinimumMajor or newer is required; found $version."
    }
    [void](Assert-CommandAvailable -Name "npm")
    return $version
}
