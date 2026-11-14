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
    define("bring it up", "آن موضوع را مطرح کردن؛ پیش کشیدن آن در گفت‌وگو",
        "brings it up", "brought it up", "bringing it up")
    define("bring up", "مطرح کردن یک موضوع؛ پیش کشیدن آن در گفت‌وگو")
    define("call it a day", "برای امروز دست از کار کشیدن؛ کار را همین‌جا تمام کردن")
    define("catch off guard", "غافلگیر کردن؛ کسی را در موقعیتی که آمادگی ندارد گیر انداختن", "caught off guard")
    define("catfished", "با هویت جعلی در اینترنت فریب داده شد")
    define("clickbait", "عنوان یا محتوای اغراق‌آمیز برای ترغیب به کلیک")
    define("could've", "شکل کوتاه could have؛ می‌توانست یا ممکن بود کاری انجام شود")
    define("couldn't've", "شکل کوتاه couldn't have؛ نمی‌توانسته یا امکان نداشته کاری انجام شده باشد")
    define("cringeworthy", "به‌شدت خجالت‌آور یا معذب‌کننده", "cringey")
    define("cut to the chase", "رفتن سر اصل مطلب؛ از مقدمه گذشتن")
    define("doomscroll", "مدام خبرهای بد را در گوشی یا شبکه‌های اجتماعی مرور کردن", "doomscrolling")
    define("dunno", "نمی‌دانم؛ شکل محاوره‌ای don't know")
    define("get the hang of it", "کم‌کم قلق کار را یاد گرفتن", "got the hang of it", "getting the hang of it")
    define("ghosted", "بی‌خبر ارتباط را قطع کرد و دیگر جواب نداد (محاوره‌ای)")
    define("gonna", "شکل محاوره‌ای going to؛ قرار است کاری انجام شود")
    define("gotta", "شکل محاوره‌ای got to یا have got to؛ باید")
    define("hangry", "از گرسنگی عصبی و بداخلاق (محاوره‌ای)")
    define("hard pass", "قطعاً نه؛ رد قاطعانهٔ یک پیشنهاد (محاوره‌ای)")
    define("kinda", "یه جورایی؛ تا حدی (شکل محاوره‌ای kind of)")
    define("lemme", "بگذار من…؛ شکل محاوره‌ای let me")
    define("long story short", "خلاصه اینکه؛ کوتاه بگویم")
    define("mansplain", "چیزی را با لحنی از بالا به پایین برای زنی توضیح دادن، با این فرض که خودش نمی‌داند", "mansplaining")
    define("might've", "شکل کوتاه might have؛ شاید کاری انجام شده باشد")
    define("must've", "شکل کوتاه must have؛ حتماً یا به‌احتمال زیاد کاری انجام شده است")
    define("muted", "بی‌صدا یا قطع‌شده (صدا)؛ ملایم و کم‌رنگ (رنگ)")
    define("my bad", "اشتباه از من بود (محاوره‌ای)")
    define("no biggie", "چیز مهمی نیست؛ مسئله‌ای نیست (محاوره‌ای)")
    define("on the house", "رایگان به حساب رستوران یا صاحب کسب‌وکار؛ مهمان ما")
    define("out of the loop", "بی‌خبر از جریان امور؛ در جریان نبودن")
    define("overshare", "بیش از حد دربارهٔ مسائل شخصی گفتن")
    define("plot twist", "چرخش غیرمنتظره در داستان")
    define("put it off", "آن کار را به تعویق انداختن؛ انجامش را به بعد موکول کردن",
        "puts it off", "putting it off")
    define("put off", "به تعویق انداختن؛ انجام چیزی را به بعد موکول کردن")
    define("read the room", "حال‌وهوای جمع را فهمیدن و رفتار را با آن هماهنگ کردن")
    define("run it by me", "موضوع یا پیشنهاد را با من در میان بگذار تا نظر بدهم", "ran it by me")
    define("screenshot", "تصویر ثبت‌شده از صفحهٔ نمایش؛ از صفحه عکس گرفتن")
    define("shouldn't've", "شکل کوتاه shouldn't have؛ نباید کاری انجام می‌شد")
    define("sorta", "یه جورایی؛ تا حدی (شکل محاوره‌ای sort of)")
    define("spill the tea", "خبر یا حرف پشت‌پرده را تعریف کردن (محاوره‌ای)")
    define("spoiler alert", "هشدار لو رفتن بخشی از داستان")
    define("start over", "دوباره از اول شروع کردن؛ از نو آغاز کردن",
        "starts over", "started over", "starting over")
    define("take a rain check", "فعلاً نپذیرفتن یک دعوت و موکول کردنش به فرصت دیگر")
    define("underwhelming", "کمتر از حد انتظار؛ ناامیدکننده یا کم‌تأثیر")
    define("unmute", "صدا یا میکروفون را دوباره فعال کردن", "unmuted")
    define("walk out on", "کسی را رها کردن و رفتن", "walked out on")
    define("wanna", "شکل محاوره‌ای want to؛ خواستن انجام کاری")
    define("we'd've", "شکل کوتاه we would have؛ در آن صورت کاری را انجام می‌دادیم")
    define("would've", "شکل کوتاه would have؛ در آن صورت کاری انجام می‌شد")
    define("y'all", "همهٔ شما؛ خطاب محاوره‌ای به چند نفر")
}
