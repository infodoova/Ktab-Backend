# PowerShell script to backup local Ktab database
param (
    [string]$DbName = "ktab",
    [string]$DbUser = "postgres",
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$Password = "123456",
    [string]$OutputFile = "backups\ktab_backup.sql"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
Set-Location $ProjectRoot

if (!(Test-Path "backups")) {
    New-Item -ItemType Directory -Path "backups" | Out-Null
}

$pgDumpPath = "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe"
if (!(Test-Path $pgDumpPath)) {
    $pgDump = Get-Command pg_dump -ErrorAction SilentlyContinue
    if ($pgDump) {
        $pgDumpPath = $pgDump.Source
    } else {
        Write-Error "pg_dump.exe not found. Please ensure PostgreSQL is installed."
        exit 1
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  📦 Backing up local PostgreSQL database: $DbName" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$env:PGPASSWORD = $Password

& $pgDumpPath -U $DbUser -h $DbHost -p $DbPort -d $DbName `
    --no-owner --no-acl --clean --if-exists `
    -f $OutputFile

if (Test-Path $OutputFile) {
    $size = (Get-Item $OutputFile).Length / 1KB
    Write-Host "✅ Backup successful: $OutputFile ($([Math]::Round($size, 2)) KB)" -ForegroundColor Green
    Write-Host "To send to your Contabo VPS, run:" -ForegroundColor Yellow
    Write-Host "scp $OutputFile root@<YOUR_CONTABO_VPS_IP>:/root/Ktab-Backend/backups/" -ForegroundColor White
} else {
    Write-Error "Backup file was not created."
}
