package com.subtitleplayer.prototype

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val demoVideo = Uri.parse("asset:///demo.mp4")
private val demoSubtitles = Uri.parse("asset:///demo.srt")
private val demoTranslation = Uri.parse("asset:///demo.fa.vtt")
private val background = PinesPalette.Night
private val accent = PinesPalette.Rose
private val Uri.isOnlineVideo: Boolean get() = scheme == "https" || scheme == "rtsp" || scheme == "smb"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var pronunciation: EnglishPronunciation
    private val httpsDataSource by lazy {
        ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { request ->
            if (!request.uri.scheme.equals("https", ignoreCase = true)) {
                throw IOException("Only HTTPS media requests are supported")
            }
            request
        }
    }
    private var videoUri by mutableStateOf<Uri?>(null)
    private var subtitleUri by mutableStateOf<Uri?>(null)
    private var translationUri by mutableStateOf<Uri?>(null)
    private var playbackError by mutableStateOf(false)
    private var videoReady by mutableStateOf(false)
    private var showLibrary by mutableStateOf(false)
    private var libraryFolder by mutableStateOf<Uri?>(null)
    private var recentVideos by mutableStateOf(emptyList<LibraryVideo>())
    private var playbackQueue by mutableStateOf(emptyList<LibraryVideo>())
    private var foreground by mutableStateOf(false)
    private var pictureInPicture by mutableStateOf(false)
    private val supportsPictureInPicture by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    private var tracks by mutableStateOf(Tracks.EMPTY)
    private var embeddedOptions by mutableStateOf(emptyList<EmbeddedSubtitleTrack>())
    private var embeddedTrackKey by mutableStateOf<String?>(null)
    private var embeddedSubtitle by mutableStateOf<ActiveSubtitle?>(null)
    private var playbackSpeed by mutableFloatStateOf(1f)
    private var skipSeconds by mutableIntStateOf(10)
    private var videoScaleMode by mutableStateOf(VideoScaleMode.Fit)
    private var englishSubtitlesVisible by mutableStateOf(true)
    private var persianSubtitlesVisible by mutableStateOf(true)
    private var englishEncoding by mutableStateOf(SubtitleTextEncoding.Auto)
    private var persianEncoding by mutableStateOf(SubtitleTextEncoding.Auto)
    private var pauseOnWordTap by mutableStateOf(false)
    private var pauseAfterDialogue by mutableStateOf(false)
    private var autoPauseEnds: List<Long> = emptyList()
    private var lastDialoguePosition: Long? = null
    private val defaultSubtitleSize by lazy { history.getInt("subtitleSize", 22).coerceIn(16, 32) }
    private var subtitleProfiles = emptyList<SavedVideoSubtitleSettings>()
    private var subtitleSize by mutableIntStateOf(22)
    private var subtitleTextTone by mutableStateOf(SubtitleTextTone.White)
    private var subtitleBackgroundOpacity by mutableFloatStateOf(0.82f)
    private var subtitleDelayMs by mutableLongStateOf(0L)
    private var translationDelayMs by mutableLongStateOf(0L)
    private var sleepDeadlineMs by mutableStateOf<Long?>(null)
    private var sleepRemainingMs by mutableStateOf<Long?>(null)

    private fun expireSleepTimer(): Boolean {
        val deadline = sleepDeadlineMs ?: return false
        if (SystemClock.elapsedRealtime() < deadline) return false
        sleepDeadlineMs = null
        sleepRemainingMs = null
        clearAbLoop()
        replayEndMs = null
        pronunciation.stop()
        player.pause()
        Toast.makeText(this, "زمان تایمر تمام شد؛ پخش مکث شد.", Toast.LENGTH_LONG).show()
        return true
    }

    private var replayEndMs: Long? = null
    private var loopStartMs by mutableStateOf<Long?>(null)
    private var abLoop by mutableStateOf<PlaybackLoop?>(null)

    private fun clearAbLoop() { abLoop = null; loopStartMs = null }
    private val history by lazy { getSharedPreferences("last_playback", MODE_PRIVATE) }
    private val vocabulary by lazy { SavedWordsDatabase.get(applicationContext).words() }
    private val personalDictionary by lazy { SavedWordsDatabase.get(applicationContext).dictionary() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pronunciation = EnglishPronunciation(this)
        pictureInPicture = isInPictureInPictureMode
        playbackSpeed = history.getFloat("speed", 1f)
        skipSeconds = history.getInt("skipSeconds", 10).takeIf { it in seekStepChoices } ?: 10
        englishSubtitlesVisible = history.getBoolean("englishSubtitlesVisible", true)
        persianSubtitlesVisible = history.getBoolean("persianSubtitlesVisible", true)
        englishEncoding = SubtitleTextEncoding.entries.firstOrNull { it.name == history.getString("englishEncoding", null) }
            ?: SubtitleTextEncoding.Auto
        persianEncoding = SubtitleTextEncoding.entries.firstOrNull { it.name == history.getString("persianEncoding", null) }
            ?: SubtitleTextEncoding.Auto
        videoScaleMode = VideoScaleMode.entries.firstOrNull { it.name == history.getString("videoScaleMode", null) }
            ?: VideoScaleMode.Fit
        pauseOnWordTap = history.getBoolean("pauseOnWordTap", false)
        pauseAfterDialogue = history.getBoolean("pauseAfterDialogue", false)
        subtitleSize = defaultSubtitleSize
        subtitleTextTone = SubtitleTextTone.entries.firstOrNull { it.name == history.getString("subtitleTextTone", null) }
            ?: SubtitleTextTone.White
        subtitleBackgroundOpacity = history.getFloat("subtitleBackgroundOpacity", 0.82f)
            .takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.82f
        subtitleProfiles = loadSubtitleProfiles()
        libraryFolder = history.getString("libraryFolder", null)?.let(Uri::parse)
        val readable = persistentReadUris()
        recentVideos = readVideoEntries(history.getString("recentVideos", null), recentVideoLimit)
            .filter { readable.coversReadAccess(it.uri) }
        playbackQueue = readVideoEntries(history.getString("playbackQueue", null), playbackQueueLimit)
            .filter { readable.coversReadAccess(it.uri) }
        showLibrary = savedInstanceState?.getBoolean("showLibrary") == true

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            setPlaybackSpeed(this@MainActivity.playbackSpeed)
            addListener(object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                    embeddedOptions = embeddedTextTracks(newTracks)
                    if (embeddedTrackKey != null) updateEmbeddedTextSelection()
                }

                override fun onCues(cueGroup: CueGroup) {
                    val selected = embeddedOptions.firstOrNull { it.key == embeddedTrackKey }
                    if (selected == null || !selected.group.isTrackSelected(selected.index)) {
                        embeddedSubtitle = null
                        return
                    }
                    val text = cueGroup.cues.mapNotNull { it.text?.toString()?.takeIf(String::isNotBlank) }
                        .joinToString("\n")
                    val timeUs = cueGroup.presentationTimeUs
                    val startMs = if (timeUs >= 0) timeUs / 1_000 else player.currentPosition
                    embeddedSubtitle = text.takeIf(String::isNotBlank)?.let { ActiveSubtitle(it, startMs) }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
                ) {
                    lastDialoguePosition = newPosition.positionMs
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    expireSleepTimer()
                    if (playbackState == Player.STATE_ENDED && pauseAfterDialogue &&
                        abLoop == null && replayEndMs == null && autoPauseEnds.lastOrNull() == player.duration
                    ) player.pause()
                    if (playbackState == Player.STATE_READY) videoReady = true
                    if (playbackState == Player.STATE_ENDED && !player.isCurrentMediaItemLive &&
                        player.playWhenReady && replayEndMs == null && abLoop == null && playbackQueue.isNotEmpty()
                    ) {
                        val endedSource = videoUri
                        window.decorView.post {
                            expireSleepTimer()
                            if (foreground && !showLibrary && videoUri == endedSource &&
                                player.playbackState == Player.STATE_ENDED && player.playWhenReady &&
                                replayEndMs == null && abLoop == null
                            ) {
                                playNextQueuedVideo()
                            }
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        pronunciation.stop()
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = true
                    clearAbLoop()
                    replayEndMs = null
                }
            })
        }
        when {
            savedInstanceState != null -> savedInstanceState.getString("video")?.let { video ->
                openVideo(
                    Uri.parse(video),
                    positionMs = savedInstanceState.getLong("position"),
                    autoplay = false,
                    restoredSubtitles = VideoSubtitleSettings(
                        english = savedInstanceState.getString("subtitle")?.let(Uri::parse),
                        englishDelayMs = savedInstanceState.getLong("subtitleDelayMs"),
                        persian = savedInstanceState.getString("translation")?.let(Uri::parse),
                        persianDelayMs = savedInstanceState.getLong("translationDelayMs"),
                        embeddedTrack = savedInstanceState.getString("embeddedSubtitleTrack"),
                        size = savedInstanceState.getInt("subtitleSize", defaultSubtitleSize)
                    )
                )
            }
            !handleOpenIntent(intent) -> restoreSavedPlayback()
        }

        setContent {
            VelvetPinesTheme {
                PlayerScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleOpenIntent(intent)) showLibrary = false
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pronunciation.stop()
        pictureInPicture = isInPictureInPictureMode
    }

    private fun enterVideoPictureInPicture() {
        if (!supportsPictureInPicture || pictureInPicture || !videoReady || playbackError) return
        pronunciation.stop()

        val size = player.videoSize
        val videoRatio = size.width * size.pixelWidthHeightRatio / size.height
        val ratio = videoRatio.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.42f, 2.38f) ?: (16f / 9f)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
            .build()
        val entered = try {
            enterPictureInPictureMode(params)
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (entered) {
            pictureInPicture = true
        } else {
            Toast.makeText(this, "باز کردن پنجرهٔ تصویر در تصویر ممکن نبود.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        expireSleepTimer()
        pronunciation.refresh()
    }

    override fun onStop() {
        pronunciation.stop()
        foreground = false
        clearAbLoop()
        replayEndMs = null
        player.pause()
        savePlayback()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("showLibrary", showLibrary)
        if (videoUri?.isOnlineVideo == true) return
        outState.putString("video", videoUri?.toString())
        outState.putString("subtitle", subtitleUri?.toString())
        outState.putLong("position", resumePosition())
        outState.putLong("subtitleDelayMs", subtitleDelayMs)
        outState.putString("translation", translationUri?.toString())
        outState.putLong("translationDelayMs", translationDelayMs)
        outState.putString("embeddedSubtitleTrack", embeddedTrackKey)
        outState.putInt("subtitleSize", subtitleSize)
    }

    override fun onDestroy() {
        pronunciation.close()
        player.release()
        super.onDestroy()
    }

    private fun handleOpenIntent(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "content") return false
        keepReadAccess(uri)
        if (history.getString("video", null) == uri.toString()) {
            restoreSavedPlayback()
        } else {
            openVideo(uri)
        }
        return true
    }

    private fun restoreSavedPlayback() {
        val video = history.getString("video", null)?.let(Uri::parse) ?: return
        openVideo(video, history.getLong("position", 0), autoplay = false)
    }

    private fun loadSubtitleProfiles(): List<SavedVideoSubtitleSettings> {
        val saved = history.getString("subtitleProfiles", null)
        if (saved != null) return readVideoSubtitleSettings(saved)
        val video = history.getString("video", null)?.let(Uri::parse) ?: return emptyList()
        return listOf(
            SavedVideoSubtitleSettings(
                video,
                VideoSubtitleSettings(
                    english = history.getString("subtitle", null)?.let(Uri::parse),
                    englishDelayMs = history.getLong("subtitleDelayMs", 0),
                    persian = history.getString("translation", null)?.let(Uri::parse),
                    persianDelayMs = history.getLong("translationDelayMs", 0),
                    embeddedTrack = history.getString("embeddedSubtitleTrack", null),
                    size = defaultSubtitleSize
                )
            )
        )
    }

    private fun VideoSubtitleSettings.withReadableFiles(readable: Set<Uri>): VideoSubtitleSettings {
        val englishFile = english?.takeIf { it == demoSubtitles || readable.coversReadAccess(it) }
        val persianFile = persian?.takeIf { it == demoTranslation || readable.coversReadAccess(it) }
        return copy(
            english = englishFile,
            englishDelayMs = if (englishFile != null) englishDelayMs else 0,
            persian = persianFile,
            persianDelayMs = if (persianFile != null) persianDelayMs else 0
        )
    }

    private fun rememberSubtitleProfile(readable: Set<Uri>) {
        val video = videoUri ?: return
        if (playbackError || (video != demoVideo && !readable.coversReadAccess(video))) return

        val settings = VideoSubtitleSettings(
            english = subtitleUri,
            englishDelayMs = subtitleDelayMs,
            persian = translationUri,
            persianDelayMs = translationDelayMs,
            embeddedTrack = embeddedTrackKey,
            size = subtitleSize
        ).withReadableFiles(readable)
        val key = videoSubtitleKey(video)
        subtitleProfiles = (listOf(SavedVideoSubtitleSettings(video, settings)) +
            subtitleProfiles.filterNot { videoSubtitleKey(it.video) == key }).take(videoSubtitleSettingsLimit)
        history.edit().putString("subtitleProfiles", writeVideoSubtitleSettings(subtitleProfiles)).apply()
    }

    private fun resumePosition(): Long =
        if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition

    private fun savePlayback() {
        val grants = contentResolver.persistedUriPermissions
        val readable = grants.filter { it.isReadPermission }.map { it.uri }.toSet()
        val video = videoUri?.takeIf { it == demoVideo || readable.coversReadAccess(it) }
        rememberSubtitleProfile(readable)
        val edit = history.edit()
            .putFloat("speed", playbackSpeed)
            .putBoolean("pauseOnWordTap", pauseOnWordTap)
        if (video != null && !playbackError) {
            edit.putString("video", video.toString())
                .putLong("position", resumePosition())
        }
        edit.apply()

        val retained = listOfNotNull(
            videoUri, subtitleUri, translationUri, libraryFolder,
            history.getString("video", null)?.let(Uri::parse)
        ) + recentVideos.map(LibraryVideo::uri) + playbackQueue.map(LibraryVideo::uri) +
            subtitleProfiles.flatMap { listOfNotNull(it.video, it.subtitles.english, it.subtitles.persian) }
        val retainedGrants = retained.flatMap { listOf(it, libraryGrantUri(it)) }.toSet()
        for (grant in grants) {
            if (grant.isReadPermission && grant.uri !in retainedGrants) {
                try {
                    contentResolver.releasePersistableUriPermission(grant.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Access may have been revoked since the grant was read.
                }
            }
        }
    }

    private fun openVideo(
        uri: Uri,
        positionMs: Long = 0,
        autoplay: Boolean = true,
        restoredSubtitles: VideoSubtitleSettings? = null,
        onlineFormat: OnlineVideoFormat? = null,
        smbCredentials: SmbCredentials? = null
    ) {
        val readable = persistentReadUris()
        rememberSubtitleProfile(readable)
        val key = videoSubtitleKey(uri)
        val settings = restoredSubtitles
            ?: subtitleProfiles.firstOrNull { videoSubtitleKey(it.video) == key }?.subtitles?.withReadableFiles(readable)
            ?: if (uri == demoVideo) {
                VideoSubtitleSettings(english = demoSubtitles, persian = demoTranslation, size = defaultSubtitleSize)
            } else VideoSubtitleSettings(size = defaultSubtitleSize)

        clearAbLoop()
        autoPauseEnds = emptyList()
        lastDialoguePosition = null
        replayEndMs = null
        embeddedTrackKey = null
        embeddedSubtitle = null
        embeddedOptions = emptyList()
        tracks = Tracks.EMPTY
        videoUri = uri
        subtitleUri = settings.english
        subtitleDelayMs = settings.englishDelayMs
        translationUri = settings.persian
        translationDelayMs = settings.persianDelayMs
        subtitleSize = settings.size
        playbackError = false
        videoReady = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        val item = MediaItem.fromUri(uri)
        if (onlineFormat == null) {
            player.setMediaItem(item, positionMs)
        } else {
            val source = when (onlineFormat) {
                OnlineVideoFormat.Auto -> ProgressiveMediaSource.Factory(httpsDataSource).createMediaSource(item)
                OnlineVideoFormat.Hls -> HlsMediaSource.Factory(httpsDataSource).createMediaSource(item)
                OnlineVideoFormat.Dash -> DashMediaSource.Factory(httpsDataSource).createMediaSource(item)
                OnlineVideoFormat.Rtsp -> RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(item)
                OnlineVideoFormat.Smb -> ProgressiveMediaSource.Factory(
                    DataSource.Factory { SmbVideoDataSource(smbCredentials) }
                ).createMediaSource(item)
            }
            player.setMediaSource(source, positionMs)
        }
        embeddedTrackKey = settings.embeddedTrack.takeIf { settings.english == null }
        player.prepare()
        player.playWhenReady = autoplay
    }

    private fun updateEmbeddedTextSelection() {
        val requested = embeddedOptions.firstOrNull { it.key == embeddedTrackKey }
        val current = player.trackSelectionParameters
        val builder = current.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, requested == null)
        if (requested != null) {
            builder.setOverrideForType(TrackSelectionOverride(requested.group.mediaTrackGroup, requested.index))
        }
        val updated = builder.build()
        if (updated != current) player.trackSelectionParameters = updated
    }

    private fun chooseEmbeddedSubtitle(key: String?) {
        replayEndMs = null
        subtitleUri = null
        subtitleDelayMs = 0
        embeddedSubtitle = null
        embeddedTrackKey = key
        updateEmbeddedTextSelection()
    }

    private fun keepReadAccess(uri: Uri): Boolean = try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        // Some providers only grant access for the current task.
        false
    }

    private fun persistentReadUris(): Set<Uri> = contentResolver.persistedUriPermissions
        .filter { it.isReadPermission }.map { it.uri }.toSet()

    private fun rememberRecentVideo(uri: Uri, name: String) {
        if (!persistentReadUris().coversReadAccess(uri)) return
        recentVideos = addRecentVideo(recentVideos, LibraryVideo(uri, name))
        history.edit().putString("recentVideos", writeVideoEntries(recentVideos)).apply()
    }

    private fun updatePlaybackQueue(entries: List<LibraryVideo>) {
        playbackQueue = entries
        history.edit().putString("playbackQueue", writeVideoEntries(entries)).apply()
    }

    private fun enqueueVideo(video: LibraryVideo) {
        if (playbackQueue.any { it.uri == video.uri }) return
        if (playbackQueue.size >= playbackQueueLimit) {
            Toast.makeText(this, "صف پخش پر است.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!persistentReadUris().coversReadAccess(video.uri)) {
            Toast.makeText(this, "برای دسترسی دوباره، فایل یا پوشه را انتخاب کنید.", Toast.LENGTH_SHORT).show()
            return
        }
        updatePlaybackQueue(playbackQueue + video)
    }

    private fun moveQueuedVideo(uri: Uri, offset: Int) {
        val index = playbackQueue.indexOfFirst { it.uri == uri }
        val target = index + offset
        if (index < 0 || target !in playbackQueue.indices) return
        val ordered = playbackQueue.toMutableList()
        val video = ordered.removeAt(index)
        ordered.add(target, video)
        updatePlaybackQueue(ordered)
    }

    private fun playNextQueuedVideo() {
        if (playbackQueue.isEmpty()) return
        val readable = persistentReadUris()
        val nextIndex = playbackQueue.indexOfFirst { readable.coversReadAccess(it.uri) }
        if (nextIndex < 0) {
            updatePlaybackQueue(emptyList())
            Toast.makeText(this, "فایل‌های صف دیگر قابل دسترسی نیستند.", Toast.LENGTH_SHORT).show()
            return
        }
        val next = playbackQueue[nextIndex]
        showLibrary = false
        openVideo(next.uri)
        updatePlaybackQueue(playbackQueue.drop(nextIndex + 1))
    }

    private fun openLibraryVideo(uri: Uri) {
        if (!persistentReadUris().coversReadAccess(uri)) {
            Toast.makeText(this, "برای دسترسی دوباره، فایل یا پوشه را انتخاب کنید.", Toast.LENGTH_SHORT).show()
            return
        }

        showLibrary = false
        when {
            uri == videoUri && !playbackError -> {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
            history.getString("video", null) == uri.toString() -> {
                restoreSavedPlayback()
                player.play()
            }
            else -> openVideo(uri)
        }
    }

    private fun loadSubtitles(uri: Uri, persian: Boolean, encoding: SubtitleTextEncoding): List<SubtitleCue> {
        val stream = when (uri) {
            demoSubtitles -> assets.open("demo.srt")
            demoTranslation -> assets.open("demo.fa.vtt")
            else -> contentResolver.openInputStream(uri) ?: throw IOException("Subtitle unavailable")
        }
        return stream.use { parseSubtitleFile(decodeSubtitleBytes(readSubtitleBytes(it), persian, encoding)) }
    }

    @Composable
    private fun subtitleTrack(uri: Uri?, encoding: SubtitleTextEncoding, persian: Boolean = false): SubtitleTrack {
        var track by remember(uri, persian, encoding) { mutableStateOf(SubtitleTrack()) }
        LaunchedEffect(uri, persian, encoding) {
            if (uri == null) return@LaunchedEffect
            track = try {
                withContext(Dispatchers.IO) {
                    val cues = loadSubtitles(uri, persian, encoding)
                    SubtitleTrack(cues, failed = cues.isEmpty())
                }
            } catch (_: IOException) {
                SubtitleTrack(failed = true)
            } catch (_: SecurityException) {
                SubtitleTrack(failed = true)
            }
        }
        return track
    }

    private fun readVideoName(uri: Uri): String? {
        if (uri == demoVideo) return "ویدیوی نمونه"
        if (uri.isOnlineVideo) return "ویدیوی آنلاین"
        val column = OpenableColumns.DISPLAY_NAME
        return try {
            contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun saveLookup(lookup: Lookup) {
        val meaning = lookup.meaning ?: return
        try {
            vocabulary.save(
                SavedWord(
                    word = lookup.word,
                    wordKey = normalizeWord(lookup.word),
                    headword = meaning.headword,
                    meaning = meaning.persian,
                    dialogue = lookup.dialogue,
                    positionMs = lookup.positionMs,
                    subtitleStartMs = lookup.subtitleStartMs,
                    sourceUri = if (lookup.source.isOnlineVideo) remoteVideoId(lookup.source.toString())
                    else lookup.source.toString(),
                    sourceName = lookup.sourceName
                )
            )
            lookup.saveState = WordSaveState.Saved
        } catch (_: SQLiteException) {
            lookup.saveState = WordSaveState.Ready
            Toast.makeText(this, "ذخیرهٔ واژه انجام نشد", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PlayerScreen() {
        val englishTrack = subtitleTrack(subtitleUri, englishEncoding)
        val persianTrack = subtitleTrack(translationUri, persianEncoding, persian = true)
        val cues = englishTrack.cues
        var lookup by remember(videoUri, subtitleUri, embeddedTrackKey) { mutableStateOf<Lookup?>(null) }
        var editingMeaning by remember(videoUri, subtitleUri, embeddedTrackKey) { mutableStateOf<Lookup?>(null) }
        val personalEntries by remember { personalDictionary.observeAll() }.collectAsState(initial = null)
        val personalMeanings = remember(personalEntries) {
            personalEntries.orEmpty().associate { it.wordKey to WordMeaning(it.headword, it.meaning) }
        }
        var frame by remember { mutableStateOf(PlaybackFrame()) }
        var showBuffering by remember(videoUri) { mutableStateOf(false) }
        LaunchedEffect(frame.source, frame.buffering, playbackError, foreground) {
            showBuffering = false
            if (foreground && frame.source == videoUri && frame.buffering && !playbackError) {
                delay(350)
                showBuffering = true
            }
        }
        var dragPosition by remember { mutableFloatStateOf(-1f) }
        var viewport by rememberSaveable { mutableStateOf(PlaybackViewport()) }
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val fullscreen = videoUri != null && viewport.immersive(landscape)
        val controlsLocked = viewport.locked
        val controlsTimeoutMs = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
            3_500L, containsIcons = true, containsText = true, containsControls = true
        ) ?: 3_500L
        var controlsVisible by remember(videoUri) { mutableStateOf(true) }
        var controlsInteraction by remember { mutableIntStateOf(0) }
        var showPlayerMenu by remember { mutableStateOf(false) }
        var showPlaybackTools by remember { mutableStateOf(false) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showHelp by rememberSaveable { mutableStateOf(false) }
        var showWords by rememberSaveable { mutableStateOf(false) }
        var showDialogues by remember(videoUri, subtitleUri) { mutableStateOf(false) }
        var showSubtitleMenu by remember(videoUri) { mutableStateOf(false) }
        var sourceName by remember(videoUri) { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(sleepDeadlineMs) {
            val deadline = sleepDeadlineMs ?: return@LaunchedEffect
            while (sleepDeadlineMs == deadline) {
                sleepRemainingMs = sleepRemainingMs(deadline, SystemClock.elapsedRealtime())
                if (expireSleepTimer()) break
                delay(1_000)
            }
        }

        LaunchedEffect(controlsVisible, frame.playing, controlsLocked, dragPosition, showSubtitleMenu,
            showPlayerMenu, showPlaybackTools, showSettings, showDialogues, editingMeaning, controlsInteraction, foreground, controlsTimeoutMs) {
            if (controlsVisible && frame.playing && !controlsLocked && dragPosition < 0 &&
                !showSubtitleMenu && !showPlayerMenu && !showPlaybackTools && !showSettings &&
                !showDialogues && editingMeaning == null && foreground) {
                delay(controlsTimeoutMs)
                controlsVisible = false
            }
        }
        BackHandler(
            enabled = !pictureInPicture &&
                (controlsLocked || fullscreen || showWords || showLibrary || lookup != null)
        ) {
            when {
                showLibrary -> showLibrary = false
                showWords -> showWords = false
                controlsLocked -> { viewport = viewport.unlock(); controlsVisible = true; controlsInteraction++ }
                lookup != null -> lookup = null
                else -> { viewport = viewport.exitFullscreen(); controlsVisible = true; controlsInteraction++ }
            }
        }
        LaunchedEffect(videoUri) {
            val source = videoUri ?: return@LaunchedEffect
            sourceName = withContext(Dispatchers.IO) { readVideoName(source) } ?: "ویدیوی محلی"
        }
        LaunchedEffect(videoUri, videoReady, sourceName) {
            val uri = videoUri ?: return@LaunchedEffect
            val name = sourceName ?: return@LaunchedEffect
            if (videoReady && !playbackError && uri.scheme == "content") rememberRecentVideo(uri, name)
        }
        LaunchedEffect(showWords, showLibrary, videoUri) {
            pronunciation.stop()
            if (showWords || showLibrary) {
                viewport = PlaybackViewport()
                clearAbLoop()
                replayEndMs = null
                player.pause()
            } else {
                controlsVisible = true
            }
        }
        val originalOrientation by rememberSaveable { mutableIntStateOf(requestedOrientation) }
        val viewportOrientation = viewport.requestedOrientation(
            playerVisible = videoUri != null && !showLibrary && !showWords,
            pictureInPicture = pictureInPicture
        )
        LaunchedEffect(viewportOrientation) {
            val request = when (viewportOrientation) {
                PlaybackOrientation.Automatic -> originalOrientation
                PlaybackOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                PlaybackOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            if (requestedOrientation != request) requestedOrientation = request
        }
        DisposableEffect(Unit) {
            onDispose { requestedOrientation = originalOrientation }
        }
        DisposableEffect(fullscreen, pictureInPicture, showWords, showLibrary) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!pictureInPicture) {
                if (fullscreen && !showWords && !showLibrary) controller.hide(WindowInsetsCompat.Type.systemBars())
                else controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }

        val selectVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                keepReadAccess(uri)
                lookup = null
                showLibrary = false
                openVideo(uri)
            }
        }
        val selectFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                if (keepReadAccess(uri)) {
                    libraryFolder = uri
                    history.edit().putString("libraryFolder", uri.toString()).apply()
                } else {
                    Toast.makeText(this, "ذخیرهٔ دسترسی به این پوشه ممکن نبود.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val selectSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                keepReadAccess(uri)
                chooseEmbeddedSubtitle(null)
                subtitleUri = uri
            }
        }

        val selectTranslation = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                keepReadAccess(uri)
                translationDelayMs = 0
                translationUri = uri
            }
        }
        LaunchedEffect(foreground, videoUri) {
            if (!foreground) return@LaunchedEffect
            while (true) {
                val position = player.currentPosition
                val loop = abLoop
                if (loop != null && player.playWhenReady &&
                    (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED)
                ) {
                    if (!player.isCurrentMediaItemSeekable || player.isCurrentMediaItemLive) clearAbLoop()
                    else loop.seekTarget(position)?.let { player.seekTo(it) }
                }
                val replayEnd = replayEndMs
                if (replayEnd != null && (position >= replayEnd || player.playbackState == Player.STATE_ENDED)) {
                    replayEndMs = null
                    player.pause()
                }
                val previousDialoguePosition = lastDialoguePosition
                lastDialoguePosition = position
                if (pauseAfterDialogue && abLoop == null && replayEnd == null &&
                    player.playWhenReady && !player.isCurrentMediaItemLive && player.isCurrentMediaItemSeekable
                ) {
                    crossedDialogueEnd(autoPauseEnds, previousDialoguePosition, position)?.let { end ->
                        player.pause()
                        player.seekTo(end)
                    }
                }
                frame = PlaybackFrame(
                    source = videoUri,
                    positionMs = position,
                    durationMs = player.duration.coerceAtLeast(0),
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                    live = player.isCurrentMediaItemLive,
                    seekable = player.duration > 0 && player.isCurrentMediaItemSeekable,
                    playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
                )
                delay(40)
            }
        }
        LaunchedEffect(lookup) { pronunciation.stop() }
        LaunchedEffect(lookup, lookup?.studyAction, lookup?.expanded) {
            if (lookup != null && lookup?.studyAction == null && lookup?.expanded != true) {
                delay(3_000)
                lookup = null
            }
        }

        if (showWords && !pictureInPicture) {
            SavedWordsScreen(vocabulary, pronunciation, onBack = { showWords = false })
            return
        }
        if (showLibrary && !pictureInPicture) {
            VideoLibraryScreen(
                folder = libraryFolder,
                recent = recentVideos,
                queue = playbackQueue,
                onEnqueue = ::enqueueVideo,
                onMoveQueued = ::moveQueuedVideo,
                onRemoveQueued = { uri -> updatePlaybackQueue(playbackQueue.filterNot { it.uri == uri }) },
                onPlayQueue = ::playNextQueuedVideo,
                onChooseFolder = { selectFolder.launch(libraryFolder) },
                onChooseFile = { selectVideo.launch(arrayOf("video/*")) },
                onOpenLink = { link, credentials ->
                    lookup = null
                    showLibrary = false
                    openVideo(Uri.parse(link.url), onlineFormat = link.format, smbCredentials = credentials)
                },
                onOpen = { openLibraryVideo(it.uri) },
                onBack = { showLibrary = false }
            )
            return
        }

        val canSeek = frame.source == videoUri && frame.seekable && !playbackError
        val navigationDialogues = remember(cues, subtitleDelayMs, frame.durationMs) {
            cues.mapNotNull { it.toDialogueRange(subtitleDelayMs, frame.durationMs) }
                .distinctBy { it.startMs }
        }
        LaunchedEffect(navigationDialogues, pauseAfterDialogue, foreground) {
            autoPauseEnds = dialoguePauseBoundaries(navigationDialogues)
            lastDialoguePosition = player.currentPosition
        }
        val previousDialogue = adjacentDialogue(navigationDialogues, frame.positionMs, false)
        val nextDialogue = adjacentDialogue(navigationDialogues, frame.positionMs, true)
        val playerLayout = Modifier.fillMaxSize().background(background)
        Box(playerLayout) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(player, if (pictureInPicture) VideoScaleMode.Fit else videoScaleMode, Modifier.fillMaxSize())
                if (videoUri != null && !pictureInPicture) {
                    PlaybackGestureLayer(
                        window = window,
                        player = player,
                        enabled = !playbackError && !controlsLocked,
                        onToggleControls = {
                            controlsVisible = !controlsVisible
                            controlsInteraction++
                        },
                        skipSeconds = skipSeconds,
                        onSkip = if (canSeek && !controlsLocked) ({ delta ->
                            lookup = null
                            skipBy(delta)
                        }) else null,
                        modifier = Modifier.matchParentSize()
                    )
                }
                if (!pictureInPicture && videoUri != null && frame.source == videoUri &&
                    frame.buffering && showBuffering && !playbackError && !englishTrack.failed && !persianTrack.failed
                ) {
                    Column(
                        Modifier.align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp), color = accent)
                        Text("در حال بارگذاری…", color = Color.White)
                    }
                }
                if (!pictureInPicture && (videoUri == null || playbackError || englishTrack.failed || persianTrack.failed)) {
                    val failedOnline = playbackError && videoUri?.isOnlineVideo == true
                    val message = when {
                        failedOnline && videoUri?.scheme == "smb" ->
                            "پخش فایل شبکه ممکن نبود. مسیر فایل و دسترسی به اشتراک را بررسی کنید."
                        failedOnline -> "پخش لینک ممکن نبود. اتصال اینترنت، آدرس و نوع پخش را بررسی کنید."
                        playbackError -> "پخش این فایل ممکن نیست. ویدیو را دوباره انتخاب کنید."
                        englishTrack.failed -> "زیرنویس انگلیسی خوانده نشد. فایل متنی SRT یا WebVTT معتبر و حداکثر ۸ مگابایت انتخاب کنید."
                        persianTrack.failed -> "زیرنویس فارسی خوانده نشد. فایل متنی SRT یا WebVTT معتبر و حداکثر ۸ مگابایت انتخاب کنید."
                        else -> "نمونه را باز کنید یا ویدیو و زیرنویس خودتان را انتخاب کنید."
                    }
                    Column(
                        Modifier.align(Alignment.Center).padding(20.dp)
                            .background(background, RoundedCornerShape(12.dp)).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (videoUri == null) PinesIdentityBanner(compact = true)
                        Text(message, color = PinesPalette.Cream, textAlign = TextAlign.Center)
                        if (videoUri == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { openVideo(demoVideo) }) { Text("نمونه") }
                                TextButton(onClick = { showLibrary = true }) { Text("انتخاب فیلم") }
                                TextButton(onClick = { showHelp = true }) { Text("راهنما") }
                            }
                        }
                        if (failedOnline && !controlsLocked) {
                            TextButton(onClick = {
                                playbackError = false
                                videoReady = false
                                if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
                                player.prepare()
                                player.play()
                            }) { Text("تلاش دوباره") }
                        }
                    }
                }
                if (!pictureInPicture) Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(bottom = if (controlsVisible && !controlsLocked && videoUri != null) 104.dp else 12.dp)
                        .safeDrawingPadding().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lookup?.takeUnless { controlsLocked }?.let { selected ->
                        MeaningCard(
                            lookup = selected,
                            pronunciation = pronunciation,
                            onDismiss = { lookup = null },
                            canEditMeaning = personalEntries != null && selected.saveState != WordSaveState.Saving &&
                                normalizeDictionaryTerm(selected.word) != null,
                            onEditMeaning = {
                                pronunciation.stop()
                                val resume = selected.studyAction == StudyAction.Resume ||
                                    (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
                                player.pause()
                                replayEndMs = null
                                selected.studyAction = if (resume) StudyAction.Resume else StudyAction.Close
                                editingMeaning = selected
                            },
                            beforeSpeak = {
                                val resume = selected.studyAction == StudyAction.Resume ||
                                    (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
                                player.pause()
                                replayEndMs = null
                                selected.studyAction = if (resume) StudyAction.Resume else StudyAction.Close
                            },
                            onSave = {
                                if (selected.saveState == WordSaveState.Ready) {
                                    selected.saveState = WordSaveState.Saving
                                    scope.launch { saveLookup(selected) }
                                }
                            },
                            onStudyAction = {
                                lookup = null
                                if (selected.studyAction == StudyAction.Resume) player.play()
                            }
                        )
                    }
                    val source = frame.source
                    val subtitle = if (embeddedTrackKey != null) embeddedSubtitle
                    else englishTrack.timeline.activeAt(frame.positionMs - subtitleDelayMs)
                    if (englishSubtitlesVisible && source != null && source == videoUri && subtitle != null) {
                        TappableSubtitle(subtitle.text, subtitleSize, Color(subtitleTextTone.argb), subtitleBackgroundOpacity,
                            enabled = !controlsLocked, personalMeanings = personalMeanings) { term ->
                            if (controlsLocked) return@TappableSubtitle
                            controlsVisible = false
                            val studyAction = if (pauseOnWordTap) {
                                val resume = lookup?.studyAction == StudyAction.Resume ||
                                    (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
                                player.pause()
                                if (resume) StudyAction.Resume else StudyAction.Close
                            } else null
                            lookup = Lookup(
                                word = term.text,
                                meaning = term.meaning,
                                dialogue = subtitle.text,
                                positionMs = frame.positionMs,
                                subtitleStartMs = subtitle.startMs,
                                source = source,
                                sourceName = sourceName ?: "ویدیوی محلی",
                                studyAction = studyAction
                            )
                        }
                    }
                    if (persianSubtitlesVisible && source != null && source == videoUri) {
                        persianTrack.timeline.activeAt(frame.positionMs - translationDelayMs)?.let {
                            PersianSubtitle(it.text, subtitleSize, subtitleBackgroundOpacity)
                        }
                    }
                }
                if (!pictureInPicture && videoUri != null && controlsVisible && !controlsLocked) {
                    val progress = if (frame.durationMs > 0)
                        (frame.positionMs.toFloat() / frame.durationMs).coerceIn(0f, 1f) else 0f
                    val shownPosition = if (dragPosition >= 0) (dragPosition * frame.durationMs).toLong() else frame.positionMs
                    PlayerOverlay(
                        title = sourceName ?: "ویدیو",
                        playing = frame.playing,
                        canPlay = !playbackError,
                        canSeek = canSeek,
                        progress = if (dragPosition >= 0) dragPosition else progress,
                        positionLabel = if (frame.live) "LIVE" else formatTime(shownPosition) + " / " +
                            (if (frame.durationMs > 0) formatTime(frame.durationMs) else "—"),
                        fullscreen = fullscreen,
                        skipSeconds = skipSeconds,
                        onBack = { lookup = null; showLibrary = true },
                        onPlayPause = {
                            controlsInteraction++
                            if (lookup?.studyAction != null) lookup = null
                            replayEndMs = null
                            when {
                                player.playbackState == Player.STATE_ENDED -> { player.seekTo(0); player.play() }
                                player.playWhenReady -> player.pause()
                                else -> player.play()
                            }
                        },
                        onSeekBack = { controlsInteraction++; lookup = null; skipBy(-skipSeconds * 1_000L) },
                        onSeekForward = { controlsInteraction++; lookup = null; skipBy(skipSeconds * 1_000L) },
                        onProgress = { dragPosition = it },
                        onSeekFinished = {
                            if (dragPosition >= 0 && canSeek) {
                                lookup = null
                                seekWithinVideo((dragPosition * frame.durationMs).toLong())
                            }
                            dragPosition = -1f
                            controlsInteraction++
                        },
                        onFullscreen = { viewport = viewport.toggleFullscreen(landscape); controlsInteraction++ },
                        onLock = {
                            viewport = viewport.lock(landscape)
                            pronunciation.stop()
                            lookup = null
                            dragPosition = -1f
                            showSettings = false
                            showDialogues = false
                            showSubtitleMenu = false
                            showPlayerMenu = false
                        },
                        topActions = {
                            Box {
                                PlayerIconButton(PlayerGlyph.Subtitles, "زیرنویس", onClick = { showSubtitleMenu = true })
                                DropdownMenu(expanded = showSubtitleMenu, onDismissRequest = { showSubtitleMenu = false }) {
                                    DropdownMenuItem(text = { Text("انتخاب زیرنویس انگلیسی") }, onClick = {
                                        showSubtitleMenu = false
                                        selectSubtitle.launch(arrayOf("*/*"))
                                    })
                                    embeddedOptions.forEachIndexed { index, option ->
                                        DropdownMenuItem(
                                            text = {
                                                val selected = if (option.key == embeddedTrackKey) "✓ " else ""
                                                Text(selected + option.displayName(index + 1))
                                            },
                                            onClick = {
                                                showSubtitleMenu = false
                                                if (option.key != embeddedTrackKey) chooseEmbeddedSubtitle(option.key)
                                            }
                                        )
                                    }
                                    if (embeddedTrackKey != null || subtitleUri != null) {
                                        DropdownMenuItem(text = { Text("خاموش کردن زیرنویس اصلی") }, onClick = {
                                            showSubtitleMenu = false
                                            chooseEmbeddedSubtitle(null)
                                        })
                                    }
                                    DropdownMenuItem(text = { Text("انتخاب زیرنویس فارسی") }, onClick = {
                                        showSubtitleMenu = false
                                        selectTranslation.launch(arrayOf("*/*"))
                                    })
                                    if (translationUri != null) {
                                        DropdownMenuItem(text = { Text("برداشتن زیرنویس فارسی") }, onClick = {
                                            showSubtitleMenu = false
                                            translationUri = null
                                            translationDelayMs = 0
                                        })
                                    }
                                }
                            }

                            PlayerIconButton(PlayerGlyph.Settings, "تنظیمات", onClick = { showSettings = true })
                            Box {
                                PlayerIconButton(PlayerGlyph.More, "گزینه‌های پخش", onClick = { showPlayerMenu = true })
                                DropdownMenu(expanded = showPlayerMenu, onDismissRequest = { showPlayerMenu = false }) {
                                    DropdownMenuItem(text = { Text("کتابخانه") }, onClick = { showPlayerMenu = false; lookup = null; showLibrary = true })
                                    DropdownMenuItem(text = { Text("دفترچهٔ واژه‌ها") }, onClick = { showPlayerMenu = false; lookup = null; showWords = true })
                                    DropdownMenuItem(text = { Text("ابزارهای مطالعه و تکرار") }, onClick = { showPlayerMenu = false; showPlaybackTools = true })
                                    if (supportsPictureInPicture && videoReady && !playbackError) {
                                        DropdownMenuItem(text = { Text("تصویر در تصویر") }, onClick = { showPlayerMenu = false; lookup = null; enterVideoPictureInPicture() })
                                    }
                                    DropdownMenuItem(text = { Text("راهنما") }, onClick = { showPlayerMenu = false; showHelp = true })
                                    DropdownMenuItem(text = { Text("پخش نمونه") }, onClick = { showPlayerMenu = false; lookup = null; openVideo(demoVideo) })
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!pictureInPicture && controlsLocked) {
                    TextButton(onClick = { viewport = viewport.unlock(); controlsVisible = true; controlsInteraction++ },
                        modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))) {
                        Text("باز کردن قفل", color = Color.White)
                    }
                }
            }

        }
        if (showPlaybackTools && !pictureInPicture && !controlsLocked) {
            ModalBottomSheet(onDismissRequest = { showPlaybackTools = false }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("ابزارهای مطالعه", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = canSeek && !player.isCurrentMediaItemLive, onClick = {
                        lookup = null
                        replayEndMs = null
                        abLoop = null
                        loopStartMs = player.currentPosition.coerceAtLeast(0)
                    }) { Text(loopStartMs?.let { "A: " + formatTime(it) } ?: "ثبت A") }
                    TextButton(enabled = canSeek && loopStartMs != null && !player.isCurrentMediaItemLive, onClick = {
                        val loop = playbackLoop(loopStartMs ?: 0, player.currentPosition, player.duration)
                        if (loop == null) {
                            Toast.makeText(this@MainActivity, "B باید دست‌کم نیم‌ثانیه بعد از A باشد.", Toast.LENGTH_SHORT).show()
                        } else {
                            lookup = null
                            replayEndMs = null
                            abLoop = loop
                            player.seekTo(loop.startMs)
                            player.play()
                        }
                    }) { Text(abLoop?.let { "B: " + formatTime(it.endMs) } ?: "ثبت B") }
                    TextButton(enabled = loopStartMs != null || abLoop != null, onClick = { clearAbLoop() }) {
                        Text("لغو تکرار")
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(enabled = canSeek && previousDialogue != null, onClick = {
                        lookup = null
                        adjacentDialogue(navigationDialogues, player.currentPosition, false)?.let {
                            seekWithinVideo(it.startMs)
                        }
                    }) { Text("شروع / قبلی") }
                    TextButton(enabled = canSeek && nextDialogue != null, onClick = {
                        lookup = null
                        adjacentDialogue(navigationDialogues, player.currentPosition, true)?.let {
                            seekWithinVideo(it.startMs)
                        }
                    }) { Text("دیالوگ بعدی") }
                    TextButton(onClick = { showSettings = true }) { Text("تنظیمات") }
                    TextButton(
                        enabled = canSeek && cues.isNotEmpty(),
                        onClick = {
                            lookup = null
                            showDialogues = true
                        }
                    ) { Text("دیالوگ‌ها") }

                }
                }
            }
        }

        editingMeaning?.takeUnless { pictureInPicture || controlsLocked }?.let { selected ->
            PersonalMeaningDialog(
                dao = personalDictionary,
                term = selected.word,
                initialMeaning = selected.meaning,
                hasPersonalMeaning = normalizeDictionaryTerm(selected.word) in personalMeanings,
                onSaved = {
                    selected.meaning = it
                    selected.saveState = WordSaveState.Ready
                },
                onDismiss = { editingMeaning = null }
            )
        }
        if (!pictureInPicture && !controlsLocked && showDialogues && canSeek) {
            val dialogues = remember(cues, subtitleDelayMs, frame.durationMs) {
                cues.mapNotNull { it.toDialogueRange(subtitleDelayMs, frame.durationMs) }
            }
            DialogueList(
                dialogues = dialogues,
                positionMs = frame.positionMs,
                onSeek = { dialogue ->
                    showDialogues = false
                    seekWithinVideo(dialogue.startMs)
                },
                onReplay = { dialogue ->
                    showDialogues = false
                    seekWithinVideo(dialogue.startMs, dialogue.endMs)
                },
                onDismiss = { showDialogues = false }
            )
        }
        if (!pictureInPicture && !controlsLocked && showHelp) {
            PlaybackHelpDialog(skipSeconds, onDismiss = { showHelp = false })
        }
        if (!pictureInPicture && !controlsLocked && showSettings && videoUri != null) {
            PlaybackSettings(
                englishEncoding = englishEncoding,
                onEnglishEncoding = {
                    englishEncoding = it
                    lookup = null
                    pronunciation.stop()
                    replayEndMs = null
                    history.edit().putString("englishEncoding", it.name).apply()
                },
                persianEncoding = persianEncoding,
                onPersianEncoding = {
                    persianEncoding = it
                    history.edit().putString("persianEncoding", it.name).apply()
                },
                englishSubtitlesVisible = englishSubtitlesVisible,
                onEnglishSubtitlesVisible = {
                    englishSubtitlesVisible = it
                    lookup = null
                    pronunciation.stop()
                    history.edit().putBoolean("englishSubtitlesVisible", it).apply()
                },
                persianSubtitlesVisible = persianSubtitlesVisible,
                onPersianSubtitlesVisible = {
                    persianSubtitlesVisible = it
                    history.edit().putBoolean("persianSubtitlesVisible", it).apply()
                },
                videoScaleMode = videoScaleMode,
                onVideoScaleMode = {
                    videoScaleMode = it
                    history.edit().putString("videoScaleMode", it.name).apply()
                },
                sleepRemainingMs = sleepRemainingMs,
                onSleepTimer = { minutes ->
                    sleepDeadlineMs = minutes?.let { sleepDeadline(SystemClock.elapsedRealtime(), it) }
                    sleepRemainingMs = minutes?.times(60_000L)
                },
                skipSeconds = skipSeconds,
                onSkipSeconds = {
                    if (it in seekStepChoices) {
                        skipSeconds = it
                        history.edit().putInt("skipSeconds", it).apply()
                    }
                },
                speed = playbackSpeed,
                onSpeed = { speed ->
                    playbackSpeed = speed
                    player.setPlaybackSpeed(speed)
                },
                tracks = tracks,
                onAudioTrack = { selection ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setOverrideForType(selection)
                        .build()
                },
                pauseAfterDialogue = pauseAfterDialogue,
                onPauseAfterDialogue = {
                    pauseAfterDialogue = it
                    lastDialoguePosition = player.currentPosition
                    history.edit().putBoolean("pauseAfterDialogue", it).apply()
                },
                pauseOnWordTap = pauseOnWordTap,
                onPauseOnWordTap = {
                    pauseOnWordTap = it
                    lookup = null
                },
                subtitleTextTone = subtitleTextTone,
                subtitleBackgroundOpacity = subtitleBackgroundOpacity,
                onSubtitleTextTone = {
                    subtitleTextTone = it
                    history.edit().putString("subtitleTextTone", it.name).apply()
                },
                onSubtitleBackgroundOpacity = {
                    subtitleBackgroundOpacity = it.coerceIn(0f, 1f)
                    history.edit().putFloat("subtitleBackgroundOpacity", subtitleBackgroundOpacity).apply()
                },
                onSubtitleStyleReset = {
                    subtitleTextTone = SubtitleTextTone.White
                    subtitleBackgroundOpacity = 0.82f
                    history.edit().putString("subtitleTextTone", subtitleTextTone.name)
                        .putFloat("subtitleBackgroundOpacity", subtitleBackgroundOpacity).apply()
                },
                subtitleSize = subtitleSize,
                onSubtitleSize = { subtitleSize = it },
                subtitleDelayMs = subtitleDelayMs.takeIf { subtitleUri != null },
                onSubtitleDelay = {
                    replayEndMs = null
                    subtitleDelayMs = it
                },
                translationDelayMs = translationDelayMs.takeIf { translationUri != null },
                onTranslationDelay = { translationDelayMs = it },
                onDismiss = { showSettings = false }
            )
        }
    }

    private fun skipBy(deltaMs: Long) = seekWithinVideo(player.currentPosition + deltaMs)

    private fun seekWithinVideo(positionMs: Long, stopAtMs: Long? = null) {
        val duration = player.duration
        if (duration <= 0 || !player.isCurrentMediaItemSeekable) return
        if (abLoop != null || stopAtMs != null) clearAbLoop()
        val start = positionMs.coerceIn(0L, duration)
        replayEndMs = stopAtMs?.coerceAtMost(duration)?.takeIf { it > start }
        if (start == duration) player.pause()
        player.seekTo(start)
        if (replayEndMs != null) player.play()
    }
}

