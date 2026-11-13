package com.subtitleplayer.prototype

/** Project-written definitions for dialogue vocabulary absent from the packaged index. */
internal val dialogueDictionary = buildMap<String, WordMeaning> {
    fun define(headword: String, persian: String, vararg forms: String) {
        val meaning = WordMeaning(headword, persian)
        put(headword, meaning)
        forms.forEach { put(it, meaning) }
    }

    define("adulting", "انجام دادن مسئولیت‌های روزمرهٔ بزرگسالی (محاوره‌ای)")
    define("binge-watch", "چند قسمت یا چند فیلم را پشت سر هم تماشا کردن",
        "binge watch", "binge-watching", "binge watching", "binge-watched", "binge watched")
    define("bookmarking", "نشانک‌گذاری برای پیدا کردن دوبارهٔ صفحه یا موقعیت")
    define("call it a day", "برای امروز دست از کار کشیدن؛ کار را همین‌جا تمام کردن")
    define("catch off guard", "غافلگیر کردن؛ کسی را در موقعیتی که آمادگی ندارد گیر انداختن", "caught off guard")
    define("catfished", "با هویت جعلی در اینترنت فریب داده شد")
    define("clickbait", "عنوان یا محتوای اغراق‌آمیز برای ترغیب به کلیک")
    define("cringeworthy", "به‌شدت خجالت‌آور یا معذب‌کننده", "cringey")
    define("cut to the chase", "رفتن سر اصل مطلب؛ از مقدمه گذشتن")
    define("doomscroll", "مدام خبرهای بد را در گوشی یا شبکه‌های اجتماعی مرور کردن", "doomscrolling")
    define("ghosted", "بی‌خبر ارتباط را قطع کرد و دیگر جواب نداد (محاوره‌ای)")
    define("hangry", "از گرسنگی عصبی و بداخلاق (محاوره‌ای)")
    define("hard pass", "قطعاً نه؛ رد قاطعانهٔ یک پیشنهاد (محاوره‌ای)")
    define("long story short", "خلاصه اینکه؛ کوتاه بگویم")
    define("mansplain", "چیزی را با لحنی از بالا به پایین برای زنی توضیح دادن، با این فرض که خودش نمی‌داند", "mansplaining")
    define("muted", "بی‌صدا یا قطع‌شده (صدا)؛ ملایم و کم‌رنگ (رنگ)")
    define("my bad", "اشتباه از من بود (محاوره‌ای)")
    define("no biggie", "چیز مهمی نیست؛ مسئله‌ای نیست (محاوره‌ای)")
    define("out of the loop", "بی‌خبر از جریان امور؛ در جریان نبودن")
    define("overshare", "بیش از حد دربارهٔ مسائل شخصی گفتن")
    define("plot twist", "چرخش غیرمنتظره در داستان")
    define("read the room", "حال‌وهوای جمع را فهمیدن و رفتار را با آن هماهنگ کردن")
    define("screenshot", "تصویر ثبت‌شده از صفحهٔ نمایش؛ از صفحه عکس گرفتن")
    define("spill the tea", "خبر یا حرف پشت‌پرده را تعریف کردن (محاوره‌ای)")
    define("spoiler alert", "هشدار لو رفتن بخشی از داستان")
    define("underwhelming", "کمتر از حد انتظار؛ ناامیدکننده یا کم‌تأثیر")
    define("unmute", "صدا یا میکروفون را دوباره فعال کردن", "unmuted")
    define("walk out on", "کسی را رها کردن و رفتن", "walked out on")
}
