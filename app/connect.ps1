$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDirectory
$oracleHome = if ($env:ORACLE_HOME) { $env:ORACLE_HOME } else { 'C:\app\Dell\product\21c\dbhomeXE' }
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Join-Path $oracleHome 'jdk' }
$java = Join-Path $javaHome 'bin\java.exe'
$javac = Join-Path $javaHome 'bin\javac.exe'
$jdbcJar = Join-Path $oracleHome 'jdbc\lib\ojdbc8.jar'
$source = Join-Path $scriptDirectory 'src\main\java\com\vehiclemanager'
$webSource = Join-Path $scriptDirectory 'src\main\resources\web'
$classes = Join-Path $scriptDirectory 'target\classes'

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

$dbUser = Read-Host 'Oracle schema username [VEHICLE_APP]'
if ([string]::IsNullOrWhiteSpace($dbUser)) {
    $dbUser = 'VEHICLE_APP'
}
$securePassword = Read-Host "Oracle password for $dbUser (input hidden)" -AsSecureString
$passwordPointer = [IntPtr]::Zero
try {
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $securePassword.Dispose()
}

if (-not $env:DB_URL) {
    $env:DB_URL = 'jdbc:oracle:thin:@//localhost:1521/XEPDB1'
}
$env:DB_USER = $dbUser

$port = 8080
while ($port -le 8090) {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $port)
    try {
        $listener.Start()
        $listener.Stop()
        break
    } catch {
        $listener.Stop()
        $port++
    }
}
if ($port -gt 8090) {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_USER -ErrorAction SilentlyContinue
    throw 'No available port found between 8080 and 8090.'
}
$env:APP_PORT = "$port"

try {
    Write-Host "Starting connected Vehicle Management System at http://localhost:$port"
    Write-Host 'Press Ctrl+C to stop the server.'
    & $java -cp "$classes;$jdbcJar" com.vehiclemanager.VehicleServer
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_USER -ErrorAction SilentlyContinue
    Remove-Item Env:DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:APP_PORT -ErrorAction SilentlyContinue
}
