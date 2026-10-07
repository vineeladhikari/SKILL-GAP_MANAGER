$ErrorActionPreference = "Stop"

$passwordFile = Join-Path $PSScriptRoot "..\.local-postgres-password"
$passwordFile = [System.IO.Path]::GetFullPath($passwordFile)
$maven = Join-Path $HOME ".maven\maven-3.10.0\bin\mvn.cmd"
if (-not (Test-Path $passwordFile)) {
    throw "Local PostgreSQL is not configured. Run .\scripts\setup-local-postgres.ps1 first."
}
if (-not (Test-Path $maven)) {
    throw "Maven was not found at $maven. Install Maven or update this script to point to mvn.cmd."
}

$securePassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$databasePassword = $null
try {
    $databasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:SPRING_PROFILES_ACTIVE = "postgres"
    $env:DB_HOST = "127.0.0.1"
    $env:DB_PORT = "5432"
    $env:DB_NAME = "skillgap_manager"
    $env:DB_USER = "skillgap_app"
    $env:DB_PASSWORD = $databasePassword
    $env:PORT = "8081"

    Set-Location (Join-Path $PSScriptRoot "..")
    & $maven spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot stopped with exit code $LASTEXITCODE."
    }
} finally {
    $env:SPRING_PROFILES_ACTIVE = $null
    $env:DB_HOST = $null
    $env:DB_PORT = $null
    $env:DB_NAME = $null
    $env:DB_USER = $null
    $env:DB_PASSWORD = $null
    $env:PORT = $null
    $databasePassword = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
