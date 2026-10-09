<#
.SYNOPSIS
    Build/run helper for the "Simple Recipe Change" Forge mod (Minecraft 1.20.1).

.DESCRIPTION
    Wraps gradlew.bat and pins the two environment variables this project depends on:

      JAVA_HOME        a JDK 17 (MC 1.20.1 / Forge 47.x compile and run on Java 17).
                       Auto-detected from the usual install locations if not passed in.
      GRADLE_USER_HOME a folder next to this project, so the ~3 GB of Gradle and
                       ForgeGradle caches do not fill up an already-tight system drive.

    Both can be overridden:  .\dev.ps1 build -JdkHome 'D:\jdk\17' -GradleUserHome 'E:\gradle'

.NOTES
    If Windows refuses to run this file ("running scripts is disabled on this system"),
    either use dev.cmd next to it, or allow signed local scripts once with:
        Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

.EXAMPLE
    .\dev.ps1 build              # compile + produce build/libs/simple_recipe_change-1.0.0.jar
    .\dev.ps1 runClient          # launch a dev Minecraft client with the mod loaded
    .\dev.ps1 runServer          # launch a dev dedicated server
    .\dev.ps1 runData            # run data generators -> src/generated/resources
    .\dev.ps1 clean
    .\dev.ps1 genIntellijRuns    # write IntelliJ run configurations
    .\dev.ps1 genEclipseRuns     # write Eclipse run configurations
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Task = 'build',

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Rest,

    [string]$JdkHome,

    [string]$GradleUserHome
)

$ErrorActionPreference = 'Stop'
$ProjectDir = $PSScriptRoot

# ------------------------------------------------------------------ JDK 17 ----
# NOTE: the parameter must not be called $Home -- PowerShell variable names are
# case-insensitive and $HOME is a read-only automatic variable.
function Test-Jdk17 {
    param([string]$JdkPath)
    if (-not $JdkPath) { return $false }
    $javaExe = Join-Path $JdkPath 'bin\java.exe'
    if (-not (Test-Path $javaExe)) { return $false }

    # Prefer the JDK's own 'release' file: reading it avoids running java.exe, whose
    # -version output goes to stderr and would trip $ErrorActionPreference = 'Stop'.
    $releaseFile = Join-Path $JdkPath 'release'
    if (Test-Path $releaseFile) {
        return [bool](Select-String -Path $releaseFile -Pattern 'JAVA_VERSION="17\.' -Quiet)
    }

    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $versionText = (& $javaExe -version 2>&1 | Out-String) }
    finally { $ErrorActionPreference = $previous }
    return ($versionText -match 'version "17\.')
}

if (-not (Test-Jdk17 -JdkPath $JdkHome)) {
    $candidates = @($env:JAVA_HOME, 'C:\Program Files\Java\jdk-17')
    foreach ($rootDir in @(
            'C:\Program Files\Java',
            'C:\Program Files\Eclipse Adoptium',
            'C:\Program Files\Microsoft',
            'C:\Program Files\Zulu',
            'C:\Program Files\Amazon Corretto',
            'C:\Program Files\BellSoft')) {
        if (Test-Path $rootDir) {
            $candidates += @(Get-ChildItem $rootDir -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '17' } |
                ForEach-Object { $_.FullName })
        }
    }
    $JdkHome = $candidates | Where-Object { Test-Jdk17 -JdkPath $_ } | Select-Object -First 1
}

if (-not $JdkHome) {
    throw "No JDK 17 found, and Minecraft 1.20.1 / Forge 47.x require Java 17. " +
          "Install one, or re-run with -JdkHome 'C:\path\to\jdk-17'."
}
$env:JAVA_HOME = $JdkHome

# ---------------------------------------------------------- GRADLE_USER_HOME ---
if (-not $GradleUserHome) {
    $GradleUserHome = Join-Path (Split-Path -Parent $ProjectDir) '.gradle-home'
}
if (-not (Test-Path $GradleUserHome)) { New-Item -ItemType Directory -Force -Path $GradleUserHome | Out-Null }
$env:GRADLE_USER_HOME = $GradleUserHome

Write-Host "JAVA_HOME        = $env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "gradle task      = $Task $($Rest -join ' ')"
Write-Host ''

$gradlew = Join-Path $ProjectDir 'gradlew.bat'
if (-not (Test-Path $gradlew)) { throw "gradlew.bat not found in $ProjectDir" }

# Gradle writes progress to stderr; do not let that trip 'Stop'.
$ErrorActionPreference = 'Continue'
& $gradlew @($Task) @Rest
exit $LASTEXITCODE
