package com.subtitleplayer.prototype

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val demoVideo = Uri.parse("asset:///demo.mp4")
private val demoSubtitles = Uri.parse("asset:///demo.srt")
private val demoTranslation = Uri.parse("asset:///demo.fa.vtt")
private val background = PinesPalette.Night
private val accent = PinesPalette.Accent
private val Uri.isOnlineVideo: Boolean get() = scheme == "https" || scheme == "rtsp" || scheme == "smb"
private fun Uri.savedWordSourceId(): String = if (isOnlineVideo) remoteVideoId(toString()) else toString()

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
    private var videoParentDirectory by mutableStateOf<Uri?>(null)
    private var videoParentPath by mutableStateOf<String?>(null)
    private var sidecarMatches by mutableStateOf(SidecarMatches())
    private var sidecarStatus by mutableStateOf(SidecarScanStatus.Idle)
    private val sidecarScope = MainScope()
    private var sidecarJob: Job? = null
    private var sidecarCancellation: CancellationSignal? = null
    private var sidecarRequestId = 0L
    private var videoOpenGeneration = 0L
    private var subtitlePreferenceRevision = 0L
    private var automaticSidecars = false
    private var subtitleUri by mutableStateOf<Uri?>(null)
    private var translationUri by mutableStateOf<Uri?>(null)
    private var playbackError by mutableStateOf(false)
    private var videoReady by mutableStateOf(false)
    private var showLibrary by mutableStateOf(false)
    private var showWords by mutableStateOf(false)
    private var showStreams by mutableStateOf(false)
    private var returnToStreams = false
    private val streamBookmarks by lazy {
        StreamPlaybackBookmarks(saved = history.getString("streamBookmarks", null)) { entries ->
            history.edit().putString("streamBookmarks", entries).apply()
        }
    }
    private var streamTitle by mutableStateOf<String?>(null)
    private var libraryTab by mutableStateOf(LibraryTab.Folder)
    private var libraryFolder by mutableStateOf<Uri?>(null)
    private var favoriteFolders by mutableStateOf<List<LibraryFolderShortcut>>(emptyList())
    private var libraryPath by mutableStateOf<List<LibrarySubfolder>>(emptyList())
    private var libraryFolderRevision by mutableIntStateOf(0)
    private var includeLibrarySubfolders by mutableStateOf(false)
    private var folderBrowseState by mutableStateOf(LibraryBrowseState())
    private val parentFolderPositions = mutableMapOf<Int, LibraryBrowsePosition>()
    private var recentVideos by mutableStateOf(emptyList<LibraryVideo>())
    private var legacyQueuedVideos: List<LibraryVideo> = emptyList()
    private var foreground by mutableStateOf(false)
    private var pictureInPicture by mutableStateOf(false)
    private val supportsPictureInPicture by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    private var tracks by mutableStateOf(Tracks.EMPTY)
    private var embeddedOptions by mutableStateOf(emptyList<EmbeddedSubtitleTrack>())
    private var embeddedSelection by mutableStateOf(EmbeddedSubtitleSelection())
    private val embeddedTrackKey: String? get() = embeddedSelection.key
    private var embeddedSubtitle by mutableStateOf<ActiveSubtitle?>(null)
    private var playbackSpeed by mutableFloatStateOf(1f)
    private var skipSeconds by mutableIntStateOf(10)
    private var videoScaleMode by mutableStateOf(VideoScaleMode.Fit)
    private var englishSubtitlesVisible by mutableStateOf(true)
    private var persianSubtitlesVisible by mutableStateOf(true)
    private var englishEncoding by mutableStateOf(SubtitleTextEncoding.Auto)
    private var persianEncoding by mutableStateOf(SubtitleTextEncoding.Auto)
    private var subtitleLookupMode by mutableStateOf(SubtitleLookupMode.Words)
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
        Toast.makeText(this, "Sleep timer ended. Playback paused.", Toast.LENGTH_LONG).show()
        return true
    }

    private var replayEndMs: Long? = null
    private var loopStartMs by mutableStateOf<Long?>(null)
    private var abLoop by mutableStateOf<PlaybackLoop?>(null)

    private fun clearAbLoop() { abLoop = null; loopStartMs = null }

    private fun startAbLoop(loop: PlaybackLoop) {
        replayEndMs = null
        loopStartMs = loop.startMs
        abLoop = loop
        player.seekTo(loop.startMs)
        player.play()
    }

    private val history by lazy { getSharedPreferences("last_playback", MODE_PRIVATE) }
    private val vocabulary by lazy { SavedWordsDatabase.get(applicationContext).words() }
    private val packagedDictionary by lazy { PackagedDictionary(applicationContext) }
    private val wordLookup by lazy {
        WordLookupService({ packagedDictionary.resolve(it, emptyMap()) }, ::translateSelectedTerm)
    }
    private val personalDictionary by lazy { SavedWordsDatabase.get(applicationContext).dictionary() }

    override fun attachBaseContext(newBase: Context) {
        val english = Configuration().apply { setLocale(Locale.ENGLISH) }
        super.attachBaseContext(newBase.createConfigurationContext(english))
    }

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
        subtitleLookupMode = subtitleLookupModeFrom(history.getString("subtitleLookupMode", null))
        pauseOnWordTap = history.getBoolean("pauseOnWordTap", false)
        pauseAfterDialogue = history.getBoolean("pauseAfterDialogue", false)
        subtitleSize = defaultSubtitleSize
        subtitleTextTone = SubtitleTextTone.entries.firstOrNull { it.name == history.getString("subtitleTextTone", null) }
            ?: SubtitleTextTone.White
        subtitleBackgroundOpacity = history.getFloat("subtitleBackgroundOpacity", 0.82f)
            .takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.82f
        subtitleProfiles = loadSubtitleProfiles()
        libraryFolder = parseLibraryFolderUri(history.getString("libraryFolder", null))
        favoriteFolders = history.getString("favoriteFolderShortcuts", null)?.let(::readFavoriteFolderShortcuts)
            ?: history.getStringSet("favoriteFolders", emptySet()).orEmpty()
                .mapNotNull(::parseLibraryFolderUri).distinct().take(favoriteFolderLimit)
                .map { LibraryFolderShortcut(it) }
        val readable = persistentReadUris()
        libraryTab = LibraryTab.entries.firstOrNull { it.name == savedInstanceState?.getString("libraryTab") }
            ?: LibraryTab.Folder
        val savedFolder = savedInstanceState?.getString("libraryLocation")
            ?.let(::readFavoriteFolderShortcuts)?.singleOrNull()
        if (savedInstanceState != null && savedFolder != null &&
            savedFolder.tree == libraryFolder && readable.coversReadAccess(savedFolder.tree)) {
            libraryPath = savedFolder.path
            includeLibrarySubfolders = savedInstanceState.getBoolean("libraryIncludeSubfolders")
            folderBrowseState = LibraryBrowseState(
                LibraryBrowsePosition(
                    savedInstanceState.getString("libraryQuery").orEmpty(),
                    savedInstanceState.getInt("libraryScrollIndex").coerceAtLeast(0),
                    savedInstanceState.getInt("libraryScrollOffset").coerceAtLeast(0)
                )
            )
        }
        recentVideos = readVideoEntries(history.getString("recentVideos", null), recentVideoLimit)
            .filter { readable.coversReadAccess(it.uri) }
        legacyQueuedVideos = readVideoEntries(history.getString("playbackQueue", null), legacyQueueEntryLimit)
            .filter { readable.coversReadAccess(it.uri) }
        showLibrary = savedInstanceState?.getBoolean("showLibrary") == true
        showWords = savedInstanceState?.getBoolean("showWords") == true
        showStreams = savedInstanceState?.getBoolean("showStreams") == true

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
                    if (videoUri == null || player.currentMediaItem?.localConfiguration?.uri != videoUri ||
                        newTracks != player.currentTracks) return
                    tracks = newTracks
                    embeddedOptions = embeddedTextTracks(newTracks)
                    refreshEmbeddedTextSelection()
                }

                override fun onCues(cueGroup: CueGroup) {
                    if (player.currentMediaItem?.localConfiguration?.uri != videoUri || cueGroup != player.currentCues) return
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
                    if (playbackState == Player.STATE_ENDED) rememberCurrentProgress()
                    expireSleepTimer()
                    if (playbackState == Player.STATE_ENDED && pauseAfterDialogue &&
                        abLoop == null && replayEndMs == null && autoPauseEnds.lastOrNull() == player.duration
                    ) player.pause()
                    if (playbackState == Player.STATE_READY) videoReady = true
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
                        automaticEnglish = savedInstanceState.getBoolean("automaticEnglish", false),
                        automaticSidecars = savedInstanceState.getBoolean("automaticSidecars", false),
                        size = savedInstanceState.getInt("subtitleSize", defaultSubtitleSize)
                    ),
                    parentDirectory = savedInstanceState.getString("videoParentDirectory")?.let(Uri::parse),
                    parentPath = savedInstanceState.getString("videoParentPath")
                )
            }
            else -> showLibrary = !handleOpenIntent(intent)
        }

        if (videoUri == null) showLibrary = true

        setContent {
            VelvetPinesTheme {
                PlayerScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleOpenIntent(intent)) {
            showWords = false
            showStreams = false
            showLibrary = false
        } else if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            showWords = false
            showStreams = false
            libraryTab = LibraryTab.Folder
            showLibrary = true
        }
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
            Toast.makeText(this, "Could not open picture-in-picture.", Toast.LENGTH_SHORT).show()
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
        outState.putBoolean("showWords", showWords)
        outState.putBoolean("showStreams", showStreams)
        outState.putString("libraryTab", libraryTab.name)
        libraryFolder?.let { folder ->
            outState.putString("libraryLocation", writeFavoriteFolderShortcuts(listOf(LibraryFolderShortcut(folder, libraryPath))))
            outState.putBoolean("libraryIncludeSubfolders", includeLibrarySubfolders)
            val position = folderBrowseState.position()
            outState.putString("libraryQuery", position.query.take(512))
            outState.putInt("libraryScrollIndex", position.index)
            outState.putInt("libraryScrollOffset", position.offset)
        }
        if (videoUri?.isOnlineVideo == true) return
        outState.putString("video", videoUri?.toString())
        outState.putString("videoParentDirectory", videoParentDirectory?.toString())
        outState.putString("videoParentPath", videoParentPath)
        outState.putString("subtitle", subtitleUri?.toString())
        outState.putLong("position", resumePosition())
        outState.putLong("subtitleDelayMs", subtitleDelayMs)
        outState.putString("translation", translationUri?.toString())
        outState.putLong("translationDelayMs", translationDelayMs)
        outState.putString("embeddedSubtitleTrack", embeddedTrackKey)
        outState.putBoolean("automaticEnglish", embeddedSelection.automaticEnglish)
        outState.putBoolean("automaticSidecars", automaticSidecars)
        outState.putInt("subtitleSize", subtitleSize)
    }

    override fun onDestroy() {
        rememberCurrentProgress()
        cancelSidecarDiscovery()
        sidecarScope.cancel()
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

    private fun restoreSavedPlayback(parentDirectory: Uri? = null, parentPath: String? = null) {
        val video = history.getString("video", null)?.let(Uri::parse) ?: return
        val bookmark = knownLibraryVideo(video)?.bookmark
        openVideo(video, bookmark?.resumePositionMs ?: history.getLong("position", 0), autoplay = false,
            parentDirectory = parentDirectory, parentPath = parentPath)
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
            automaticEnglish = embeddedSelection.automaticEnglish,
            automaticSidecars = automaticSidecars,
            size = subtitleSize
        ).withReadableFiles(readable)
        val key = videoSubtitleKey(video)
        subtitleProfiles = (listOf(SavedVideoSubtitleSettings(video, settings)) +
            subtitleProfiles.filterNot { videoSubtitleKey(it.video) == key }).take(videoSubtitleSettingsLimit)
        history.edit().putString("subtitleProfiles", writeVideoSubtitleSettings(subtitleProfiles)).apply()
    }

    private fun resumePosition(): Long =
        if (player.playbackState == Player.STATE_ENDED || !player.isCurrentMediaItemSeekable) 0 else player.currentPosition

    private fun savePlayback() {
        rememberCurrentProgress()
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
            videoUri, subtitleUri, translationUri, libraryFolder, videoParentDirectory,
            history.getString("video", null)?.let(Uri::parse)
        ) + recentVideos.map(LibraryVideo::uri) + legacyQueuedVideos.map(LibraryVideo::uri) +
            recentVideos.mapNotNull(LibraryVideo::parentDirectory) + legacyQueuedVideos.mapNotNull(LibraryVideo::parentDirectory) +
            subtitleProfiles.flatMap { listOfNotNull(it.video, it.subtitles.english, it.subtitles.persian) }
        val retainedGrants = (retained + favoriteFolders.map { it.tree }).flatMap { listOf(it, libraryGrantUri(it)) }.toSet()
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
        positionMs: Long? = null,
        autoplay: Boolean = true,
        restoredSubtitles: VideoSubtitleSettings? = null,
        onlineFormat: OnlineVideoFormat? = null,
        smbCredentials: SmbCredentials? = null,
        parentDirectory: Uri? = null,
        parentPath: String? = null,
        fromStreams: Boolean = false,
        title: String? = null
    ) {
        val incoming = knownLibraryVideo(uri)
        val incomingStream = if (fromStreams) streamBookmarks[uri.toString()] else null
        val readable = persistentReadUris()
        rememberCurrentProgress()
        rememberSubtitleProfile(readable)
        returnToStreams = fromStreams
        streamTitle = title
        val key = videoSubtitleKey(uri)
        val savedProfile = subtitleProfiles.firstOrNull { videoSubtitleKey(it.video) == key }?.subtitles?.withReadableFiles(readable)
        val settings = restoredSubtitles
            ?: savedProfile
            ?: if (uri == demoVideo) {
                VideoSubtitleSettings(english = demoSubtitles, persian = demoTranslation, size = defaultSubtitleSize)
            } else VideoSubtitleSettings(size = defaultSubtitleSize, automaticEnglish = true, automaticSidecars = true)

        val knownVideo = knownLibraryVideo(uri) ?: incoming
        val startingPosition = if (fromStreams && positionMs == null) {
            resumePositionForOpen(null, streamBookmarks[uri.toString()], incomingStream)
                .takeIf { it > 0 } ?: C.TIME_UNSET
        } else {
            resumePositionForOpen(positionMs, knownVideo?.bookmark, incoming?.bookmark)
        }
        val knownParent = parentDirectory ?: knownParentFor(uri) ?: knownVideo?.parentDirectory
        cancelSidecarDiscovery()
        videoOpenGeneration++
        videoParentDirectory = validSidecarParent(uri, knownParent)
        videoParentPath = if (parentDirectory != null) parentPath else knownVideo?.parentPath
        automaticSidecars = settings.automaticSidecars && settings.automaticEnglish && settings.english == null
        sidecarMatches = SidecarMatches()
        sidecarStatus = SidecarScanStatus.Idle
        clearAbLoop()
        autoPauseEnds = emptyList()
        lastDialoguePosition = null
        replayEndMs = null
        embeddedSelection = EmbeddedSubtitleSelection(automaticEnglish = false)
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
            player.setMediaItem(item, startingPosition)
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
            player.setMediaSource(source, startingPosition)
        }
        embeddedSelection = EmbeddedSubtitleSelection(
            automaticEnglish = settings.automaticEnglish && settings.english == null,
            key = settings.embeddedTrack.takeIf { settings.english == null }
        )
        player.prepare()
        player.playWhenReady = autoplay
        startSidecarDiscovery(allowAutomatic = automaticSidecars)
    }

    private fun openDemoVideo() {
        openVideo(demoVideo, restoredSubtitles = VideoSubtitleSettings(
            english = demoSubtitles, persian = demoTranslation, size = defaultSubtitleSize
        ))
    }
    private fun refreshEmbeddedTextSelection() {
        val updated = embeddedSelection.refresh(embeddedOptions.map { it.candidate }, subtitleUri != null)
        if (updated.key != embeddedTrackKey || embeddedOptions.none { it.key == updated.key }) embeddedSubtitle = null
        embeddedSelection = updated
        updateEmbeddedTextSelection()
    }

    private fun chooseAutomaticEnglishSubtitle() {
        automaticSidecars = false
        subtitlePreferenceRevision++
        replayEndMs = null
        subtitleUri = null
        subtitleDelayMs = 0
        embeddedSelection = embeddedSelection.automatic()
        refreshEmbeddedTextSelection()
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
        automaticSidecars = false
        subtitlePreferenceRevision++
        if (key != embeddedTrackKey || subtitleUri != null || key == null) embeddedSubtitle = null
        replayEndMs = null
        subtitleUri = null
        subtitleDelayMs = 0
        embeddedSelection = embeddedSelection.manual(key)
        updateEmbeddedTextSelection()
    }

    private fun knownParentFor(uri: Uri): Uri? {
        if (uri == videoUri && videoParentDirectory != null) return videoParentDirectory
        val key = videoSubtitleKey(uri)
        return (recentVideos + legacyQueuedVideos).firstOrNull {
            videoSubtitleKey(it.uri) == key && it.parentDirectory != null
        }?.parentDirectory
    }

    private fun validSidecarParent(video: Uri, parent: Uri?): Uri? = parent?.takeIf {
        video.scheme == "content" && it.scheme == "content" && it.authority == video.authority &&
            DocumentsContract.isTreeUri(it) && persistentReadUris().coversReadAccess(it)
    }

    private fun chooseExternalSubtitle(uri: Uri) {
        chooseEmbeddedSubtitle(null)
        subtitleUri = uri
    }

    private fun chooseNearbySubtitle(nearby: SidecarMatch) {
        val source = videoUri ?: return
        if (nearby !in sidecarMatches.matches) return
        val parent = validSidecarParent(source, videoParentDirectory) ?: run {
            sidecarMatches = SidecarMatches()
            sidecarStatus = SidecarScanStatus.Failed
            return
        }
        chooseExternalSubtitle(DocumentsContract.buildDocumentUriUsingTree(parent, nearby.file.id))
    }
    private fun cancelSidecarDiscovery() {
        sidecarRequestId++
        sidecarCancellation?.cancel()
        sidecarJob?.cancel()
        sidecarCancellation = null
        sidecarJob = null
    }

    private fun startSidecarDiscovery(allowAutomatic: Boolean) {
        val source = videoUri ?: return
        val parent = validSidecarParent(source, videoParentDirectory) ?: run {
            cancelSidecarDiscovery()
            sidecarMatches = SidecarMatches()
            sidecarStatus = if (videoParentDirectory == null) SidecarScanStatus.Idle else SidecarScanStatus.Failed
            return
        }
        cancelSidecarDiscovery()
        val requestId = sidecarRequestId
        val started = SubtitleDiscoveryVersion(videoOpenGeneration, subtitlePreferenceRevision)
        val encoding = englishEncoding
        val cancellation = CancellationSignal()
        sidecarCancellation = cancellation
        sidecarStatus = SidecarScanStatus.Loading
        sidecarJob = sidecarScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val matches = discoverSubtitleSidecars(contentResolver, source, parent, cancellation)
                    val candidate = matches.automatic.takeIf { allowAutomatic }
                    val readable = candidate?.takeIf {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(parent, it.file.id)
                        try {
                            loadSubtitles(uri, persian = false, encoding = encoding).isNotEmpty()
                        } catch (_: IOException) { false }
                        catch (_: SecurityException) { false }
                        catch (_: IllegalArgumentException) { false }
                    }
                    cancellation.throwIfCanceled()
                    Triple(matches, readable, candidate != null && readable == null)
                }
                if (requestId != sidecarRequestId) return@launch
                sidecarMatches = result.first
                sidecarStatus = if (result.third) SidecarScanStatus.Unreadable else SidecarScanStatus.Ready
                val current = SubtitleDiscoveryVersion(videoOpenGeneration, subtitlePreferenceRevision)
                if (result.second != null && encoding == englishEncoding && canApplySidecarAutomatically(
                        started, current, automaticMode = allowAutomatic && automaticSidecars && embeddedSelection.automaticEnglish &&
                            !playbackError && loopStartMs == null && replayEndMs == null,
                        externalFileSelected = subtitleUri != null)) {
                    chooseExternalSubtitle(DocumentsContract.buildDocumentUriUsingTree(parent, result.second!!.file.id))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestId == sidecarRequestId) sidecarStatus = SidecarScanStatus.Failed
            }
        }
    }
    private fun keepReadAccess(uri: Uri): Boolean = try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        // Some providers only grant access for the current task.
        false
    }

    private fun selectLibraryFolder(folder: Uri, path: List<LibrarySubfolder> = emptyList()) {
        libraryFolder = folder
        libraryPath = path
        parentFolderPositions.clear()
        includeLibrarySubfolders = false
        folderBrowseState = LibraryBrowseState()
        libraryTab = LibraryTab.Folder
        libraryFolderRevision++
        history.edit().putString("libraryFolder", folder.toString()).apply()
    }

    private fun navigateLibraryPath(path: List<LibrarySubfolder>) {
        if (path == libraryPath) return
        if (path.size > libraryPath.size) parentFolderPositions[libraryPath.size] = folderBrowseState.position()
        val position = parentFolderPositions[path.size]
        parentFolderPositions.keys.removeAll { it >= path.size }
        libraryPath = path
        folderBrowseState = LibraryBrowseState(position)
    }

    private fun toggleFavoriteFolder(folder: LibraryFolderShortcut) {
        val existing = favoriteFolders.firstOrNull { it.sameFolder(folder) }
        if (existing != null) {
            favoriteFolders = favoriteFolders - existing
        } else {
            if (!persistentReadUris().coversReadAccess(folder.tree)) {
                Toast.makeText(this, "Grant access again to add this folder.", Toast.LENGTH_SHORT).show()
                return
            }
            if (favoriteFolders.size >= favoriteFolderLimit) {
                Toast.makeText(this, "You can save up to $favoriteFolderLimit favorite folders.", Toast.LENGTH_SHORT).show()
                return
            }
            favoriteFolders = favoriteFolders + folder
        }
        history.edit().putString("favoriteFolderShortcuts", writeFavoriteFolderShortcuts(favoriteFolders)).apply()
    }
    private fun persistentReadUris(): Set<Uri> = contentResolver.persistedUriPermissions
        .filter { it.isReadPermission }.map { it.uri }.toSet()

    private fun rememberRecentVideo(uri: Uri, name: String) {
        if (!persistentReadUris().coversReadAccess(uri)) return
        val old = knownLibraryVideo(uri)
        recentVideos = addRecentVideo(recentVideos, LibraryVideo(uri, name,
            parentPath = if (videoParentDirectory != null) videoParentPath else old?.parentPath,
            parentDirectory = videoParentDirectory ?: old?.parentDirectory,
            bookmark = currentProgress(uri) ?: old?.bookmark))
        history.edit().putString("recentVideos", writeVideoEntries(recentVideos)).apply()
    }

    private fun knownLibraryVideo(uri: Uri): LibraryVideo? {
        val key = videoSubtitleKey(uri)
        val matches = (recentVideos + legacyQueuedVideos).filter { videoSubtitleKey(it.uri) == key }
        val first = matches.firstOrNull() ?: return null
        return if (first.bookmark != null) first else first.copy(bookmark = matches.firstNotNullOfOrNull { it.bookmark })
    }

    private fun currentProgress(uri: Uri): PlaybackBookmark? {
        if (uri != videoUri || !videoReady || playbackError || !player.isCurrentMediaItemSeekable ||
            player.currentMediaItem?.localConfiguration?.uri != uri) return null
        return capturePlaybackBookmark(player.currentPosition, player.duration, player.playbackState == Player.STATE_ENDED)
    }

    private fun rememberCurrentProgress() {
        val uri = videoUri ?: return
        if (returnToStreams && uri.isOnlineVideo) {
            if (!player.isCurrentMediaItemLive) {
                currentProgress(uri)?.takeIf { it.durationMs > 0 }?.let { streamBookmarks.record(uri.toString(), it) }
            }
            return
        }
        if (uri.scheme != "content" || !persistentReadUris().coversReadAccess(uri)) return
        val bookmark = currentProgress(uri) ?: return
        val old = knownLibraryVideo(uri)
        val entry = LibraryVideo(uri, old?.name ?: "Video",
            parentPath = if (videoParentDirectory != null) videoParentPath else old?.parentPath,
            parentDirectory = videoParentDirectory ?: old?.parentDirectory, bookmark = bookmark)
        val updated = addRecentVideo(recentVideos, entry)
        if (updated != recentVideos) {
            recentVideos = updated
            history.edit().putString("recentVideos", writeVideoEntries(recentVideos)).apply()
        }
    }
    private fun openLibraryVideo(video: LibraryVideo, startOver: Boolean = false) {
        val uri = video.uri
        if (!persistentReadUris().coversReadAccess(uri)) {
            Toast.makeText(this, "Select the file or folder again to restore access.", Toast.LENGTH_SHORT).show()
            return
        }

        showLibrary = false
        when {
            uri == videoUri && !playbackError -> {
                val parent = validSidecarParent(uri, video.parentDirectory)
                if (parent != null) videoParentPath = video.parentPath
                if (parent != null && parent != videoParentDirectory) {
                    videoParentDirectory = parent
                    startSidecarDiscovery(allowAutomatic = automaticSidecars)
                }
                if (startOver || player.playbackState == Player.STATE_ENDED) {
                    clearAbLoop(); replayEndMs = null; player.seekTo(0)
                    rememberCurrentProgress()
                }
                player.play()
            }
            startOver -> openVideo(uri, positionMs = 0, parentDirectory = video.parentDirectory, parentPath = video.parentPath)
            history.getString("video", null) == uri.toString() -> {
                restoreSavedPlayback(video.parentDirectory, video.parentPath)
                player.play()
            }
            else -> openVideo(uri, parentDirectory = video.parentDirectory, parentPath = video.parentPath)
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
        if (uri == demoVideo) return "Sample video"
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

    private fun openSavedWordScene(entry: SavedWord): Boolean {
        fun unavailable(message: String): Boolean {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return false
        }

        val source = Uri.parse(entry.sourceUri)
        val current = videoUri
        val sameSource = current != null && (
            current.savedWordSourceId() == entry.sourceUri ||
                (current.scheme == "content" && source.scheme == "content" &&
                    videoSubtitleKey(current) == videoSubtitleKey(source))
            )
        if (entry.positionMs < 0) return unavailable("This scene has an invalid timestamp.")
        if (sameSource && videoReady && !playbackError) {
            if (!player.isCurrentMediaItemSeekable || player.duration <= 0) {
                return unavailable("This video does not support seeking to the saved position.")
            }
            if (entry.positionMs >= player.duration) {
                return unavailable("The saved timestamp is outside this video.")
            }
            player.pause()
            seekWithinVideo(entry.positionMs)
            return true
        }

        if (entry.sourceUri.startsWith("remote:")) {
            return unavailable("Reopen the same online video and try again once it is ready.")
        }
        if (source != demoVideo && (source.scheme != "content" || !persistentReadUris().coversReadAccess(source))) {
            return unavailable("Reopen the video from Files to restore access to this scene.")
        }
        openVideo(source, positionMs = entry.positionMs, autoplay = false)
        return true
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
                    sourceUri = lookup.source.savedWordSourceId(),
                    sourceName = lookup.sourceName
                )
            )
            lookup.saveState = WordSaveState.Saved
        } catch (_: SQLiteException) {
            lookup.saveState = WordSaveState.Ready
            Toast.makeText(this, "Could not save the word.", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PlayerScreen() {
        val englishTrack = subtitleTrack(subtitleUri, englishEncoding)
        val persianTrack = subtitleTrack(translationUri, persianEncoding, persian = true)
        val cues = englishTrack.cues
        var lookup by remember(videoUri, subtitleUri, embeddedTrackKey, subtitleLookupMode) { mutableStateOf<Lookup?>(null) }
        var editingMeaning by remember(videoUri, subtitleUri, embeddedTrackKey, subtitleLookupMode) { mutableStateOf<Lookup?>(null) }
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
        val meaningTimeoutMs = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
            3_000L, containsIcons = true, containsText = true, containsControls = true
        ) ?: 3_000L
        var listeningMode by remember(videoUri) { mutableStateOf(false) }
        val hasVisibleSubtitles =
            (englishSubtitlesVisible && (englishTrack.cues.isNotEmpty() || embeddedTrackKey != null)) ||
                (persianSubtitlesVisible && persianTrack.cues.isNotEmpty())
        var controlsVisible by remember(videoUri) { mutableStateOf(true) }
        var controlsInteraction by remember { mutableIntStateOf(0) }
        var showPlayerMenu by remember { mutableStateOf(false) }
        var showPlaybackTools by remember { mutableStateOf(false) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showHelp by rememberSaveable { mutableStateOf(false) }
        val recentBrowseState = remember { LibraryBrowseState() }
        val streamsBrowseState = remember { StreamsBrowseState() }
        val flashcardsState = rememberSaveableStateHolder()
        var showDictionary by rememberSaveable { mutableStateOf(false) }
        var dictionarySeed by rememberSaveable { mutableStateOf("") }
        var showDialogues by remember(videoUri, subtitleUri) { mutableStateOf(false) }
        var showSubtitleMenu by remember(videoUri) { mutableStateOf(false) }
        var localVideoName by remember(videoUri) { mutableStateOf<String?>(null) }
        val sourceName = if (videoUri?.isOnlineVideo == true) streamTitle ?: "Online video" else localVideoName
        val scope = rememberCoroutineScope()
        fun selectHome(destination: HomeDestination) {
            showLibrary = true
            showStreams = destination == HomeDestination.Streams
            showWords = destination == HomeDestination.Flashcards
        }
        fun openHomeFromVideo() {
            lookup = null
            selectHome(if (returnToStreams) HomeDestination.Streams else HomeDestination.Files)
        }
        LaunchedEffect(sleepDeadlineMs) {
            val deadline = sleepDeadlineMs ?: return@LaunchedEffect
            while (sleepDeadlineMs == deadline) {
                sleepRemainingMs = sleepRemainingMs(deadline, SystemClock.elapsedRealtime())
                if (expireSleepTimer()) break
                delay(1_000)
            }
        }

        LaunchedEffect(controlsVisible, frame.playing, controlsLocked, dragPosition, showSubtitleMenu,
            showPlayerMenu, showPlaybackTools, showSettings, showDialogues, showDictionary, editingMeaning, controlsInteraction, foreground, controlsTimeoutMs) {
            if (controlsVisible && frame.playing && !controlsLocked && dragPosition < 0 &&
                !showSubtitleMenu && !showPlayerMenu && !showPlaybackTools && !showSettings &&
                !showDialogues && !showDictionary && editingMeaning == null && foreground) {
                delay(controlsTimeoutMs)
                controlsVisible = false
            }
        }
        BackHandler(
            enabled = !pictureInPicture && (showWords || showLibrary || videoUri != null)
        ) {
            when {
                showStreams -> showStreams = false
                showWords -> showWords = false
                showLibrary -> finish()
                controlsLocked -> { viewport = viewport.unlock(); controlsVisible = true; controlsInteraction++ }
                lookup != null -> lookup = null
                fullscreen -> { viewport = viewport.exitFullscreen(); controlsVisible = true; controlsInteraction++ }
                else -> openHomeFromVideo()
            }
        }
        LaunchedEffect(videoUri) {
            val source = videoUri?.takeUnless { it.isOnlineVideo } ?: return@LaunchedEffect
            localVideoName = withContext(Dispatchers.IO) { readVideoName(source) } ?: "Local video"
        }
        LaunchedEffect(videoUri, videoReady, sourceName, videoParentDirectory, videoParentPath) {
            val uri = videoUri ?: return@LaunchedEffect
            val name = sourceName ?: return@LaunchedEffect
            if (videoReady && !playbackError && uri.scheme == "content") rememberRecentVideo(uri, name)
        }
        LaunchedEffect(videoUri, foreground, pictureInPicture) {
            if (videoUri == null || (!foreground && !pictureInPicture)) return@LaunchedEffect
            while (true) {
                delay(10_000)
                if (player.isPlaying) rememberCurrentProgress()
            }
        }
        LaunchedEffect(showWords, showLibrary, videoUri) {
            pronunciation.stop()
            if (showWords || showLibrary) {
                lookup = null
                viewport = PlaybackViewport()
                clearAbLoop()
                replayEndMs = null
                player.pause()
                rememberCurrentProgress()
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
            val folder = parseLibraryFolderUri(uri?.toString())
            if (folder != null) {
                if (keepReadAccess(folder)) selectLibraryFolder(folder)
                else Toast.makeText(this, "Could not save access to this folder.", Toast.LENGTH_SHORT).show()
            }
        }
        val selectSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                keepReadAccess(uri)
                chooseExternalSubtitle(uri)
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
        val selectedPersonalMeaning = lookup?.word?.let(::normalizeDictionaryTerm)?.let(personalMeanings::get)
        LaunchedEffect(lookup, selectedPersonalMeaning, lookup?.requestRevision) {
            val selected = lookup ?: return@LaunchedEffect
            val revision = selected.requestRevision
            val request = selected.requests.consume()
            val requestContext = kotlinx.coroutines.currentCoroutineContext()
            selected.loading = true
            val result = withContext(Dispatchers.IO) {
                wordLookup.resolve(selected.word, personalMeanings, request) { requestContext.ensureActive() }
            }
            if (lookup !== selected || selected.requestRevision != revision) return@LaunchedEffect
            selected.meaning = result.meaning
            selected.meaningSource = result.source
            selected.failure = result.failure
            if (result.failure != null) selected.expanded = true
            selected.loading = false
            selected.resultRevision++
        }
        val timedLookup = lookup?.takeIf { it.canAutoDismiss }
        LaunchedEffect(timedLookup, timedLookup?.resultRevision, timedLookup?.saveState, meaningTimeoutMs) {
            val selected = timedLookup ?: return@LaunchedEffect
            val revision = selected.resultRevision
            val saveState = selected.saveState
            delay(meaningTimeoutMs)
            if (lookup === selected && selected.canAutoDismiss &&
                selected.resultRevision == revision && selected.saveState == saveState
            ) {
                lookup = null
            }
        }

        if (showDictionary && !pictureInPicture && !controlsLocked) {
            DictionarySearchPanel(
                dictionary = packagedDictionary, personal = personalMeanings,
                personalReady = personalEntries != null, dao = personalDictionary,
                speech = pronunciation, initialTerm = dictionarySeed,
                beforeSpeak = {
                    val resume = player.playWhenReady && player.playbackState != Player.STATE_ENDED
                    lookup?.let { selected ->
                        selected.studyAction = if (resume || selected.studyAction == StudyAction.Resume)
                            StudyAction.Resume else StudyAction.Close
                    }
                    player.pause()
                    replayEndMs = null
                },
                onDismiss = { showDictionary = false; controlsVisible = true; controlsInteraction++ }
            )
        }
        if (showStreams && !pictureInPicture) {
            StreamsScreen(
                state = streamsBrowseState,
                bookmarks = streamBookmarks,
                onOpenFiles = { selectHome(HomeDestination.Files) },
                onPlay = { link, title, positionMs ->
                    lookup = null
                    showStreams = false
                    showLibrary = false
                    openVideo(Uri.parse(link.url), positionMs = positionMs, onlineFormat = link.format,
                        fromStreams = true, title = title)
                },
                onReturnToPlayback = if (videoUri != null) ({ showStreams = false; showLibrary = false }) else null,
                bottomBar = { HomeNavigation(HomeDestination.Streams, ::selectHome) }
            )
            return
        }
        if (showWords && !pictureInPicture) {
            flashcardsState.SaveableStateProvider("flashcards") {
                SavedWordsScreen(
                    vocabulary, pronunciation,
                    onOpenScene = { entry ->
                        if (openSavedWordScene(entry)) {
                            lookup = null
                            showLibrary = false
                            showWords = false
                        }
                    },
                    onBack = if (showLibrary) null else ({ showWords = false }),
                    bottomBar = { HomeNavigation(HomeDestination.Flashcards, ::selectHome) }
                )
            }
            return
        }
        if (showLibrary && !pictureInPicture) {
            VideoLibraryScreen(
                folder = libraryFolder,
                folderRevision = libraryFolderRevision,
                folderPath = libraryPath,
                onFolderPathChange = ::navigateLibraryPath,
                favoriteFolders = favoriteFolders,
                recent = recentVideos,
                tab = libraryTab,
                onTabChange = { libraryTab = it },
                browseState = if (libraryTab == LibraryTab.Folder) folderBrowseState else recentBrowseState,
                includeSubfolders = includeLibrarySubfolders,
                onIncludeSubfoldersChange = { includeLibrarySubfolders = it },
                onChooseFolder = { selectFolder.launch(libraryFolder) },
                onOpenFavorite = { folder ->
                    if (persistentReadUris().coversReadAccess(folder.tree)) selectLibraryFolder(folder.tree, folder.path)
                    else {
                        Toast.makeText(this, "Grant access to this folder again.", Toast.LENGTH_SHORT).show()
                        selectFolder.launch(null)
                    }
                },
                onToggleFavorite = ::toggleFavoriteFolder,
                onChooseFile = { selectVideo.launch(arrayOf("video/*")) },
                onOpenLink = { link, credentials ->
                    lookup = null
                    showLibrary = false
                    openVideo(Uri.parse(link.url), onlineFormat = link.format, smbCredentials = credentials)
                },
                onOpen = { openLibraryVideo(it) },
                onRestart = { openLibraryVideo(it, startOver = true) },
                canReturnToPlayback = videoUri != null,
                skipSeconds = skipSeconds,
                onPlaySample = { showLibrary = false; openDemoVideo() },
                bottomBar = { HomeNavigation(HomeDestination.Files, ::selectHome) },
                onDictionary = { dictionarySeed = ""; showDictionary = true },
                onReturnToPlayback = { showLibrary = false }
            )
            return
        }

        val canSeek = frame.source == videoUri && frame.seekable && !playbackError
        val dialogueRanges = remember(cues, subtitleDelayMs, frame.durationMs) {
            cues.mapNotNull { it.toDialogueRange(subtitleDelayMs, frame.durationMs) }
        }
        val navigationDialogues = remember(dialogueRanges) { dialogueRanges.distinctBy { it.startMs } }
        LaunchedEffect(navigationDialogues, pauseAfterDialogue, foreground) {
            autoPauseEnds = dialoguePauseBoundaries(navigationDialogues)
            lastDialoguePosition = player.currentPosition
        }
        val previousDialogue = adjacentDialogue(navigationDialogues, frame.positionMs, false)
        val nextDialogue = adjacentDialogue(navigationDialogues, frame.positionMs, true)
        fun replayDialogueAt(positionMs: Long): DialogueRange? {
            return englishTrack.timeline.firstActiveCueAt(positionMs - subtitleDelayMs)
                ?.toDialogueRange(subtitleDelayMs, frame.durationMs)
                ?: dialogueRanges.getOrNull(dialogueUpperBound(dialogueRanges, positionMs) - 1)
        }
        val canReplayDialogue = canSeek && !frame.live && embeddedTrackKey == null
        val quickReplayAvailable = canReplayDialogue && (showPlayerMenu || listeningMode) &&
            replayDialogueAt(frame.positionMs) != null
        fun replayDialogue(dialogue: DialogueRange) {
            if (!canReplayDialogue || !player.isCurrentMediaItemSeekable || player.isCurrentMediaItemLive) return
            lookup = null
            controlsVisible = false
            seekWithinVideo(dialogue.startMs, dialogue.endMs)
        }
        fun replayCurrentDialogue() {
            if (canReplayDialogue) replayDialogueAt(player.currentPosition)?.let(::replayDialogue)
        }
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
                            if (lookup != null) {
                                lookup = null
                                controlsVisible = true
                            } else controlsVisible = !controlsVisible
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
                        Text("Loading…", color = Color.White)
                    }
                }
                if (!pictureInPicture && (videoUri == null || playbackError || englishTrack.failed || persianTrack.failed)) {
                    val failedOnline = playbackError && videoUri?.isOnlineVideo == true
                    val message = when {
                        failedOnline && videoUri?.scheme == "smb" ->
                            "Could not play the network file. Check the path and share permissions."
                        failedOnline -> "Could not play the stream. Check your connection, URL, and stream type."
                        playbackError -> "Could not play this file. Select the video again."
                        englishTrack.failed -> "Could not read the English subtitles. Choose a valid SRT or WebVTT file up to 8 MB."
                        persianTrack.failed -> "Could not read the Persian subtitles. Choose a valid SRT or WebVTT file up to 8 MB."
                        else -> "Play the sample or choose your own video and subtitles."
                    }
                    Column(
                        Modifier.align(Alignment.Center).padding(20.dp)
                            .background(background, RoundedCornerShape(12.dp)).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (videoUri == null) PinesIdentityBanner()
                        Text(message, color = PinesPalette.Ink, textAlign = TextAlign.Center)
                        if (videoUri == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { openDemoVideo() }) { Text("Sample") }
                                TextButton(onClick = { showLibrary = true }) { Text("Choose video") }
                                TextButton(onClick = { showHelp = true }) { Text("Help") }
                            }
                        }
                        if (failedOnline && !controlsLocked) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    playbackError = false
                                    videoReady = false
                                    if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
                                    player.prepare()
                                    player.play()
                                }) { Text("Try again") }
                                if (returnToStreams) {
                                    TextButton(onClick = ::openHomeFromVideo) { Text("Browse Streams") }
                                }
                                TextButton(onClick = { selectHome(HomeDestination.Files) }) { Text("Open Files") }
                            }
                        }
                    }
                }
                if (!pictureInPicture) {
                    val subtitlesAboveControls = !controlsLocked && videoUri != null &&
                        (controlsVisible || lookup?.subtitlesAboveControls == true)
                    SubtitleOverlay(
                        topReserved = if (controlsVisible && !controlsLocked) 72.dp else 12.dp,
                        meaningVisible = lookup != null && !controlsLocked && !listeningMode,
                        subtitles = {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    .padding(bottom = if (subtitlesAboveControls) 104.dp else 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val source = frame.source
                                val subtitle = when {
                                    listeningMode -> null
                                    embeddedTrackKey != null -> embeddedSubtitle
                                    else -> englishTrack.timeline.activeAt(frame.positionMs - subtitleDelayMs)
                                }
                                if (englishSubtitlesVisible && source != null && source == videoUri && subtitle != null) {
                                    TappableSubtitle(subtitle.text, subtitleSize, Color(subtitleTextTone.argb), subtitleBackgroundOpacity,
                                        enabled = !controlsLocked, personalMeanings = personalMeanings,
                                        mode = subtitleLookupMode, selectedRange = lookup?.selection?.highlightFor(subtitle)) { term ->
                                        if (controlsLocked) return@TappableSubtitle
                                        controlsVisible = false
                                        subtitlePreferenceRevision++
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
                                            selection = SubtitleSelection(subtitle, term.start, term.endExclusive),
                                            positionMs = frame.positionMs,
                                            subtitleStartMs = subtitle.startMs,
                                            replayCue = if (embeddedTrackKey == null) {
                                                englishTrack.timeline.cueForSelectionAt(
                                                    frame.positionMs - subtitleDelayMs, term.start, term.endExclusive
                                                )
                                            } else null,
                                            source = source,
                                            sourceName = sourceName ?: "Local video",
                                            subtitlesAboveControls = subtitlesAboveControls,
                                            studyAction = studyAction
                                        )
                                    }
                                }
                                if (!listeningMode && persianSubtitlesVisible && source != null && source == videoUri) {
                                    persianTrack.timeline.activeAt(frame.positionMs - translationDelayMs)?.let {
                                        PersianSubtitle(it.text, subtitleSize, subtitleBackgroundOpacity)
                                    }
                                }
                                if (listeningMode && !controlsLocked) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                                        val buttonBackground = Modifier.background(
                                            Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp)
                                        )
                                        TextButton(onClick = { listeningMode = false }, modifier = buttonBackground) {
                                            Text("Show subtitles", color = PinesPalette.Ink)
                                        }
                                        if (quickReplayAvailable) {
                                            TextButton(onClick = ::replayCurrentDialogue, modifier = buttonBackground) {
                                                Text("Replay dialogue", color = PinesPalette.Ink)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        meaning = {
                            lookup?.takeUnless { controlsLocked }?.let { selected ->
                                val replayRange = selected.replayCue
                                    ?.takeIf { canReplayDialogue && selected.source == videoUri }
                                    ?.toDialogueRange(subtitleDelayMs, frame.durationMs)
                                MeaningCard(
                                    lookup = selected,
                                    pronunciation = pronunciation,
                                    onOnline = { selected.requests.online(); selected.requestRevision++; selected.loading = true },
                                    onRetry = { selected.requests.offline(); selected.requestRevision++; selected.loading = true },
                                    phraseChoices = {
                                        SubtitlePhraseChoices(selected.selection, personalMeanings, packagedDictionary) { term ->
                                            if (lookup === selected) {
                                                lookup = Lookup(
                                                    word = term.text,
                                                    meaning = null,
                                                    dialogue = selected.dialogue,
                                                    selection = selected.selection.copy(start = term.start, endExclusive = term.endExclusive),
                                                    positionMs = selected.positionMs,
                                                    subtitleStartMs = selected.subtitleStartMs,
                                                    replayCue = selected.replayCue,
                                                    source = selected.source,
                                                    sourceName = selected.sourceName,
                                                    subtitlesAboveControls = selected.subtitlesAboveControls,
                                                    studyAction = selected.studyAction
                                                )
                                            }
                                        }
                                    },
                                    onReplay = replayRange?.let { range ->
                                        { if (lookup === selected) replayDialogue(range) }
                                    },
                                    onDictionary = { dictionarySeed = selected.word; showDictionary = true },
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
                        }
                    )
                }
                if (!pictureInPicture && videoUri != null) AnimatedVisibility(
                    visible = controlsVisible && !controlsLocked,
                    modifier = Modifier.matchParentSize(),
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120))
                ) {
                    val progress = if (frame.durationMs > 0)
                        (frame.positionMs.toFloat() / frame.durationMs).coerceIn(0f, 1f) else 0f
                    val shownPosition = if (dragPosition >= 0) (dragPosition * frame.durationMs).toLong() else frame.positionMs
                    PlayerOverlay(
                        title = sourceName ?: "Video",
                        backLabel = if (returnToStreams) "Back to Streams" else "Back to Files",
                        playing = frame.playing,
                        canPlay = !playbackError,
                        canSeek = canSeek,
                        progress = if (dragPosition >= 0) dragPosition else progress,
                        positionLabel = if (frame.live) "LIVE" else formatTime(shownPosition) + " / " +
                            (if (frame.durationMs > 0) formatTime(frame.durationMs) else "—"),
                        fullscreen = fullscreen,
                        skipSeconds = skipSeconds,
                        onBack = ::openHomeFromVideo,
                        onPlayPause = {
                            controlsInteraction++
                            if (lookup?.studyAction != null) lookup = null
                            replayEndMs = null
                            when {
                                player.playbackState == Player.STATE_ENDED -> { player.seekTo(0); player.play() }
                                player.playWhenReady -> player.pause()
                                else -> player.play()
                            }
                            rememberCurrentProgress()
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
                                PlayerIconButton(PlayerGlyph.Subtitles, "Subtitles", onClick = { showSubtitleMenu = true })
                                DropdownMenu(expanded = showSubtitleMenu, onDismissRequest = { showSubtitleMenu = false }) {
                                    val available = embeddedOptions.any { it.key == embeddedTrackKey }
                                    val status = when {
                                        !englishSubtitlesVisible -> "English subtitles hidden"
                                        subtitleUri != null -> "English subtitles: external file"
                                        playbackError -> "Playback error. Embedded tracks are unavailable."
                                        embeddedSelection.automaticEnglish && available -> "Embedded English: automatic"
                                        embeddedSelection.automaticEnglish && !videoReady && !playbackError -> "Waiting for subtitle track information…"
                                        embeddedSelection.automaticEnglish -> "No embedded English track found. Choose an SRT or VTT file."
                                        embeddedTrackKey != null && !available -> "The saved track is unavailable."
                                        available -> "Embedded subtitles: manual selection"
                                        else -> "Primary subtitles off"
                                    }
                                    DropdownMenuItem(enabled = false,
                                        text = { Text(status, maxLines = 3) }, onClick = { })
                                    DropdownMenuItem(text = {
                                        Text((if (embeddedSelection.automaticEnglish) "✓ " else "") + "Auto-select embedded English")
                                    }, onClick = {
                                        showSubtitleMenu = false
                                        lookup = null
                                        chooseAutomaticEnglishSubtitle()
                                    })
                                    DropdownMenuItem(text = { Text(if (englishSubtitlesVisible) "Hide English" else "Show English") }, onClick = {
                                        showSubtitleMenu = false
                                        lookup = null
                                        englishSubtitlesVisible = !englishSubtitlesVisible
                                        history.edit().putBoolean("englishSubtitlesVisible", englishSubtitlesVisible).apply()
                                    })
                                    DropdownMenuItem(text = { Text("Choose English subtitles") }, onClick = {
                                        showSubtitleMenu = false
                                        selectSubtitle.launch(arrayOf("*/*"))
                                    })
                                    if (videoParentDirectory != null) {
                                        HorizontalDivider(color = PinesPalette.AccentContainer)
                                        DropdownMenuItem(enabled = sidecarStatus != SidecarScanStatus.Loading,
                                            text = { Text("Find matching subtitle files") }, onClick = {
                                                lookup = null
                                                startSidecarDiscovery(allowAutomatic = false)
                                            })
                                        val nearbyStatus = when (sidecarStatus) {
                                            SidecarScanStatus.Loading -> "Scanning folder…"
                                            SidecarScanStatus.Failed -> "Could not read the folder. Try again."
                                            SidecarScanStatus.Unreadable -> "Could not read the matching file. You can choose a file manually."
                                            SidecarScanStatus.Ready -> if (sidecarMatches.matches.isEmpty()) "No matching English or unlabeled subtitles found." else null
                                            SidecarScanStatus.Idle -> null
                                        }
                                        nearbyStatus?.let { message ->
                                            DropdownMenuItem(enabled = false, text = { Text(message, maxLines = 3) }, onClick = { })
                                        }
                                        sidecarMatches.matches.forEach { nearby ->
                                            DropdownMenuItem(text = {
                                                Text(nearby.file.name + if (!nearby.english) " · Unknown language" else if (nearby.forced) " · Forced" else "",
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }, onClick = {
                                                showSubtitleMenu = false
                                                lookup = null
                                                chooseNearbySubtitle(nearby)
                                            })
                                        }
                                        HorizontalDivider(color = PinesPalette.AccentContainer)
                                    }
                                    embeddedOptions.forEachIndexed { index, option ->
                                        DropdownMenuItem(
                                            text = {
                                                val selected = if (option.key == embeddedTrackKey) "✓ " else ""
                                                Text(selected + option.displayName(index + 1))
                                            },
                                            onClick = {
                                                showSubtitleMenu = false
                                                lookup = null
                                                chooseEmbeddedSubtitle(option.key)
                                            }
                                        )
                                    }
                                    if (embeddedSelection.automaticEnglish || embeddedTrackKey != null || subtitleUri != null) {
                                        DropdownMenuItem(text = { Text("Turn off primary subtitles") }, onClick = {
                                            showSubtitleMenu = false
                                            lookup = null
                                            chooseEmbeddedSubtitle(null)
                                        })
                                    }
                                    DropdownMenuItem(text = { Text("Choose Persian subtitles") }, onClick = {
                                        showSubtitleMenu = false
                                        selectTranslation.launch(arrayOf("*/*"))
                                    })
                                    if (translationUri != null) {
                                        DropdownMenuItem(text = { Text("Remove Persian subtitles") }, onClick = {
                                            showSubtitleMenu = false
                                            translationUri = null
                                            translationDelayMs = 0
                                        })
                                    }
                                }
                            }

                            PlayerIconButton(PlayerGlyph.Settings, "Settings", onClick = { showSettings = true })
                            Box {
                                PlayerIconButton(PlayerGlyph.More, "Playback options", onClick = { showPlayerMenu = true })
                                DropdownMenu(expanded = showPlayerMenu, onDismissRequest = { showPlayerMenu = false }) {
                                    DropdownMenuItem(text = { Text("Files") }, onClick = { showPlayerMenu = false; lookup = null; showLibrary = true })
                                    DropdownMenuItem(text = { Text("Flashcards") }, onClick = { showPlayerMenu = false; lookup = null; showWords = true })
                                    DropdownMenuItem(text = { Text("Search dictionary") }, onClick = {
                                        showPlayerMenu = false; dictionarySeed = ""; showDictionary = true
                                    })
                                    if (hasVisibleSubtitles || listeningMode) {
                                        DropdownMenuItem(
                                            text = { Text(if (listeningMode) "Show subtitles" else "Listen without subtitles") },
                                            onClick = {
                                                showPlayerMenu = false
                                                lookup = null
                                                listeningMode = !listeningMode
                                            }
                                        )
                                    }
                                    if (quickReplayAvailable) {
                                        DropdownMenuItem(text = { Text("Replay dialogue") }, onClick = {
                                            showPlayerMenu = false
                                            replayCurrentDialogue()
                                        })
                                    }
                                    if (canSeek && dialogueRanges.isNotEmpty()) {
                                        DropdownMenuItem(text = { Text("Search dialogues") }, onClick = {
                                            showPlayerMenu = false
                                            lookup = null
                                            showDialogues = true
                                        })
                                    }
                                    if (abLoop != null) {
                                        DropdownMenuItem(text = { Text("Clear loop") }, onClick = {
                                            showPlayerMenu = false
                                            clearAbLoop()
                                        })
                                    }
                                    DropdownMenuItem(text = { Text("Study and repeat") }, onClick = { showPlayerMenu = false; showPlaybackTools = true })
                                    if (supportsPictureInPicture && videoReady && !playbackError) {
                                        DropdownMenuItem(text = { Text("Picture-in-picture") }, onClick = { showPlayerMenu = false; lookup = null; enterVideoPictureInPicture() })
                                    }
                                    DropdownMenuItem(text = { Text("Help") }, onClick = { showPlayerMenu = false; showHelp = true })
                                    DropdownMenuItem(text = { Text("Play sample") }, onClick = { showPlayerMenu = false; lookup = null; openDemoVideo() })
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
                        Text("Unlock controls", color = Color.White)
                    }
                }
            }

        }
        if (showPlaybackTools && !pictureInPicture && !controlsLocked) {
            ModalBottomSheet(onDismissRequest = { showPlaybackTools = false }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("Study tools", style = MaterialTheme.typography.titleMedium)
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
                    }) { Text(loopStartMs?.let { "A: " + formatTime(it) } ?: "Set A") }
                    TextButton(enabled = canSeek && loopStartMs != null && !player.isCurrentMediaItemLive, onClick = {
                        val loop = playbackLoop(loopStartMs ?: 0, player.currentPosition, player.duration)
                        if (loop == null) {
                            Toast.makeText(this@MainActivity, "Set B at least half a second after A.", Toast.LENGTH_SHORT).show()
                        } else {
                            lookup = null
                            startAbLoop(loop)
                        }
                    }) { Text(abLoop?.let { "B: " + formatTime(it.endMs) } ?: "Set B") }
                    TextButton(enabled = loopStartMs != null || abLoop != null, onClick = { clearAbLoop() }) {
                        Text("Clear loop")
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
                    }) { Text("Start / Previous") }
                    TextButton(enabled = canSeek && nextDialogue != null, onClick = {
                        lookup = null
                        adjacentDialogue(navigationDialogues, player.currentPosition, true)?.let {
                            seekWithinVideo(it.startMs)
                        }
                    }) { Text("Next dialogue") }
                    TextButton(onClick = { showSettings = true }) { Text("Settings") }
                    TextButton(
                        enabled = canSeek && dialogueRanges.isNotEmpty(),
                        onClick = {
                            lookup = null
                            showPlaybackTools = false
                            showDialogues = true
                        }
                    ) { Text("Dialogues") }

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
            DialogueList(
                dialogues = dialogueRanges,
                positionMs = frame.positionMs,
                onSeek = { dialogue ->
                    showDialogues = false
                    seekWithinVideo(dialogue.startMs)
                },
                onReplay = { dialogue ->
                    showDialogues = false
                    seekWithinVideo(dialogue.startMs, dialogue.endMs)
                },
                onLoop = { dialogue ->
                    val loop = if (player.isCurrentMediaItemSeekable && !player.isCurrentMediaItemLive) {
                        playbackLoop(dialogue.startMs, dialogue.endMs, player.duration)
                    } else null
                    if (loop == null) {
                        Toast.makeText(this@MainActivity, "Could not loop this range.", Toast.LENGTH_SHORT).show()
                    } else {
                        showDialogues = false
                        controlsVisible = true
                        startAbLoop(loop)
                    }
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
                subtitleLookupMode = subtitleLookupMode,
                onSubtitleLookupMode = {
                    subtitleLookupMode = it
                    pronunciation.stop()
                    history.edit().putString("subtitleLookupMode", it.name).apply()
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
    val selection: SubtitleSelection,
    val positionMs: Long,
    val subtitleStartMs: Long,
    val replayCue: SubtitleCue?,
    val source: Uri,
    val sourceName: String,
    val subtitlesAboveControls: Boolean,
    studyAction: StudyAction?
) {
    var loading by mutableStateOf(true)
    var meaningSource by mutableStateOf("")
    val requests = WordLookupRequestGate()
    var requestRevision by mutableStateOf(0)
    var failure by mutableStateOf<LookupFailure?>(null)
    var resultRevision by mutableStateOf(0)
    var expanded by mutableStateOf(false)
    var meaning by mutableStateOf(meaning)
    var studyAction by mutableStateOf(studyAction)
    var saveState by mutableStateOf(WordSaveState.Ready)

    val canAutoDismiss: Boolean
        get() = !loading && meaning != null && !expanded && studyAction == null && saveState != WordSaveState.Saving
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
    mode: SubtitleLookupMode,
    selectedRange: IntRange?,
    onTerm: (SubtitleTerm) -> Unit
) {
    val currentOnTerm by rememberUpdatedState(onTerm)
    val terms = remember(text, personalMeanings, mode) { subtitleTerms(text, personalMeanings, mode) }
    val annotated = remember(text, enabled, terms, selectedRange) {
        buildAnnotatedString {
            append(text)
            if (!enabled) return@buildAnnotatedString
            terms.forEach { term ->
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
            selectedRange?.let { range ->
                addStyle(SpanStyle(background = PinesPalette.AccentContainer, color = PinesPalette.Ink),
                    start = range.first, end = range.last + 1)
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
    onOnline: () -> Unit,
    onRetry: () -> Unit,
    phraseChoices: @Composable () -> Unit,
    onReplay: (() -> Unit)?,
    onDictionary: () -> Unit,
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
            .glassPanel(MaterialTheme.shapes.large, overVideo = true)
            .verticalScroll(remember(lookup) { ScrollState(0) }).padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, color = PinesPalette.Accent, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (lookup.loading) "Looking up meaning…" else meaning?.persian ?: lookup.meaningSource,
                    color = PinesPalette.Ink, maxLines = if (lookup.expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr))
            }
            if (lookup.loading) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = PinesPalette.Ink) }
            } else if (meaning != null) {
                TextButton(enabled = lookup.saveState == WordSaveState.Ready, onClick = onSave) {
                    Text(when (lookup.saveState) {
                        WordSaveState.Ready -> "Save"
                        WordSaveState.Saving -> "…"
                        WordSaveState.Saved -> "Saved"
                    }, color = PinesPalette.Ink)
                }
            }
            PlayerIconButton(PlayerGlyph.More, "Word options", onClick = { lookup.expanded = !lookup.expanded })
        }
        if (!lookup.loading && meaning != null) {
            Text(lookup.meaningSource, color = PinesPalette.Accent, style = MaterialTheme.typography.labelSmall)
        }
        if (!lookup.loading && meaning == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (lookup.failure == LookupFailure.OfflineUnavailable) {
                    TextButton(onClick = onRetry) { Text("Retry offline lookup", color = PinesPalette.Ink) }
                }
                TextButton(onClick = onOnline) {
                    Text(if (lookup.failure != null && lookup.failure != LookupFailure.OfflineUnavailable)
                        "Retry online translation" else "Translate online", color = PinesPalette.Ink)
                }
                if (!lookup.expanded && lookup.studyAction == null) {
                    TextButton(onClick = onDismiss) { Text("Close", color = PinesPalette.Ink) }
                }
            }
            Text("Only the selected term is sent to MyMemory. This requires internet access and is subject to a daily quota.",
                color = PinesPalette.Accent, style = MaterialTheme.typography.labelSmall)
        }
        if (lookup.expanded) {
            if (onReplay != null) {
                TextButton(onClick = onReplay) { Text("Replay this dialogue", color = PinesPalette.Ink) }
            }
            phraseChoices()
            TextButton(onClick = onDictionary) { Text("Search dictionary", color = PinesPalette.Ink) }
            TextButton(enabled = canEditMeaning && !lookup.loading, onClick = onEditMeaning) {
                Text(if (meaning == null) "Add definition" else "Edit definition", color = PinesPalette.Ink)
            }
            PronunciationControls(pronunciation, lookup.word, contentColor = PinesPalette.Accent, beforeSpeak = beforeSpeak)
            TextButton(onClick = onDismiss) { Text("Close", color = PinesPalette.Ink) }
        }
        lookup.studyAction?.let { action ->
            TextButton(onClick = onStudyAction) {
                Text(if (action == StudyAction.Resume) "Resume playback" else "Close", color = PinesPalette.Ink)
            }
        }
    }
}
