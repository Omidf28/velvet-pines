package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackHelp(skipSeconds: Int) {
    PinesIdentityBanner(compact = true)
    Text("نسخهٔ آزمایشی ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    HelpCard("کنترل تصویر",
        "یک لمس روی فضای خالی تصویر، کنترل‌های شناور را نشان می‌دهد یا پنهان می‌کند و فیلم کوچک نمی‌شود. کنترل‌ها هنگام پخش خودکار محو می‌شوند. در حالت افقی، پخش تمام‌صفحه است. دو ضربه در فضای خالی سمت چپ: $skipSeconds ثانیه عقب؛ سمت راست: جلو. کشیدن عمودی سمت چپ، روشنایی و سمت راست، صدای همین پلیر را تغییر می‌دهد. قفل، این لمس‌ها را غیرفعال می‌کند.")
    HelpCard("کشف معنی",
        "روی واژه یا عبارت انگلیسی بزنید. معنی روی همان تصویر سه ثانیه نمایش داده می‌شود. گزینه‌های ویرایش و تلفظ از منوی سه‌نقطهٔ کارت در دسترس‌اند. در بخش مطالعه، مکث هنگام لمس را روشن کنید تا کارت باز بماند. معنی‌های داخلی محدودند؛ از کارت می‌توانید معنی خودتان را اضافه کنید.")
    HelpCard("زیرنویس دوزبانه",
        "از منوی زیرنویس، فایل انگلیسی و فارسی را جدا انتخاب کنید. در بخش زیرنویسِ تنظیمات، نمایش، تأخیر، کدگذاری و ظاهر را تغییر دهید. اگر متن ناخواناست، ابتدا کدگذاری خودکار و سپس کدگذاری مناسب فایل را امتحان کنید.")
    HelpCard("تکرار و شنیدن",
        "A را در شروع و B را دست‌کم نیم‌ثانیه بعد ثبت کنید تا بازه تکرار شود. لغو تکرار یا پرش دستی، آن را تمام می‌کند. دیالوگ قبلی/بعدی و مکث پایان جمله برای فایل زیرنویس جداگانه‌اند.")
    HelpCard("دفترچه و تمرین",
        "واژه را از کارت معنی ذخیره کنید؛ سپس «واژه‌ها» را باز کنید. جست‌وجو، انتخاب فیلم، مرور، آمار و خروجی CSV و Anki در دفترچه‌اند. تلفظ به صدای آفلاین انگلیسی نصب‌شده روی گوشی نیاز دارد.")
    HelpCard("نگه‌داری داده‌ها",
        "از دفترچه، واژه‌نامهٔ شخصی را مدیریت یا فایل آن را وارد کنید. پشتیبان یادگیری، کارت‌ها و پیشرفت مرور را نگه می‌دارد؛ ویدیوها و مجوز فایل‌های دستگاه داخل آن نیستند.")
}

@Composable
private fun HelpCard(title: String, description: String) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
