package com.subtitleplayer.prototype

import java.util.concurrent.CancellationException

internal enum class LookupFailure(val message: String) {
    OfflineUnavailable("Could not open the offline dictionary. Check available storage and try again."),
    Connection("Could not connect to the translation service. Check your connection."),
    Timeout("Translation timed out. Try again."),
    Service("The translation service is unavailable."),
    Quota("The service has reached its request limit. Try again later."),
    InvalidResponse("No valid Persian translation received.")
}
internal class OnlineLookupException(val failure: LookupFailure, cause: Throwable? = null) : Exception(failure.message, cause)
internal enum class WordLookupRequest { Offline, Online }

/** A network intent is consumed once; personal-dictionary emissions cannot replay it. */
internal class WordLookupRequestGate {
    private var pending = WordLookupRequest.Offline
    fun online() { pending = WordLookupRequest.Online }
    fun offline() { pending = WordLookupRequest.Offline }
    fun consume(): WordLookupRequest = pending.also { pending = WordLookupRequest.Offline }
}

private class LookupCache<V>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val values = LinkedHashMap<String, V>(capacity, 0.75f, true)
    @Synchronized fun get(key: String): V? = values[key]
    @Synchronized fun put(key: String, value: V) {
        values[key] = value
        while (values.size > capacity) values.remove(values.entries.first().key)
    }
}

/** Memory-only, bounded caches; personal definitions are always checked before cached results. */
internal class WordLookupService(
    private val offlineLookup: (String) -> DictionaryResult,
    private val onlineLookup: (String, () -> Unit) -> WordMeaning,
    offlineCapacity: Int = 256,
    onlineCapacity: Int = 64
) {
    private val offline = LookupCache<DictionaryResult>(offlineCapacity)
    private val online = LookupCache<DictionaryResult>(onlineCapacity)

    fun resolve(
        term: String,
        personal: Map<String, WordMeaning>,
        request: WordLookupRequest = WordLookupRequest.Offline,
        checkCancelled: () -> Unit = {}
    ): DictionaryResult {
        checkCancelled()
        val key = normalizeDictionaryTerm(term) ?: return DictionaryResult(null, "This term cannot be looked up")
        personal[key]?.let { return DictionaryResult(it, "Personal dictionary") }
        if (request == WordLookupRequest.Offline) {
            val result = offline.get(key) ?: try {
                offlineLookup(key).also {
                    checkCancelled()
                    if (it.failure == null) offline.put(key, it)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failedLookup(LookupFailure.OfflineUnavailable) }
            if (result.meaning != null) return result
            return online.get(key)?.copy(source = "Machine translation · MyMemory · Cached") ?: result
        }
        online.get(key)?.let { return it.copy(source = "Machine translation · MyMemory · Cached") }
        return try {
            val translated = onlineLookup(key, checkCancelled)
            checkCancelled()
            if (!validOnlineDefinition(translated.persian)) throw OnlineLookupException(LookupFailure.InvalidResponse)
            DictionaryResult(WordMeaning(key, translated.persian), "Machine translation · MyMemory").also { online.put(key, it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: OnlineLookupException) { failedLookup(error.failure) }
        catch (_: Exception) { failedLookup(LookupFailure.Service) }
    }
}

internal fun failedLookup(failure: LookupFailure) = DictionaryResult(null, failure.message, failure)