private data class PlaybackFrame(
    val source: Uri? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffering: Boolean = false,
    val live: Boolean = false,
    val seekable: Boolean = false,
    val playing: Boolean = false
)

private data class SubtitleTrack(
    val cues: List<SubtitleCue> = emptyList(),
    val failed: Boolean = false
) {
    val timeline = SubtitleTimeline(cues)
}

private enum class WordSaveState { Ready, Saving, Saved }
private enum class StudyAction { Resume, Close }

private class Lookup(
    val word: String,
    meaning: WordMeaning?,
    val dialogue: String,
    val positionMs: Long,
    val subtitleStartMs: Long,
    val source: Uri,
    val sourceName: String,
    studyAction: StudyAction?
) {
    var expanded by mutableStateOf(false)
    var meaning by mutableStateOf(meaning)
    var studyAction by mutableStateOf(studyAction)
    var saveState by mutableStateOf(WordSaveState.Ready)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoSurface(player: Player, scaleMode: VideoScaleMode, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                subtitleView?.visibility = View.GONE
            }
        },
        modifier = modifier,
        onReset = null,
        onRelease = { it.player = null },
        update = {
            it.player = player
            it.resizeMode = when (scaleMode) {
                VideoScaleMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                VideoScaleMode.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                VideoScaleMode.Stretch -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        }
    )
}

