$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $projectDir '.cache/jdk17/extracted/jdk-17.0.20.1+1'
if (Test-Path -LiteralPath (Join-Path $localJdk 'bin/java.exe')) {
    $env:JAVA_HOME = $localJdk
    $env:Path = (Join-Path $localJdk 'bin') + ';' + $env:Path
}
if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME is required. Install Temurin JDK 17 or use the project-local JDK cache.'
}
$version = & (Join-Path $env:JAVA_HOME 'bin/java.exe') -version 2>&1 | Select-Object -First 1
if ($version -notmatch 'version "17\.') {
    throw "Java 17 is required by the team baseline. Found: $version"
}
& (Join-Path $PSScriptRoot 'maven.ps1') --batch-mode --no-transfer-progress verify @args
exit $LASTEXITCODE