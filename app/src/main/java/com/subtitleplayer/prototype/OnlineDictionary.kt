package com.subtitleplayer.prototype

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/** Only invoked by an explicit network intent; sends the term, never its subtitle or source. */
internal fun translateSelectedTerm(term: String, checkCancelled: () -> Unit): WordMeaning {
    val key = normalizeDictionaryTerm(term) ?: throw OnlineLookupException(LookupFailure.InvalidResponse)
    require(key.toByteArray(Charsets.UTF_8).size <= 500)
    val query = URLEncoder.encode(key, "UTF-8")
    val connection = URL("https://api.mymemory.translated.net/get?q=$query&langpair=en%7Cfa")
        .openConnection() as HttpsURLConnection
    try {
        checkCancelled()
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = false
        translationStatusFailure(connection.responseCode)?.let { throw OnlineLookupException(it) }
        val bytes = connection.inputStream.use { readTranslationResponse(it, checkCancelled) }
        val response = JSONObject(String(bytes, Charsets.UTF_8))
        translationStatusFailure(response.optInt("responseStatus", -1), response.optBoolean("quotaFinished"))
            ?.let { throw OnlineLookupException(it) }
        val value = response.getJSONObject("responseData").getString("translatedText").trim()
        if (!validOnlineDefinition(value)) throw OnlineLookupException(LookupFailure.InvalidResponse)
        checkCancelled()
        return WordMeaning(key, value)
    } catch (error: SocketTimeoutException) {
        throw OnlineLookupException(LookupFailure.Timeout, error)
    } catch (error: IOException) {
        throw OnlineLookupException(LookupFailure.Connection, error)
    } catch (error: JSONException) {
        throw OnlineLookupException(LookupFailure.InvalidResponse, error)
    } finally { connection.disconnect() }
}
