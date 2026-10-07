$ErrorActionPreference = "Stop"

$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$createdb = "C:\Program Files\PostgreSQL\18\bin\createdb.exe"
$passwordFile = Join-Path $PSScriptRoot "..\.local-postgres-password"
$passwordFile = [System.IO.Path]::GetFullPath($passwordFile)

if (-not (Test-Path $psql) -or -not (Test-Path $createdb)) {
    throw "PostgreSQL 18 command-line tools were not found in C:\Program Files\PostgreSQL\18\bin."
}

$adminSecurePassword = Read-Host "Enter your PostgreSQL administrator (postgres) password" -AsSecureString
$adminPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecurePassword)
$adminPassword = $null
$applicationPassword = $null
$roleSql = $null
$env:PGPASSWORD = $null

try {
    $adminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPasswordPointer)
    $env:PGPASSWORD = $adminPassword

    & $psql -w -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc "SELECT 1;"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not authenticate to PostgreSQL. Check the password and confirm the PostgreSQL service is running."
    }

    $roleExists = & $psql -w -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = 'skillgap_app';"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the PostgreSQL application role."
    }

    $randomBytes = New-Object byte[] 32
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($randomBytes)
    } finally {
        $randomGenerator.Dispose()
    }
    $applicationPassword = [BitConverter]::ToString($randomBytes).Replace("-", "")
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)

    if (($roleExists | Out-String).Trim() -eq "1") {
        $roleSql = "ALTER ROLE skillgap_app WITH LOGIN PASSWORD '$applicationPassword';"
    } else {
        $roleSql = "CREATE ROLE skillgap_app WITH LOGIN PASSWORD '$applicationPassword';"
    }
    $roleSql | & $psql -w -h 127.0.0.1 -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create or update the PostgreSQL application role."
    }

    $databaseExists = & $psql -w -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'skillgap_manager';"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the application database."
    }
    if (($databaseExists | Out-String).Trim() -ne "1") {
        & $createdb -w -h 127.0.0.1 -p 5432 -U postgres -O skillgap_app skillgap_manager
        if ($LASTEXITCODE -ne 0) {
            throw "Could not create the skillgap_manager database."
        }
    } else {
        & $psql -w -h 127.0.0.1 -p 5432 -U postgres -d skillgap_manager -v ON_ERROR_STOP=1 -c "GRANT CONNECT, CREATE, TEMPORARY ON DATABASE skillgap_manager TO skillgap_app; GRANT USAGE, CREATE ON SCHEMA public TO skillgap_app;"
        if ($LASTEXITCODE -ne 0) {
            throw "Could not grant the application role access to the existing skillgap_manager database."
        }
    }

    ConvertTo-SecureString $applicationPassword -AsPlainText -Force |
        ConvertFrom-SecureString |
        Set-Content -LiteralPath $passwordFile -Encoding ASCII
    Write-Output "PostgreSQL database 'skillgap_manager' and application role 'skillgap_app' are ready."
    Write-Output "The generated application password is encrypted for this Windows account and was not displayed."
    Write-Output "Next, run .\scripts\run-local-postgres.ps1 from the BACKEND folder."
} finally {
    $env:PGPASSWORD = $null
    $adminPassword = $null
    $applicationPassword = $null
    $roleSql = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPasswordPointer)
}
