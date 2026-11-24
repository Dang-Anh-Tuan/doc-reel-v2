<#
.SYNOPSIS
  Chạy DocReel ở máy dev: bật Postgres, build jar nếu chưa có (hoặc -Build), chạy server ở foreground (Ctrl+C để dừng).
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1 -Build -Port 8081
#>
param(
    [switch]$Build,      # build lại jar trước khi chạy
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root   # app đọc .env, data\, inbox\... theo thư mục chạy

$env:Path = [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + [Environment]::GetEnvironmentVariable('Path', 'Machine')
$jh = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
if ($jh) { $env:JAVA_HOME = $jh }

docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'Postgres chưa lên — Docker Desktop đã chạy chưa?' }

$jar = Get-ChildItem target\docreel-*.jar -ErrorAction SilentlyContinue | Where-Object Name -notmatch 'plain' | Select-Object -First 1
if ($Build -or -not $jar) {
    & .\mvnw.cmd -B -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Build thất bại' }
    $jar = Get-ChildItem target\docreel-*.jar | Where-Object Name -notmatch 'plain' | Select-Object -First 1
}

Write-Host "DocReel: http://localhost:$Port  (thả JSON vào $root\inbox)" -ForegroundColor Green
java -jar $jar.FullName "--server.port=$Port"
