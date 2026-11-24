<#
.SYNOPSIS
  Cài mọi thứ DocReel cần trên máy Windows mới, chạy lại nhiều lần vẫn an toàn (bước nào có rồi thì bỏ qua).
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup.ps1 -SkipBuild
#>
param(
    [switch]$SkipBuild   # không build jar (chỉ cài công cụ + DB)
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$PlaywrightVersion = '1.65.1'   # khớp com.microsoft.playwright trong pom.xml

function Update-PathFromRegistry {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $jh = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    if ($jh) { $env:JAVA_HOME = $jh }
}
function Test-Cmd([string]$Name) { [bool](Get-Command $Name -ErrorAction SilentlyContinue) }
function Step([string]$Msg) { Write-Host "==> $Msg" -ForegroundColor Cyan }
function Install-Winget([string]$Id) {
    winget install --id $Id -e --silent --accept-package-agreements --accept-source-agreements
    Update-PathFromRegistry
}

Update-PathFromRegistry

Step 'Java 21'
if (-not (Test-Cmd java)) { Install-Winget 'EclipseAdoptium.Temurin.21.JDK' } else { Write-Host '  có sẵn' }

Step 'ffmpeg'
if (-not (Test-Cmd ffmpeg)) { Install-Winget 'Gyan.FFmpeg' } else { Write-Host '  có sẵn' }

Step 'Node.js (build web quản trị, cài Chromium)'
if (-not (Test-Cmd npx)) { Install-Winget 'OpenJS.NodeJS.LTS' } else { Write-Host '  có sẵn' }

Step 'Python + edge-tts'
if (-not (Test-Cmd python)) { Install-Winget 'Python.Python.3.12' }
if (-not (Test-Cmd edge-tts)) { python -m pip install --user edge-tts; Update-PathFromRegistry } else { Write-Host '  có sẵn' }

Step "Chromium cho Playwright $PlaywrightVersion"
npx -y "playwright@$PlaywrightVersion" install chromium

Step 'Docker Desktop'
if (-not (Test-Cmd docker)) { Install-Winget 'Docker.DockerDesktop'; throw 'Vừa cài Docker Desktop: đăng xuất/khởi động lại máy, mở Docker Desktop rồi chạy lại script.' }
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Start-Process "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    Write-Host '  đang chờ Docker khởi động...'
    for ($i = 0; $i -lt 60; $i++) { Start-Sleep 3; docker info *> $null; if ($LASTEXITCODE -eq 0) { break } }
    if ($LASTEXITCODE -ne 0) { throw 'Docker chưa chạy được, mở Docker Desktop bằng tay rồi chạy lại.' }
}

Step '.env'
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    Write-Host '  Đã tạo .env từ .env.example — điền key (ElevenLabs, YouTube) rồi chạy lại.' -ForegroundColor Yellow
} else { Write-Host '  có sẵn (không đọc nội dung)' }

Step 'PostgreSQL (docker compose)'
docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'docker compose up thất bại' }

if (-not $SkipBuild) {
    Step 'Build jar'
    & .\mvnw.cmd -B -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Build thất bại' }

    Step 'Tự kiểm tra (--doctor)'
    $jar = Get-ChildItem target\docreel-*.jar | Where-Object Name -notmatch 'plain' | Select-Object -First 1
    java -jar $jar.FullName --doctor
}

Write-Host ''
Write-Host 'Xong. Chạy server: scripts\run.ps1' -ForegroundColor Green
if (-not (Test-Path data\youtube-token.enc)) {
    Write-Host 'Chưa cấp quyền YouTube: java -jar target\docreel-*.jar --youtube-auth' -ForegroundColor Yellow
}
