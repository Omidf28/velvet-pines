package com.subtitleplayer.prototype

import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/** Only invoked by the explicit online button; sends the selected term, never the subtitle. */
internal fun translateSelectedTerm(term: String): WordMeaning {
    val key = normalizeDictionaryTerm(term) ?: error("واژه قابل ترجمه نیست")
    require(key.toByteArray(Charsets.UTF_8).size <= 500)
    val query = URLEncoder.encode(key, "UTF-8")
    val connection = URL("https://api.mymemory.translated.net/get?q=$query&langpair=en%7Cfa")
        .openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = false
        check(connection.responseCode == 200) { "سرویس ترجمه در دسترس نیست" }
        val bytes = connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (output.size() <= 65536) {
                val n = input.read(buffer, 0, minOf(buffer.size, 65537 - output.size()))
                if (n < 0) break
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
        check(bytes.size <= 65536) { "پاسخ ترجمه نامعتبر است" }
        val response = JSONObject(String(bytes, Charsets.UTF_8))
        check(response.optInt("responseStatus") == 200 && !response.optBoolean("quotaFinished")) {
            "سهمیه یا سرویس ترجمه فعلاً در دسترس نیست"
        }
        val value = response.getJSONObject("responseData").getString("translatedText").trim()
        check(validOnlineDefinition(value)) {
            "ترجمهٔ فارسی معتبری دریافت نشد"
        }
        return WordMeaning(key, value)
    } finally { connection.disconnect() }
}
