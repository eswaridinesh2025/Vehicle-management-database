$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$oracleHome = if ($env:ORACLE_HOME) { $env:ORACLE_HOME } else { 'C:\app\Dell\product\21c\dbhomeXE' }
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Join-Path $oracleHome 'jdk' }
$java = Join-Path $javaHome 'bin\java.exe'
$javac = Join-Path $javaHome 'bin\javac.exe'
$jdbcJar = Join-Path $oracleHome 'jdbc\lib\ojdbc8.jar'
$source = Join-Path $PSScriptRoot 'src\main\java\com\vehiclemanager'
$webSource = Join-Path $PSScriptRoot 'src\main\resources\web'
$classes = Join-Path $PSScriptRoot 'target\classes'

foreach ($path in @($java, $javac, $jdbcJar, $source, $webSource)) {
    if (-not (Test-Path $path)) {
        throw "Required file not found: $path. Set JAVA_HOME or ORACLE_HOME if Oracle is installed elsewhere."
    }
}

New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javaSources = Get-ChildItem -Path $source -Filter '*.java' -File | Select-Object -ExpandProperty FullName
if (-not $javaSources) {
    throw "No Java source files found in: $source"
}
& $javac -encoding UTF-8 -cp $jdbcJar -d $classes $javaSources
if ($LASTEXITCODE -ne 0) {
    throw 'Java compilation failed.'
}

$webTarget = Join-Path $classes 'web'
New-Item -ItemType Directory -Force -Path $webTarget | Out-Null
Copy-Item -Path (Join-Path $webSource '*') -Destination $webTarget -Force

$port = 8080
if (Test-NetConnection -ComputerName localhost -Port $port -InformationLevel Quiet) {
    $port = 8081
}
if (Test-NetConnection -ComputerName localhost -Port $port -InformationLevel Quiet) {
    throw 'Ports 8080 and 8081 are already in use. Stop the other service or update app/run.ps1.'
}

$env:APP_PORT = "$port"
Write-Host "Starting Vehicle Management System: http://localhost:$port"
Write-Host 'Oracle credentials are entered in the local website and are not saved to disk.'
& $java -cp "$classes;$jdbcJar" com.vehiclemanager.VehicleServer