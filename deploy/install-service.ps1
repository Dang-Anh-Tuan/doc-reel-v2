<#
.SYNOPSIS
  Cài DocReel thành Windows service bằng WinSW. Chạy bằng PowerShell quyền Administrator.
.DESCRIPTION
  Việc script làm: tạo thư mục cài đặt, copy jar + cấu hình WinSW, đăng ký service, đăng ký tác vụ dọn log
  (giữ 14 ngày), chạy `--doctor` rồi mới start service. Script KHÔNG đọc, in hay ghi giá trị trong .env;
  chỉ kiểm file có tồn tại.
.PARAMETER Jar        Đường dẫn docreel.jar (mặc định: jar mới nhất trong target\)
.PARAMETER WinSW      Đường dẫn WinSW-x64.exe đã tải (https://github.com/winsw/winsw/releases)
.PARAMETER InstallDir Thư mục cài (mặc định C:\docreel)
.PARAMETER EnvFile    File .env có bí mật (mặc định .env ở gốc repo); được copy nguyên văn vào InstallDir
.PARAMETER LogDays    Số ngày giữ log (mặc định 14)
.PARAMETER Uninstall  Gỡ service và tác vụ dọn log (không xóa dữ liệu)
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File deploy\install-service.ps1 -WinSW C:\tools\WinSW-x64.exe
#>
param(
    [string]$Jar,
    [string]$WinSW,
    [string]$InstallDir = 'C:\docreel',
    [string]$EnvFile,
    [int]$LogDays = 14,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$serviceExe = Join-Path $InstallDir 'docreel-service.exe'
$taskName = 'DocReel-LogCleanup'

function Assert-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not ([Security.Principal.WindowsPrincipal]$id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Cần chạy PowerShell với quyền Administrator.'
    }
}

Assert-Admin

if ($Uninstall) {
    if (Test-Path $serviceExe) {
        & $serviceExe stop | Out-Null
        & $serviceExe uninstall
    }
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host 'Đã gỡ service và tác vụ dọn log. Dữ liệu trong' $InstallDir 'được giữ nguyên.'
    return
}

if (-not $Jar) {
    $latest = Get-ChildItem (Join-Path $repo 'target') -Filter 'docreel-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'original|sources' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) { throw 'Không thấy jar. Chạy `.\mvnw.cmd package` trước hoặc truyền -Jar.' }
    $Jar = $latest.FullName
}
if (-not (Test-Path $Jar)) { throw "Không thấy jar: $Jar" }
if (-not $WinSW -or -not (Test-Path $WinSW)) { throw 'Truyền -WinSW tới WinSW-x64.exe (tải từ trang release của WinSW).' }
if (-not $EnvFile) { $EnvFile = Join-Path $repo '.env' }
if (-not (Test-Path $EnvFile)) {
    Write-Warning "Không thấy ${EnvFile}: service vẫn cài được nhưng thiếu key ElevenLabs / YouTube / DB. Tạo .env rồi chép vào $InstallDir."
}

New-Item -ItemType Directory -Force -Path $InstallDir, (Join-Path $InstallDir 'logs'), (Join-Path $InstallDir 'data-root') | Out-Null
if (Test-Path $serviceExe) {
    & $serviceExe stop 2>$null | Out-Null
    & $serviceExe uninstall 2>$null | Out-Null
}
Copy-Item $Jar (Join-Path $InstallDir 'docreel.jar') -Force
Copy-Item $WinSW $serviceExe -Force

# Service chạy bằng LocalSystem → không có PATH người dùng (winget/pip cài vào profile) và không thấy
# %LOCALAPPDATA%\ms-playwright. Ghi đường dẫn tuyệt đối của công cụ vào XML (không có bí mật).
function Resolve-Tool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $cmd) { throw "Không thấy '$name' trong PATH của người cài. Cài rồi chạy lại script." }
    return $cmd.Source
}
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} else { Resolve-Tool 'java' }
$toolEnv = [ordered]@{
    FFMPEG_PATH = Resolve-Tool 'ffmpeg'
    FFPROBE_PATH = Resolve-Tool 'ffprobe'
    DOCREEL_TTS_EDGE_COMMAND = Resolve-Tool 'edge-tts'
}
$browsers = if ($env:PLAYWRIGHT_BROWSERS_PATH) { $env:PLAYWRIGHT_BROWSERS_PATH } else { Join-Path $env:LOCALAPPDATA 'ms-playwright' }
if (Test-Path $browsers) { $toolEnv['PLAYWRIGHT_BROWSERS_PATH'] = $browsers }
else { Write-Warning "Không thấy $browsers (Chromium của Playwright): --doctor sẽ báo Chromium CRITICAL." }

$esc = { param($s) [Security.SecurityElement]::Escape($s) }
$envXml = ($toolEnv.GetEnumerator() | ForEach-Object { "  <env name=`"$($_.Key)`" value=`"$(& $esc $_.Value)`"/>" }) -join "`r`n"
$xml = Get-Content (Join-Path $PSScriptRoot 'docreel-service.xml') -Raw -Encoding UTF8
$xml = $xml.Replace('<executable>java</executable>', "<executable>$(& $esc $java)</executable>").Replace('<!--TOOL_ENV-->', $envXml)
[IO.File]::WriteAllText((Join-Path $InstallDir 'docreel-service.xml'), $xml, (New-Object Text.UTF8Encoding $false))
if (Test-Path $EnvFile) { Copy-Item $EnvFile (Join-Path $InstallDir '.env') -Force }
# Thư mục config/ (lexicon, ...) cạnh jar
if (Test-Path (Join-Path $repo 'config')) {
    Copy-Item (Join-Path $repo 'config') (Join-Path $InstallDir 'config') -Recurse -Force
}

# .env chỉ cho Administrators + SYSTEM + tài khoản hiện tại đọc
$envTarget = Join-Path $InstallDir '.env'
if (Test-Path $envTarget) {
    icacls $envTarget /inheritance:r /grant:r 'Administrators:F' 'SYSTEM:F' "$($env:USERNAME):R" | Out-Null
}

# Tác vụ dọn log hằng ngày 03:00: xóa log cũ hơn $LogDays ngày (WinSW không tự xóa)
$cleanup = "Get-ChildItem '$InstallDir\logs' -File | Where-Object { `$_.LastWriteTime -lt (Get-Date).AddDays(-$LogDays) } | Remove-Item -Force"
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -WindowStyle Hidden -Command `"$cleanup`""
$trigger = New-ScheduledTaskTrigger -Daily -At 3am
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -User 'SYSTEM' -RunLevel Highest -Force | Out-Null

& $serviceExe install

# Kiểm tra máy trước khi chạy: --doctor đọc đúng .env trong InstallDir
Push-Location $InstallDir
try {
    $env:DOCREEL_HOME = Join-Path $InstallDir 'data-root'
    foreach ($kv in $toolEnv.GetEnumerator()) { Set-Item -Path "env:$($kv.Key)" -Value $kv.Value }
    & $java -jar (Join-Path $InstallDir 'docreel.jar') --doctor
    $doctorExit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($doctorExit -ne 0) {
    Write-Warning '--doctor có mục CRITICAL: service đã đăng ký nhưng CHƯA start. Sửa theo hướng dẫn trong bảng rồi chạy: docreel-service.exe start'
    exit 1
}

& $serviceExe start
Write-Host "Đã cài và chạy service 'docreel' tại $InstallDir. Log: $InstallDir\logs (giữ $LogDays ngày)."
