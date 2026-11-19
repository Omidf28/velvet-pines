package com.subtitleplayer.prototype

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

private val lexiconInstallLock = Any()
private val verifiedLexiconPaths = mutableSetOf<String>()

/** Install immutable dictionary assets on IO, independently of the user's learning database. */
internal fun preparePackagedLexicon(
    context: Context,
    assetFolder: String,
    filePrefix: String,
    compressedName: String,
    maxBytes: Long
): File = synchronized(lexiconInstallLock) {
    val manifest = context.assets.open("$assetFolder/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
    val hash = manifest.getString("databaseSha256")
    require(hash.matches(Regex("[a-f0-9]{64}")))
    val expectedBytes = manifest.getLong("databaseBytes")
    require(expectedBytes in 1..maxBytes)
    val file = File(context.noBackupFilesDir, "$filePrefix-$hash.sqlite")
    if (file.path in verifiedLexiconPaths && file.length() == expectedBytes) return@synchronized file
    fun digest(target: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sha.update(buffer, 0, count)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
    if (file.length() != expectedBytes || digest(file) != hash) {
        val temp = File(context.noBackupFilesDir, "$filePrefix-install.tmp")
        try {
            GZIPInputStream(context.assets.open("$assetFolder/$compressedName"), 32768).use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(32768)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= expectedBytes) { "Lexicon too large" }
                        output.write(buffer, 0, count)
                    }
                    check(total == expectedBytes) { "Incomplete lexicon" }
                }
            }
            check(digest(temp) == hash) { "Lexicon checksum mismatch" }
            check(temp.renameTo(file)) { "Lexicon installation failed" }
        } finally {
            temp.delete()
        }
    }
    verifiedLexiconPaths.add(file.path)
    val cacheName = Regex("${Regex.escape(filePrefix)}-[a-f0-9]{64}\\.sqlite")
    context.noBackupFilesDir.listFiles()?.filter {
        it.name != file.name && cacheName.matches(it.name)
    }?.forEach { it.delete() }
    file
}
