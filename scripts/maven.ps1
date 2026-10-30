$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
Push-Location $projectDir
$previousMavenUserHome = $env:MAVEN_USER_HOME
try {
    $env:MAVEN_USER_HOME = Join-Path $projectDir '.cache/maven'
    & .\mvnw.cmd @args
    $result = $LASTEXITCODE
} finally {
    $env:MAVEN_USER_HOME = $previousMavenUserHome
    Pop-Location
}
exit $result
