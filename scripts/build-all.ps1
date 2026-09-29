# Build every target (or the given ones) and collect release jars in dist\.
#   powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 26.3-neoforge 26.3-fabric
# Gradle JVM per target comes from target.properties (gradle.jdk = 17 | 21 | 25).
# JDK locations: JDK17_HOME / JDK21_HOME / JDK25_HOME, else found in the usual install locations (see Resolve-Jdk).
param([string[]]$Targets)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Get-JdkMajor([string]$home_) {
    $release = Join-Path $home_ 'release'
    if (-not (Test-Path $release)) { return $null }
    foreach ($line in Get-Content $release) {
        if ($line -match '^JAVA_VERSION="(\d+)') { return $Matches[1] }
    }
    return $null
}

# JDK<N>_HOME if set; else JAVA_HOME when it is a JDK N; else the first JDK N in the usual install locations
# (vendor folders under Program Files, Gradle-provisioned %USERPROFILE%\.gradle\jdks, IntelliJ's %USERPROFILE%\.jdks).
function Resolve-Jdk([string]$version) {
    $override = [Environment]::GetEnvironmentVariable("JDK${version}_HOME")
    if ($override) {
        if (Test-Path "$override\bin\java.exe") { return $override }
        throw "JDK${version}_HOME=$override has no bin\java.exe"
    }
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe") -and (Get-JdkMajor $env:JAVA_HOME) -eq $version) {
        return $env:JAVA_HOME
    }
    $patterns = @(
        "$env:ProgramFiles\Java\jdk-$version*",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-$version*",
        "$env:ProgramFiles\Microsoft\jdk-$version*",
        "$env:ProgramFiles\Zulu\zulu-$version*",
        "$env:ProgramFiles\Amazon Corretto\jdk$version*",
        "$env:USERPROFILE\.gradle\jdks\*-$version-*",
        "$env:USERPROFILE\.jdks\*-$version*"
    )
    foreach ($p in $patterns) {
        foreach ($c in (Get-Item $p -ErrorAction SilentlyContinue | Where-Object { $_.PSIsContainer })) {
            if ((Test-Path "$($c.FullName)\bin\java.exe") -and (Get-JdkMajor $c.FullName) -eq $version) { return $c.FullName }
        }
    }
    throw "JDK $version not found; install one or set JDK${version}_HOME"
}

function Read-Props([string]$file) {
    $props = @{}
    foreach ($line in Get-Content $file) {
        if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2].Trim() }
    }
    return $props
}

if (-not $Targets -or $Targets.Count -eq 0) {
    $Targets = Get-ChildItem "$root\targets" -Directory |
        Where-Object { Test-Path "$($_.FullName)\target.properties" } |
        ForEach-Object { $_.Name }
}

New-Item -ItemType Directory -Force "$root\dist" | Out-Null
$ok = @(); $failed = @()
foreach ($t in $Targets) {
    $dir = "$root\targets\$t"
    $props = Read-Props "$dir\target.properties"
    $env:JAVA_HOME = Resolve-Jdk $props['gradle.jdk']
    Write-Host "=== $t (Gradle JVM: $env:JAVA_HOME) ==="
    Push-Location $dir
    try {
        & .\gradlew.bat build --no-daemon *> "$root\dist\.build-$t.log"
        if ($LASTEXITCODE -ne 0) { throw "gradle exit $LASTEXITCODE" }
        $jar = Get-ChildItem (Join-Path $dir $props['jar.glob']) -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike '*-sources*' } | Select-Object -First 1
        if (-not $jar) { throw "no jar matching $($props['jar.glob'])" }
        Copy-Item $jar.FullName "$root\dist\" -Force
        Write-Host "    -> dist\$($jar.Name)"
        $ok += $t
    } catch {
        Write-Host "    FAILED: $_ (log: dist\.build-$t.log)"
        $failed += $t
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "OK ($($ok.Count)): $($ok -join ', ')"
if ($failed.Count -gt 0) { Write-Host "FAILED ($($failed.Count)): $($failed -join ', ')"; exit 1 }
