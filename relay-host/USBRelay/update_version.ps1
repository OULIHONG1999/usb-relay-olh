# PowerShell script to auto-increment version
# Usage: .\update_version.ps1 [major|minor|patch]

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("major", "minor", "patch")]
    [string]$versionType = "patch"
)

$versionFile = ".\version.properties"

if (-not (Test-Path $versionFile)) {
    Write-Error "Version file not found: $versionFile"
    exit 1
}

# Read current version
$content = Get-Content $versionFile
$major = ($content | Where-Object { $_ -match "^VERSION_MAJOR=" }) -replace "VERSION_MAJOR=", ""
$minor = ($content | Where-Object { $_ -match "^VERSION_MINOR=" }) -replace "VERSION_MINOR=", ""
$patch = ($content | Where-Object { $_ -match "^VERSION_PATCH=" }) -replace "VERSION_PATCH=", ""

Write-Host "Current version: $major.$minor.$patch" -ForegroundColor Cyan

# Increment version
switch ($versionType) {
    "major" {
        $major = [int]$major + 1
        $minor = 0
        $patch = 0
    }
    "minor" {
        $minor = [int]$minor + 1
        $patch = 0
    }
    "patch" {
        $patch = [int]$patch + 1
    }
}

$newVersion = "$major.$minor.$patch"
Write-Host "New version: $newVersion" -ForegroundColor Green

# Update version file
$newContent = @"
# USB Relay Version Configuration
# Format: MAJOR.MINOR.PATCH
# This file is automatically updated by build scripts

VERSION_MAJOR=$major
VERSION_MINOR=$minor
VERSION_PATCH=$patch
"@

Set-Content -Path $versionFile -Value $newContent -Encoding UTF8

Write-Host "Version updated successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Build the app: .\gradlew.bat assembleDebug"
Write-Host "2. The app will show version: $newVersion"

exit 0
