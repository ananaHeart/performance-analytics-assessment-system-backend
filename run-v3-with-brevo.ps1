[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Test-V3MfaEncryptionKey {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    try {
        $decoded = [Convert]::FromBase64String($Value.Trim())
        return $decoded.Length -eq 32
    }
    catch {
        return $false
    }
}

function ConvertFrom-ProtectedV3Secret {
    param([string]$ProtectedValue)

    $secureValue = ConvertTo-SecureString -String $ProtectedValue
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        if ($pointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
    }
}

function Get-V3MfaEncryptionKey {
    $configuredKey = $env:V3_MFA_ENCRYPTION_KEY
    if (-not [string]::IsNullOrWhiteSpace($configuredKey)) {
        if (-not (Test-V3MfaEncryptionKey $configuredKey)) {
            throw 'V3_MFA_ENCRYPTION_KEY must be a Base64-encoded 32-byte value.'
        }
        return $configuredKey.Trim()
    }

    $localAppData = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
    if ([string]::IsNullOrWhiteSpace($localAppData)) {
        throw 'LOCALAPPDATA is unavailable. Set V3_MFA_ENCRYPTION_KEY explicitly.'
    }

    $secretDirectory = Join-Path $localAppData 'SMARTAssessment\secrets'
    $secretPath = Join-Path $secretDirectory 'v3-mfa-encryption-key.dpapi'

    if (Test-Path -LiteralPath $secretPath) {
        try {
            $protectedValue = (Get-Content -LiteralPath $secretPath -Raw).Trim()
            $storedKey = ConvertFrom-ProtectedV3Secret $protectedValue
        }
        catch {
            throw "The local MFA key cannot be decrypted for this Windows user. Restore the original key or set V3_MFA_ENCRYPTION_KEY explicitly. Key file: $secretPath"
        }

        if (-not (Test-V3MfaEncryptionKey $storedKey)) {
            throw "The local MFA key is invalid. Restore the original key instead of replacing it. Key file: $secretPath"
        }

        Write-Host "Using the Windows-protected local MFA key at $secretPath"
        return $storedKey.Trim()
    }

    $keyBytes = [byte[]]::new(32)
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($keyBytes)
    try {
        $generatedKey = [Convert]::ToBase64String($keyBytes)
    }
    finally {
        if ($null -ne $rng) {
            $rng.Dispose()
        }
        [Array]::Clear($keyBytes, 0, $keyBytes.Length)
    }

    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    $secureGeneratedKey = ConvertTo-SecureString -String $generatedKey -AsPlainText -Force
    $protectedGeneratedKey = ConvertFrom-SecureString -SecureString $secureGeneratedKey
    [IO.File]::WriteAllText($secretPath, $protectedGeneratedKey, [Text.Encoding]::ASCII)

    Write-Host "Created a Windows-protected local MFA key at $secretPath"
    Write-Warning 'Keep this key file. Deleting or replacing it will make existing authenticator enrollments unreadable.'
    return $generatedKey
}

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8080 is already in use. Stop the existing backend before running this script.'
}

$smtpLogin = (Read-Host 'Brevo SMTP Login').Trim()
if ($smtpLogin -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'Enter the exact SMTP Login displayed on the Brevo SMTP & API page.'
}

$senderEmail = (Read-Host 'Verified sender email [smartassessment.notifications@gmail.com]').Trim()
if (-not $senderEmail) {
    $senderEmail = 'smartassessment.notifications@gmail.com'
}
if ($senderEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'Enter a complete sender email that is verified in Brevo.'
}

$secureSmtpKey = Read-Host 'Brevo SMTP Key' -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSmtpKey)

try {
    $smtpKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    $smtpKey = $smtpKey.Trim()
    if ($smtpKey.Contains('*') -or $smtpKey.Length -lt 15) {
        throw 'Use the full Brevo SMTP key shown at creation, not an API key or masked table value.'
    }

    $env:V3_EMAIL_DELIVERY_MODE = 'smtp'
    $env:V3_SMTP_HOST = 'smtp-relay.brevo.com'
    $env:V3_SMTP_PORT = '587'
    $env:V3_SMTP_USERNAME = $smtpLogin
    $env:V3_SMTP_PASSWORD = $smtpKey
    $env:V3_EMAIL_FROM_ADDRESS = $senderEmail
    $env:V3_SMTP_AUTH = 'true'
    $env:V3_SMTP_STARTTLS = 'true'
    $env:V3_SMTP_STARTTLS_REQUIRED = 'true'
    $env:V3_MFA_ENABLED = 'true'
    $env:V3_MFA_ENCRYPTION_KEY = Get-V3MfaEncryptionKey

    # Mobile write APIs are on by default now (application-v3.properties), but
    # two values genuinely can't have a fixed default because they depend on
    # this machine/network: the LAN address, and an absolute (not relative)
    # writable path for scan evidence storage.
    if (-not $env:V3_MOBILE_PUBLIC_BASE_URL) {
        $lanIPv4 = (Get-NetIPAddress -AddressFamily IPv4 -PrefixOrigin Dhcp -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1 -ExpandProperty IPAddress)
        if (-not $lanIPv4) { $lanIPv4 = 'localhost' }
        $env:V3_MOBILE_PUBLIC_BASE_URL = "http://$($lanIPv4):8080"
    }
    if (-not $env:V3_SCAN_EVIDENCE_STORAGE_DIRECTORY) {
        $evidenceDirectory = Join-Path (Get-Location) 'output\v3-scan-evidence'
        New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
        $env:V3_SCAN_EVIDENCE_STORAGE_DIRECTORY = $evidenceDirectory
    }
    Write-Host "Mobile write APIs will bind to $($env:V3_MOBILE_PUBLIC_BASE_URL) - point the mobile app's server setting at this exact address."

    Write-Host 'Starting V3 backend with Brevo SMTP, Authenticator MFA, and mobile write APIs on http://localhost:8080 ...'
    $installedMaven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($installedMaven) {
        & $installedMaven.Source spring-boot:run '-Dspring-boot.run.profiles=v3' '-Dmaven.test.skip=true'
    }
    elseif (Test-Path -LiteralPath '.\mvnw.cmd') {
        & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=v3' '-Dmaven.test.skip=true'
    }
    else {
        throw 'Maven is unavailable. Install Maven or restore mvnw.cmd before starting the backend.'
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot exited with code $LASTEXITCODE."
    }
}
finally {
    if ($keyPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    }
    Remove-Variable smtpKey -ErrorAction SilentlyContinue
    Remove-Item Env:V3_EMAIL_DELIVERY_MODE -ErrorAction SilentlyContinue
    Remove-Item Env:V3_SMTP_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:V3_SMTP_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:V3_SMTP_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:V3_SMTP_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:V3_EMAIL_FROM_ADDRESS -ErrorAction SilentlyContinue
    Remove-Item Env:V3_SMTP_AUTH -ErrorAction SilentlyContinue
    Remove-Item Env:V3_SMTP_STARTTLS -ErrorAction SilentlyContinue
    Remove-Item Env:V3_SMTP_STARTTLS_REQUIRED -ErrorAction SilentlyContinue
    Remove-Item Env:V3_MFA_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:V3_MFA_ENCRYPTION_KEY -ErrorAction SilentlyContinue
}
