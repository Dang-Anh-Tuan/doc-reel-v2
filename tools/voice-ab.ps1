<#
.SYNOPSIS
  Tạo mẫu nghe so sánh ElevenLabs (cùng câu, nhiều cấu hình) vào tools\voice-samples\ab\.
  Script tự đọc ELEVENLABS_API_KEY trong .env (người chạy, không in key). Tốn khoảng 1.500 credit (in credit thật mỗi mẫu nếu API trả header character-cost).
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\voice-ab.ps1
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$out = Join-Path $root 'tools\voice-samples\ab'
New-Item -ItemType Directory -Force $out | Out-Null

$key = ((Get-Content .env | Where-Object { $_ -match '^\s*ELEVENLABS_API_KEY\s*=' }) -replace '^\s*ELEVENLABS_API_KEY\s*=', '').Trim().Trim('"', "'")
if (-not $key) { throw 'Không thấy ELEVENLABS_API_KEY trong .env' }
$voice = '2VozU7Otb8AvGqzNAtnE'   # Ngọc Anh (application.yml)

# Đoạn có số + câu hỏi để nghe nhấn nhá, chuẩn hóa số
$raw = 'useEffect chạy lúc nào? Không phải trong lúc render, mà sau khi React đã cập nhật màn hình. Có 3 trường hợp: không truyền mảng, mảng rỗng, và mảng có phụ thuộc. Hàm trả về từ effect là cleanup.'
$lex = 'iu-ì-phếch chạy lúc nào? Không phải trong lúc Ren-đơ, mà sau khi Rì-ách đã cập nhật màn hình. Có 3 trường hợp: không truyền mảng, mảng rỗng, và mảng có phụ thuộc. Hàm trả về từ i-phéc là cờ-lin-ắp.'

$variants = @(
    @{ name = '1-hien-tai_flash_lexicon';         model = 'eleven_flash_v2_5'; text = $lex; lang = $null; vs = $null; norm = $null },
    @{ name = '2-flash_goc_vi_stab0.4';           model = 'eleven_flash_v2_5'; text = $raw; lang = 'vi'; vs = @{ stability = 0.4; similarity_boost = 0.8; style = 0.2; use_speaker_boost = $true }; norm = 'on' },
    @{ name = '3-v3_goc_vi_stab0.5';              model = 'eleven_v3';         text = $raw; lang = 'vi'; vs = @{ stability = 0.5; similarity_boost = 0.8 }; norm = $null },
    @{ name = '4-v4_goc_vi_stab0.5';              model = 'eleven_v4';         text = $raw; lang = 'vi'; vs = @{ stability = 0.5; similarity_boost = 0.8 }; norm = $null },
    @{ name = '5-v4-turbo_goc_vi_stab0.5';        model = 'eleven_v4_turbo';   text = $raw; lang = 'vi'; vs = @{ stability = 0.5; similarity_boost = 0.8 }; norm = $null }
)

foreach ($v in $variants) {
    $body = [ordered]@{ text = $v.text; model_id = $v.model }
    if ($v.lang) { $body.language_code = $v.lang }
    if ($v.vs) { $body.voice_settings = $v.vs }
    if ($v.norm) { $body.apply_text_normalization = $v.norm }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress -Depth 4))
    $file = Join-Path $out "$($v.name).mp3"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Method Post "https://api.elevenlabs.io/v1/text-to-speech/${voice}?output_format=mp3_44100_128" `
            -Headers @{ 'xi-api-key' = $key; Accept = 'audio/mpeg' } -ContentType 'application/json; charset=utf-8' `
            -Body $bytes -OutFile $file -PassThru
        $cost = $resp.Headers['character-cost']
        Write-Host ("OK   {0,-32} {1,5:N0} KB  {2,5:N1}s  credit: {3}" -f $v.name, ((Get-Item $file).Length / 1KB), $sw.Elapsed.TotalSeconds, $(if ($cost) { $cost } else { '?' })) -ForegroundColor Green
    } catch {
        Write-Host "LỖI $($v.name): $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
}Write-Host ''
Write-Host "Nghe các file trong: $out" -ForegroundColor Cyan
Invoke-Item $out
