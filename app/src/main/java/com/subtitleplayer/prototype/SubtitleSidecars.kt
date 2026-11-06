package com.subtitleplayer.prototype

import java.text.Normalizer
import java.util.Locale

internal enum class SidecarScanStatus { Idle, Loading, Ready, Unreadable, Failed }

internal data class SidecarFile(val id: String, val name: String)
internal data class SidecarMatch(
    val file: SidecarFile,
    val english: Boolean,
    val captions: Boolean = false,
    val forced: Boolean = false
)
internal data class SidecarMatches(val matches: List<SidecarMatch> = emptyList()) {
    val automatic: SidecarMatch? get() {
        val eligible = matches.filter { it.english && !it.forced }
        val ordinary = eligible.filterNot { it.captions }
        return (ordinary.ifEmpty { eligible }).singleOrNull()
    }
}

private val englishSidecarTag = Regex("(?:en|eng)(?:[-_](?:[a-z]{2}|[0-9]{3}))?")
private fun foldedFilename(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT)

/** Match the entire video basename; never infer a parent directory from a document ID. */
internal fun matchSubtitleSidecars(videoName: String, files: List<SidecarFile>): SidecarMatches {
    val folded = foldedFilename(videoName)
    val stem = folded.substringBeforeLast('.', folded)
    if (stem.isBlank()) return SidecarMatches()
    val matches = files.distinctBy { it.id }.mapNotNull { file ->
        val name = foldedFilename(file.name)
        if (name.substringAfterLast('.', "") !in setOf("srt", "vtt")) return@mapNotNull null
        val base = name.substringBeforeLast('.')
        if (base == stem) return@mapNotNull SidecarMatch(file, english = false)
        if (!base.startsWith("$stem.")) return@mapNotNull null
        val tags = base.removePrefix("$stem.").split('.')
        if (tags.count { englishSidecarTag.matches(it) } != 1 ||
            tags.any { !englishSidecarTag.matches(it) && it !in setOf("cc", "sdh", "forced") }) return@mapNotNull null
        SidecarMatch(file, english = true, captions = tags.any { it == "cc" || it == "sdh" }, forced = "forced" in tags)
    }.sortedWith { a, b -> compareNaturalNames(a.file.name, b.file.name) }
    return SidecarMatches(matches)
}

internal data class SubtitleDiscoveryVersion(val video: Long, val preference: Long)
internal fun canApplySidecarAutomatically(
    started: SubtitleDiscoveryVersion,
    current: SubtitleDiscoveryVersion,
    automaticMode: Boolean,
    externalFileSelected: Boolean
): Boolean = started == current && automaticMode && !externalFileSelected
