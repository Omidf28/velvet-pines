$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildFile = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'app/build.gradle.kts')
$version = [regex]::Match($buildFile, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if ($version -notmatch '^[A-Za-z0-9][A-Za-z0-9.+-]*$') { throw 'Application version not found or invalid' }
$outputRoot = Join-Path $projectRoot 'app/build/outputs/test-kits'
$kitRoot = Join-Path $outputRoot ('sidecars-' + [guid]::NewGuid().ToString('N'))
$cases = @{
    '01-auto' = @('Episode.en.srt')
    '02-choose' = @('Episode.en.srt', 'Episode.eng.srt')
    '03-untagged' = @('Episode.srt')
}
foreach ($entry in $cases.GetEnumerator()) {
    $caseRoot = Join-Path $kitRoot $entry.Key
    New-Item -ItemType Directory -Force -Path $caseRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'app/src/main/assets/demo.mp4') -Destination (Join-Path $caseRoot 'Episode.mp4')
    foreach ($name in $entry.Value) {
        Copy-Item -LiteralPath (Join-Path $projectRoot 'app/src/main/assets/demo.srt') -Destination (Join-Path $caseRoot $name)
    }
}
$guide = @"
Velvet Pines - آزمون زیرنویس کنار فیلم

این فیلم و زیرنویس‌ها همان نمونه‌های مصنوعی همراه برنامه هستند.

۱. ZIP را به گوشی منتقل و استخراج کنید.
۲. در کتابخانهٔ برنامه، پوشهٔ استخراج‌شده را انتخاب و اجازهٔ خواندن بدهید.
۳. Episode.mp4 در پوشهٔ 01-auto باید برای نخستین بار Episode.en.srt را خودکار باز کند.
۴. در پوشهٔ 02-choose دو گزینهٔ هم‌ارزش وجود دارد؛ از منوی زیرنویس یکی را انتخاب کنید.
۵. در پوشهٔ 03-untagged زبان فایل مشخص نشده است؛ فایل فقط برای انتخاب دستی در منو پیشنهاد می‌شود.
۶. پس از انتخاب یا خاموش‌کردن زیرنویس، برنامه را ببندید و فیلم را از «اخیرها» باز کنید؛ انتخاب قبلی باید حفظ شود.

برای تست مجدد حالت خودکار، یک کپی تازه از پوشهٔ آزمون بسازید. انتخاب دستی یا خاموش‌شدهٔ قبلی عمداً تغییر نمی‌کند.
"@
Set-Content -LiteralPath (Join-Path $kitRoot 'README.txt') -Value $guide -Encoding UTF8
$zipPath = Join-Path $outputRoot ('Velvet-Pines-' + $version + '-sidecar-test.zip')
Compress-Archive -Path (Join-Path $kitRoot '*') -DestinationPath $zipPath -Force
Get-Item -LiteralPath $zipPath | Select-Object FullName, Length
