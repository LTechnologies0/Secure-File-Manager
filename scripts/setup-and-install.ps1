#Requires -Version 5.1
<#
.SYNOPSIS
  JDK + SDK setup, compile Secure-File-Manager, install on phone.
.EXAMPLE
  .\scripts\setup-and-install.ps1
  .\scripts\setup-and-install.ps1 -SkipSetup
#>
[CmdletBinding()]
param(
    [switch]$SkipSetup,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$AndroidEnv = Join-Path $RepoRoot '..\AndroWatch\scripts\lib\AndroidEnv.ps1'
. $AndroidEnv

Write-Host ''
Write-Host '========================================' -ForegroundColor Magenta
Write-Host '  Secure File Manager - install' -ForegroundColor Magenta
Write-Host '========================================' -ForegroundColor Magenta
Write-Host ''

try {
    if (-not $SkipSetup) {
        Write-Step 'Java (JDK 21)'
        Ensure-Java
        Write-Step 'Android SDK + ADB'
        $sdk = Ensure-AndroidSdk
        Write-ProjectSdkConfig -RepoRoot $RepoRoot -SdkRoot $sdk -JdkHome $env:JAVA_HOME
    }

    & (Join-Path $RepoRoot 'scripts\install-to-device.ps1') -DeviceSerial $DeviceSerial
} catch {
    Write-Fail $_.Exception.Message
    exit 1
}
