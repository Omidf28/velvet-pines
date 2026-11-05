package com.subtitleplayer.prototype

internal enum class VideoScaleMode(val label: String, val description: String) {
    Fit("نمایش کامل", "تمام تصویر با نسبت اصلی دیده می‌شود؛ ممکن است حاشیهٔ سیاه داشته باشد."),
    Crop("پر کردن با برش", "نسبت تصویر حفظ می‌شود و لبه‌ها برای پر شدن صفحه بریده می‌شوند."),
    Stretch("کشیدن تصویر", "تصویر تمام صفحه را می‌پوشاند؛ شکل چهره‌ها و اشیا ممکن است کشیده شود.")
}
