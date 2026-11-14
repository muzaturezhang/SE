param(
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $projectDir '.cache/jdk17/extracted/jdk-17.0.20.1+1'
$java = Join-Path $localJdk 'bin/java.exe'
$jar = Join-Path $projectDir 'target/holiday-resort-booking-0.1.0-SNAPSHOT.jar'
$dataDir = Join-Path $projectDir 'data/local'
$credentialDir = Join-Path $projectDir '.cache/staff-preview'
$passwordFile = Join-Path $credentialDir 'password.txt'

if (-not (Test-Path -LiteralPath $java)) {
    throw 'Project-local JDK 17 is missing. Restore .cache/jdk17 or set up Java 17 first.'
}
if (-not (Test-Path -LiteralPath $jar)) {
    Write-Host 'Build artifact not found. Building the runnable JAR...'
    & (Join-Path $PSScriptRoot 'maven.ps1') --batch-mode --no-transfer-progress -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'JAR build failed.' }
}

New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
New-Item -ItemType Directory -Path $credentialDir -Force | Out-Null

if (-not (Test-Path -LiteralPath $passwordFile)) {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $password = [Convert]::ToBase64String($bytes).Replace('+','A').Replace('/','B').TrimEnd('=')
    Set-Content -LiteralPath $passwordFile -Value $password -NoNewline -Encoding utf8
}

$staffPassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim()
if ($staffPassword.Length -lt 16) { throw 'Stored local STAFF password is invalid. Delete the ignored password file and rerun.' }

$dbPath = (Join-Path $dataDir 'stage4-use').Replace('\','/')
$env:JAVA_HOME = $localJdk
$env:Path = "$($localJdk)\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE = 'dev,local-staff'
$env:SPRING_DATASOURCE_URL = "jdbc:h2:file:$dbPath;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = "$Port"
$env:LOCAL_STAFF_USERNAME = 'localstaff'
$env:LOCAL_STAFF_PASSWORD = $staffPassword

Write-Host ''
Write-Host 'Holiday Resort Stage 4 local test environment'
Write-Host "Customer entry:  http://127.0.0.1:$Port/account/register"
Write-Host "Customer stays:   http://127.0.0.1:$Port/stays/new"
Write-Host "STAFF entry:      http://127.0.0.1:$Port/account/login"
Write-Host "STAFF bookings:   http://127.0.0.1:$Port/admin/stays"
Write-Host 'STAFF username:   localstaff'
Write-Host "STAFF password:   $passwordFile"
Write-Host 'Database file:    data/local/stage4-use.mv.db'
Write-Host 'Payments are sandbox-only. Use fictional names and emails; never enter real card or payment credentials.'
Write-Host 'Press Ctrl+C to stop the service.'
Write-Host ''

& $java -jar $jar
exit $LASTEXITCODE