@Composable
private fun TappableSubtitle(
    text: String,
    fontSize: Int,
    textColor: Color,
    backgroundOpacity: Float,
    enabled: Boolean,
    personalMeanings: Map<String, WordMeaning>,
    onTerm: (SubtitleTerm) -> Unit
) {
    val currentOnTerm by rememberUpdatedState(onTerm)
    val annotated = remember(text, enabled, personalMeanings) {
        buildAnnotatedString {
            append(text)
            if (!enabled) return@buildAnnotatedString
            subtitleTerms(text, personalMeanings).forEach { term ->
                addLink(
                    LinkAnnotation.Clickable(
                        tag = term.start.toString(),
                        styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.None)),
                        linkInteractionListener = { currentOnTerm(term) }
                    ),
                    start = term.start,
                    end = term.endExclusive
                )
            }
        }
    }
    Text(
        annotated,
        Modifier.background(Color.Black.copy(alpha = backgroundOpacity), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = textColor,
        style = MaterialTheme.typography.titleLarge.copy(
            textDirection = TextDirection.Ltr,
            textAlign = TextAlign.Center,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 6).sp
        )
    )
}

@Composable
private fun PersianSubtitle(text: String, fontSize: Int, backgroundOpacity: Float) {
    Text(
        text,
        Modifier.background(Color.Black.copy(alpha = backgroundOpacity), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color(0xFFE1F3EB),
        style = MaterialTheme.typography.titleMedium.copy(
            textDirection = TextDirection.Rtl,
            textAlign = TextAlign.Center,
            fontSize = (fontSize - 2).sp,
            lineHeight = (fontSize + 4).sp
        )
    )
}

@Composable
private fun MeaningCard(
    lookup: Lookup,
    pronunciation: EnglishPronunciation,
    onDismiss: () -> Unit,
    canEditMeaning: Boolean,
    onEditMeaning: () -> Unit,
    beforeSpeak: () -> Unit,
    onSave: () -> Unit,
    onStudyAction: () -> Unit
) {
    val meaning = lookup.meaning
    val label = if (meaning == null || lookup.word.equals(meaning.headword, ignoreCase = true))
        lookup.word else "${lookup.word} → ${meaning.headword}"
    Column(
        Modifier.widthIn(max = 520.dp).heightIn(max = 190.dp)
            .background(PinesPalette.Card.copy(alpha = 0.97f), RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, color = PinesPalette.Rose, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meaning?.persian ?: "معنی موجود نیست؛ می‌توانید معنی اضافه کنید",
                    color = PinesPalette.Cream, maxLines = if (lookup.expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
            }
            if (meaning != null) {
                TextButton(enabled = lookup.saveState == WordSaveState.Ready, onClick = onSave) {
                    Text(when (lookup.saveState) {
                        WordSaveState.Ready -> "ذخیره"
                        WordSaveState.Saving -> "…"
                        WordSaveState.Saved -> "ذخیره شد"
                    }, color = PinesPalette.Cream)
                }
            }
            PlayerIconButton(PlayerGlyph.More, "گزینه‌های واژه", onClick = { lookup.expanded = !lookup.expanded })
        }
        if (lookup.expanded) {
            TextButton(enabled = canEditMeaning, onClick = onEditMeaning) {
                Text(if (meaning == null) "افزودن معنی" else "ویرایش معنی", color = PinesPalette.Cream)
            }
            PronunciationControls(pronunciation, lookup.word, contentColor = PinesPalette.Rose, beforeSpeak = beforeSpeak)
            TextButton(onClick = onDismiss) { Text("بستن", color = PinesPalette.Cream) }
        }
        lookup.studyAction?.let { action ->
            TextButton(onClick = onStudyAction) {
                Text(if (action == StudyAction.Resume) "ادامهٔ پخش" else "بستن", color = PinesPalette.Cream)
            }
        }
    }
}
