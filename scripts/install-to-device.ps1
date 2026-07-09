#Requires -Version 5.1
<#
.SYNOPSIS
  Compile Secure-File-Manager debug APK and install on a connected device.
.EXAMPLE
  .\scripts\install-to-device.ps1
  .\scripts\install-to-device.ps1 -NoBuild -DeviceSerial 49281FDJH000Y8
#>
[CmdletBinding()]
param(
    [switch]$NoBuild,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$AndroidEnv = Join-Path $RepoRoot '..\AndroWatch\scripts\lib\AndroidEnv.ps1'
if (-not (Test-Path $AndroidEnv)) {
    throw "AndroidEnv.ps1 introuvable: $AndroidEnv (besoin du dossier AndroWatch a cote)"
}
. $AndroidEnv

$jdk = Find-Jdk21
if ($jdk) {
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdk\bin;$env:PATH"
}
$sdk = Find-SdkRoot
if ($sdk) {
    $env:ANDROID_HOME = $sdk
    $pt = Join-Path $sdk 'platform-tools'
    if (Test-Path $pt) { $env:PATH = "$pt;$env:PATH" }
}

Push-Location $RepoRoot
try {
    if (-not $NoBuild) {
        Write-Step 'Compilation Secure-File-Manager (debug)'
        & .\gradlew.bat assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Compilation echouee' }
    }

    $serial = Select-AdbDevice -PreferredSerial $DeviceSerial
    if (-not $serial) { exit 1 }

    $apk = Join-Path $RepoRoot 'app\build\outputs\apk\debug\secure-file-manager-debug.apk'
    Install-ApkToDevice -ApkPath $apk -Serial $serial -AppLabel 'Secure File Manager'
} finally {
    Pop-Location
}
