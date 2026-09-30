[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8080 is already in use. Stop the existing backend before running this script.'
}

$senderEmail = (Read-Host 'Sender Gmail address').Trim().ToLowerInvariant()
if ($senderEmail -notmatch '^[^\s@]+@gmail\.com$') {
    throw 'Enter a complete Gmail address, for example smart.assessment@gmail.com.'
}

$secureAppPassword = Read-Host 'Google 16-character App Password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureAppPassword)

try {
    $appPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $appPassword = $appPassword -replace '\s', ''
    if ($appPassword -notmatch '^[A-Za-z0-9]{16}$') {
        throw 'Google App Password must contain 16 letters or numbers. Do not use the normal Gmail password.'
    }

    $env:V2_PORT = '8080'
    $env:V2_EMAIL_DELIVERY_MODE = 'smtp'
    $env:V2_SMTP_HOST = 'smtp.gmail.com'
    $env:V2_SMTP_PORT = '587'
    $env:V2_SMTP_USERNAME = $senderEmail
    $env:V2_SMTP_PASSWORD = $appPassword
    $env:V2_EMAIL_FROM_ADDRESS = $senderEmail
    $env:V2_SMTP_AUTH = 'true'
    $env:V2_SMTP_STARTTLS = 'true'

    Write-Host 'Starting V2 backend with Gmail SMTP on http://localhost:8080 ...'
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=v2'
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot exited with code $LASTEXITCODE."
    }
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    Remove-Variable appPassword -ErrorAction SilentlyContinue
    Remove-Item Env:V2_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:V2_EMAIL_DELIVERY_MODE -ErrorAction SilentlyContinue
    Remove-Item Env:V2_SMTP_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:V2_SMTP_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:V2_SMTP_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:V2_SMTP_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:V2_EMAIL_FROM_ADDRESS -ErrorAction SilentlyContinue
    Remove-Item Env:V2_SMTP_AUTH -ErrorAction SilentlyContinue
    Remove-Item Env:V2_SMTP_STARTTLS -ErrorAction SilentlyContinue
}
