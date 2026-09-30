[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

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
    throw 'Enter a complete verified sender email address.'
}

$secureSmtpKey = Read-Host 'Brevo SMTP Key' -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSmtpKey)

try {
    $smtpKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    $smtpKey = $smtpKey.Trim()
    if ($smtpKey.Contains('*') -or $smtpKey.Length -lt 15) {
        throw 'Use the full Brevo SMTP key shown once at creation, not an API key or masked table value.'
    }

    $env:V2_PORT = '8080'
    $env:V2_EMAIL_DELIVERY_MODE = 'smtp'
    $env:V2_SMTP_HOST = 'smtp-relay.brevo.com'
    $env:V2_SMTP_PORT = '587'
    $env:V2_SMTP_USERNAME = $smtpLogin
    $env:V2_SMTP_PASSWORD = $smtpKey
    $env:V2_EMAIL_FROM_ADDRESS = $senderEmail
    $env:V2_SMTP_AUTH = 'true'
    $env:V2_SMTP_STARTTLS = 'true'

    Write-Host 'Starting V2 backend with Brevo SMTP on http://localhost:8080 ...'
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=v2'
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot exited with code $LASTEXITCODE."
    }
}
finally {
    if ($keyPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    }
    Remove-Variable smtpKey -ErrorAction SilentlyContinue
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
