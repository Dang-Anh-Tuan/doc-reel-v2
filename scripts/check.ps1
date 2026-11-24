<#
.SYNOPSIS
  Kiểm tra toàn bộ DocReel (PLAN 2.7). Thoát mã 1 nếu có bước KHÔNG ĐẠT.
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check.ps1 -Media -E2E
#>
param(
    [switch]$Media,   # thêm: ./mvnw verify -Pmedia (cần ffmpeg + Chromium)
    [switch]$E2E      # thêm: ./mvnw verify -Pe2e
)

$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$mvnw = Join-Path $root 'mvnw.cmd'
$web = Join-Path $root 'web'

# Vitest cần Node >= 22.22 (SPEC 7.1). Node trên PATH cũ hơn → dùng bản nvm v22.22.0 nếu có (chỉ trong phiên này).
$nodeOk = $false
try { $nodeOk = ([version]((node -v) -replace '^v', '')) -ge [version]'22.22.0' } catch { }
$nvm22 = Join-Path $env:LOCALAPPDATA 'nvm\v22.22.0'
if (-not $nodeOk -and (Test-Path (Join-Path $nvm22 'node.exe'))) {
    $env:Path = "$nvm22;$env:Path"
    Write-Host "Node trên PATH < 22.22 → dùng $nvm22 cho phiên này" -ForegroundColor Yellow
} elseif (-not $nodeOk) {
    Write-Host 'CẢNH BÁO: cần Node >= 22.22 cho web (nvm install 22.22.0; nvm use 22.22.0)' -ForegroundColor Yellow
}

$results = New-Object System.Collections.Generic.List[object]

function Invoke-Step {
    param([string]$Name, [scriptblock]$Block)
    Write-Host ''
    Write-Host "==> $Name" -ForegroundColor Cyan
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $global:LASTEXITCODE = 0
    try {
        & $Block | Out-Host
        $ok = ($LASTEXITCODE -eq 0)
    } catch {
        Write-Host $_ -ForegroundColor Red
        $ok = $false
    }
    $sw.Stop()
    $results.Add([pscustomobject]@{ Step = $Name; Result = $(if ($ok) { 'ĐẠT' } else { 'KHÔNG ĐẠT' }); Seconds = [int]$sw.Elapsed.TotalSeconds })
    return $ok
}

function Skip-Step {
    param([string]$Name, [string]$Reason)
    $results.Add([pscustomobject]@{ Step = $Name; Result = "BỎ QUA ($Reason)"; Seconds = 0 })
}

# 1. Định dạng code
Invoke-Step 'spotless:check' { & $mvnw -B -q spotless:check } | Out-Null

# 2. Build + test: compile -Werror, ArchUnit, unit, integration, contract, JaCoCo
Invoke-Step 'mvnw verify' { & $mvnw -B verify '-Dskip.web=true' } | Out-Null   # web build ở bước 3

# 3. Web
Push-Location $web
try {
    $webReady = $true
    if (-not (Test-Path (Join-Path $web 'node_modules'))) {
        $webReady = Invoke-Step 'web: npm ci' { npm ci }
    }
    if ($webReady) {
        Invoke-Step 'web: lint' { npm run lint } | Out-Null
        Invoke-Step 'web: typecheck' { npm run typecheck } | Out-Null
        Invoke-Step 'web: test' { npm test } | Out-Null
        Invoke-Step 'web: build' { npm run build } | Out-Null
    } else {
        foreach ($s in 'web: lint', 'web: typecheck', 'web: test', 'web: build') { Skip-Step $s 'npm ci lỗi' }
    }
} finally {
    Pop-Location
}

# 4–5. Tùy chọn
if ($Media) { Invoke-Step 'mvnw verify -Pmedia' { & $mvnw -B verify -Pmedia '-Dskip.web=true' } | Out-Null }
if ($E2E) { Invoke-Step 'mvnw verify -Pe2e' { & $mvnw -B verify -Pe2e '-Dskip.web=true' } | Out-Null }

Write-Host ''
Write-Host '==================== TỔNG KẾT ====================' -ForegroundColor Cyan
$results | Format-Table -AutoSize | Out-String | Write-Host
$failed = @($results | Where-Object { $_.Result -ne 'ĐẠT' })
if ($failed.Count -gt 0) {
    Write-Host "KẾT QUẢ: KHÔNG ĐẠT ($($failed.Count) bước)" -ForegroundColor Red
    exit 1
}
Write-Host 'KẾT QUẢ: ĐẠT' -ForegroundColor Green
exit 0