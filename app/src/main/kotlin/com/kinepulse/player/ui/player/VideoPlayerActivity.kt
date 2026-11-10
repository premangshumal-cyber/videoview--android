package com.kinepulse.player.ui.player

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.R
import com.kinepulse.player.data.db.Bookmark
import com.kinepulse.player.data.db.ExternalSubtitle
import com.kinepulse.player.data.db.HistoryEntry
import com.kinepulse.player.data.db.PlaybackState
import com.kinepulse.player.databinding.ActivityVideoPlayerBinding
import com.kinepulse.player.player.AudioEffectController
import com.kinepulse.player.player.PlayQueueManager
import com.kinepulse.player.player.QueueItem
import com.kinepulse.player.player.RepeatMode
import com.kinepulse.player.player.subtitle.SubtitleParsers
import com.kinepulse.player.player.subtitle.SubtitleStyleConfig
import com.kinepulse.player.service.AudioPlaybackService
import com.kinepulse.player.ui.video.MediaInfoActivity
import com.kinepulse.player.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoPlayerActivity : AppCompatActivity(), PlayerGestureHelper.Listener {

    private lateinit var binding: ActivityVideoPlayerBinding
    private val app by lazy { application as KinePulseApp }
    private val prefs by lazy { app.prefs }
    private val dao by lazy { app.database.playbackDao() }
    private val queueManager = PlayQueueManager()
    private val audioEffectController = AudioEffectController()
    private lateinit var gestureHelper: PlayerGestureHelper
    private lateinit var audioManager: AudioManager

    private var mediaPlayerRef: WeakReference<MediaPlayer>? = null
    private var pollJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val hudHandler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable { setHudVisible(false) }

    private var isPlayerLocked = false
    private var showRemainingTime = false
    private var currentPlaybackSpeed = 1.0f
    private var subtitleDelayMs = 0L
    private var abPointAMs: Long? = null
    private var abPointBMs: Long? = null
    private var applyResumeToAllQueue: Boolean? = null
    private var lastSavedTimestampMs = 0L

    private val subtitlePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            loadExternalSubtitleUri(uri, saveToDb = true)
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausePlayback()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        gestureHelper = PlayerGestureHelper(this, prefs, this)

        configureFullscreenWindow()
        setupVideoViewListeners()
        setupHudControls()

        if (prefs.saveBrightness && prefs.savedBrightnessValue in 0.01f..1.0f) {
            window.attributes = window.attributes.apply { screenBrightness = prefs.savedBrightnessValue }
        }

        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun configureFullscreenWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.action
        val uris = intent?.getStringArrayListExtra(EXTRA_QUEUE_URIS)
        val titles = intent?.getStringArrayListExtra(EXTRA_QUEUE_TITLES)
        val startIdx = intent?.getIntExtra(EXTRA_START_INDEX, 0) ?: 0

        if (!uris.isNullOrEmpty()) {
            val items = uris.mapIndexed { idx, u ->
                QueueItem(Uri.parse(u), titles?.getOrNull(idx) ?: Uri.parse(u).lastPathSegment ?: "Video")
            }
            queueManager.setQueue(items, startIdx)
            prefs.lastQueueUrisCsv = uris.joinToString("|")
            startCurrentQueueItem()
            return
        }

        @Suppress("DEPRECATION")
        val targetUri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Uri.parse(it.trim()) }
            }
            else -> intent?.data
        }

        if (targetUri != null) {
            val title = intent?.getStringExtra(EXTRA_TITLE) ?: targetUri.lastPathSegment ?: getString(R.string.untitled_video)
            queueManager.setQueue(listOf(QueueItem(targetUri, title)), 0)
            startCurrentQueueItem()
        } else {
            Toast.makeText(this, R.string.no_media_found, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startCurrentQueueItem() {
        val current = queueManager.currentItem() ?: return
        val scheme = current.uri.scheme?.lowercase(Locale.ROOT)
        if (scheme == "http" || scheme == "https" || scheme == "rtsp") {
            if (!checkMeteredNetworkAllowed()) return
        }

        binding.tvPlayerTitle.text = current.title
        binding.tvClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        abPointAMs = null
        abPointBMs = null
        binding.subtitleRenderer.setCues(emptyList())
        applySubtitlePrefsStyle()

        lifecycleScope.launch {
            val savedState = dao.getState(current.uri.toString())
            currentPlaybackSpeed = savedState?.speed ?: prefs.globalPlaybackSpeed
            subtitleDelayMs = savedState?.subtitleDelayMs ?: prefs.globalSubtitleDelayMs
            binding.videoView.aspectMode = AspectRatioMode.entries.getOrElse(savedState?.aspectMode ?: 0) { AspectRatioMode.BEST_FIT }

            if (!prefs.incognitoMode) {
                dao.insertHistory(HistoryEntry(uri = current.uri.toString(), title = current.title, type = "VIDEO"))
            }

            if (savedState?.subtitleUri != null) {
                loadExternalSubtitleUri(Uri.parse(savedState.subtitleUri), saveToDb = false)
            }

            binding.videoView.setVideoURI(current.uri)
        }
    }

    private fun checkMeteredNetworkAllowed(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (!cm.isActiveNetworkMetered) return true
        return when (prefs.meteredConnectionPolicy) {
            "allow" -> true
            "block" -> {
                Toast.makeText(this, R.string.metered_connection_blocked, Toast.LENGTH_LONG).show()
                false
            }
            else -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.metered_warning_title)
                    .setMessage(R.string.metered_warning_message)
                    .setPositiveButton(R.string.play_anyway) { _, _ ->
                        binding.videoView.setVideoURI(queueManager.currentItem()!!.uri)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                    .show()
                false
            }
        }
    }

    private fun setupVideoViewListeners() {
        binding.videoContainer.setOnTouchListener { v, event ->
            gestureHelper.setSurfaceSize(v.width, v.height)
            if (isPlayerLocked) {
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    binding.lockOverlay.visibility = View.VISIBLE
                }
                true
            } else {
                gestureHelper.onTouchEvent(event)
            }
        }

        binding.videoView.setOnPreparedListener { mp ->
            mediaPlayerRef = WeakReference(mp)
            binding.progressBuffering.visibility = View.GONE
            binding.videoView.setVideoDimensions(mp.videoWidth, mp.videoHeight)

            binding.videoView.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            binding.videoView.setAudioFocusRequest(AudioManager.AUDIOFOCUS_GAIN)

            audioEffectController.attachSession(binding.videoView.audioSessionId)
            val totalMs = binding.videoView.duration.coerceAtLeast(0)
            binding.seekBar.max = totalMs
            binding.tvTotalTime.text = TimeUtils.formatMs(totalMs.toLong())

            applySpeed(mp, currentPlaybackSpeed)

            val currentUriStr = queueManager.currentItem()?.uri?.toString() ?: ""
            lifecycleScope.launch {
                val st = dao.getState(currentUriStr)
                if (st != null && st.audioTrackIndex >= 0) {
                    try { mp.selectTrack(st.audioTrackIndex) } catch (_: Throwable) {}
                }
                handleResumePolicy(st)
            }
        }

        binding.videoView.setOnCompletionListener {
            saveCurrentPosition(completed = true)
            val next = queueManager.moveToNext(fromCompletion = true)
            if (next != null) {
                if (prefs.videoTransitionShow) {
                    Toast.makeText(this, getString(R.string.playing_next, next.title), Toast.LENGTH_SHORT).show()
                }
                startCurrentQueueItem()
            } else {
                finish()
            }
        }

        binding.videoView.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> binding.progressBuffering.visibility = View.VISIBLE
                MediaPlayer.MEDIA_INFO_BUFFERING_END,
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> binding.progressBuffering.visibility = View.GONE
            }
            false
        }

        binding.videoView.setOnErrorListener { _, what, extra ->
            binding.progressBuffering.visibility = View.GONE
            showUnsupportedFormatDialog(what, extra)
            true
        }
    }

    private fun handleResumePolicy(state: PlaybackState?) {
        val savedPos = state?.positionMs ?: 0L
        val duration = binding.videoView.duration
        if (savedPos <= 3_000L || (duration > 0 && savedPos >= duration - 3_000L)) {
            startPlayback()
            return
        }

        val policy = when {
            applyResumeToAllQueue == true -> "always"
            applyResumeToAllQueue == false -> "never"
            else -> prefs.videoConfirmResume
        }

        when (policy) {
            "always" -> {
                seekToPosition(savedPos)
                startPlayback()
            }
            "never" -> startPlayback()
            else -> {
                pausePlayback()
                val view = layoutInflater.inflate(R.layout.dialog_resume_prompt, null)
                val cbApplyAll = view.findViewById<android.widget.CheckBox>(R.id.cbApplyQueue)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.resume_playback_title)
                    .setMessage(getString(R.string.resume_from_position_msg, TimeUtils.formatMs(savedPos)))
                    .setView(view)
                    .setPositiveButton(R.string.resume_action) { _, _ ->
                        if (cbApplyAll.isChecked) applyResumeToAllQueue = true
                        seekToPosition(savedPos)
                        startPlayback()
                    }
                    .setNegativeButton(R.string.start_over) { _, _ ->
                        if (cbApplyAll.isChecked) applyResumeToAllQueue = false
                        seekToPosition(0L)
                        startPlayback()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun setupHudControls() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnNext.setOnClickListener {
            if (queueManager.moveToNext() != null) startCurrentQueueItem()
        }
        binding.btnPrev.setOnClickListener {
            if (binding.videoView.currentPosition > 5000) {
                seekToPosition(0L)
            } else if (queueManager.moveToPrevious() != null) {
                startCurrentQueueItem()
            }
        }

        val seekBtnsVisibility = if (prefs.enableSeekButtons) View.VISIBLE else View.GONE
        binding.btnRewind.visibility = seekBtnsVisibility
        binding.btnForward.visibility = seekBtnsVisibility
        binding.btnRewind.text = "-${prefs.jumpDelaySec}s"
        binding.btnForward.text = "+${prefs.jumpDelaySec}s"

        binding.btnRewind.setOnClickListener { seekByDelta(-prefs.jumpDelaySec * 1000L) }
        binding.btnForward.setOnClickListener { seekByDelta(prefs.jumpDelaySec * 1000L) }
        binding.btnRewind.setOnLongClickListener { seekByDelta(-prefs.longJumpDelaySec * 1000L); true }
        binding.btnForward.setOnLongClickListener { seekByDelta(prefs.longJumpDelaySec * 1000L); true }

        binding.btnAspectRatio.setOnClickListener { cycleAspectRatio() }
        binding.btnOrientation.setOnClickListener { showOrientationDialog() }
        binding.chipSpeed.setOnClickListener { showSpeedDialog() }
        binding.chipSubtitleDelay.setOnClickListener { showSubtitleDelayDialog() }
        binding.chipSleepTimer.setOnClickListener { showSleepTimerDialog() }
        binding.btnTracks.setOnClickListener { showTracksDialog() }
        binding.btnAdvancedOptions.setOnClickListener { showAdvancedOptionsSheet() }
        binding.btnPip.setOnClickListener { enterPipMode() }

        val showScreenshotBtn = prefs.screenshotMode == "button" || prefs.screenshotMode == "both"
        binding.btnScreenshot.visibility = if (showScreenshotBtn) View.VISIBLE else View.GONE
        binding.btnScreenshot.setOnClickListener { captureVideoScreenshot() }

        binding.btnLock.setOnClickListener {
            isPlayerLocked = true
            setHudVisible(false)
            binding.lockOverlay.visibility = View.VISIBLE
        }
        binding.btnUnlockSwipe.setOnClickListener {
            isPlayerLocked = false
            binding.lockOverlay.visibility = View.GONE
            setHudVisible(true)
        }

        binding.tvTotalTime.setOnClickListener {
            showRemainingTime = !showRemainingTime
            updateProgressTick()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvGestureIndicator.visibility = View.VISIBLE
                    binding.tvGestureIndicator.text = TimeUtils.formatMs(progress.toLong())
                    seekToPosition(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                hudHandler.removeCallbacks(hideHudRunnable)
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                binding.tvGestureIndicator.visibility = View.GONE
                scheduleHudHide()
            }
        })
    }

    private fun startPlayback() {
        binding.videoView.start()
        binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        startPositionPolling()
        scheduleHudHide()
    }

    private fun pausePlayback() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
        }
        binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        pollJob?.cancel()
        saveCurrentPosition(completed = false)
    }

    private fun togglePlayPause() {
        if (binding.videoView.isPlaying) pausePlayback() else startPlayback()
    }

    private fun startPositionPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive && binding.videoView.isPlaying) {
                updateProgressTick()
                delay(200L)
            }
        }
    }

    private fun updateProgressTick() {
        val pos = binding.videoView.currentPosition.toLong()
        val dur = binding.videoView.duration.toLong().coerceAtLeast(0L)

        val pointA = abPointAMs
        val pointB = abPointBMs
        if (pointA != null && pointB != null && pos >= pointB) {
            seekToPosition(pointA)
            return
        }

        binding.seekBar.progress = pos.toInt()
        binding.tvCurrentTime.text = TimeUtils.formatMs(pos)
        binding.tvTotalTime.text = if (showRemainingTime && dur > pos) {
            "-${TimeUtils.formatMs(dur - pos)}"
        } else {
            TimeUtils.formatMs(dur)
        }

        binding.subtitleRenderer.updatePlaybackPosition(pos, subtitleDelayMs)

        val now = System.currentTimeMillis()
        if (now - lastSavedTimestampMs >= 5_000L) {
            lastSavedTimestampMs = now
            saveCurrentPosition(completed = false)
        }
    }

    private fun seekByDelta(deltaMs: Long) {
        val target = (binding.videoView.currentPosition + deltaMs).coerceIn(0L, binding.videoView.duration.toLong())
        seekToPosition(target)
    }

    private fun seekToPosition(targetMs: Long) {
        val mp = mediaPlayerRef?.get()
        if (mp != null) {
            val mode = if (prefs.alwaysFastSeek) MediaPlayer.SEEK_CLOSEST_SYNC else MediaPlayer.SEEK_CLOSEST
            try {
                mp.seekTo(targetMs, mode)
            } catch (_: Throwable) {
                binding.videoView.seekTo(targetMs.toInt())
            }
        } else {
            binding.videoView.seekTo(targetMs.toInt())
        }
        binding.subtitleRenderer.updatePlaybackPosition(targetMs, subtitleDelayMs)
    }

    private fun applySpeed(mp: MediaPlayer?, speed: Float) {
        val player = mp ?: mediaPlayerRef?.get() ?: return
        val wasPlaying = binding.videoView.isPlaying
        try {
            player.playbackParams = player.playbackParams.setSpeed(speed).setPitch(1.0f)
            if (!wasPlaying) {
                binding.videoView.pause()
            }
            binding.chipSpeed.text = String.format(Locale.US, "%.2fx", speed)
        } catch (_: Throwable) {}
    }

    private fun cycleAspectRatio() {
        val nextOrd = (binding.videoView.aspectMode.ordinal + 1) % AspectRatioMode.entries.size
        val nextMode = AspectRatioMode.entries[nextOrd]
        binding.videoView.aspectMode = nextMode
        binding.videoView.userZoomScale = 1.0f
        showTransientBanner(nextMode.label)
    }

    private fun showTransientBanner(text: String) {
        binding.tvGestureIndicator.text = text
        binding.tvGestureIndicator.visibility = View.VISIBLE
        hudHandler.postDelayed({ binding.tvGestureIndicator.visibility = View.GONE }, 1100L)
    }

    private fun setHudVisible(visible: Boolean) {
        binding.hudContainer.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) scheduleHudHide() else hudHandler.removeCallbacks(hideHudRunnable)
    }

    private fun scheduleHudHide() {
        hudHandler.removeCallbacks(hideHudRunnable)
        val timeoutMs = prefs.hudTimeoutSec * 1000L
        if (timeoutMs > 0 && binding.videoView.isPlaying) {
            hudHandler.postDelayed(hideHudRunnable, timeoutMs)
        }
    }

    private fun loadExternalSubtitleUri(uri: Uri, saveToDb: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val charset = try { Charset.forName(prefs.subtitleTextEncoding) } catch (_: Exception) { Charsets.UTF_8 }
                val content = contentResolver.openInputStream(uri)?.bufferedReader(charset)?.use { it.readText() } ?: return@launch
                val cues = SubtitleParsers.parseAuto(content, uri.lastPathSegment ?: "")
                withContext(Dispatchers.Main) {
                    binding.subtitleRenderer.setCues(cues)
                    Toast.makeText(this@VideoPlayerActivity, getString(R.string.subtitles_loaded, cues.size), Toast.LENGTH_SHORT).show()
                }
                if (saveToDb && !prefs.incognitoMode) {
                    val mediaUri = queueManager.currentItem()?.uri?.toString() ?: return@launch
                    dao.insertExternalSubtitle(
                        ExternalSubtitle(subtitlePath = uri.toString(), mediaPath = mediaUri)
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoPlayerActivity, R.string.subtitle_load_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applySubtitlePrefsStyle() {
        binding.subtitleRenderer.applyStyle(
            SubtitleStyleConfig(
                textSizeSp = prefs.subtitlesSizeSp,
                isBold = prefs.subtitlesBold,
                textColorRgb = prefs.subtitlesColor,
                textOpacity = prefs.subtitlesColorOpacity,
                hasBackground = prefs.subtitlesBackground,
                hasShadow = prefs.subtitlesShadow,
                hasOutline = prefs.subtitlesOutline
            )
        )
    }

    private fun showTracksDialog() {
        val mp = mediaPlayerRef?.get()
        val tracks = try { mp?.trackInfo ?: emptyArray() } catch (_: Throwable) { emptyArray() }
        val audioEntries = mutableListOf<Pair<Int, String>>()
        val embeddedSubCount = tracks.count {
            it.trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT ||
                it.trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE
        }

        tracks.forEachIndexed { index, info ->
            if (info.trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                audioEntries.add(index to "Audio #${audioEntries.size + 1} (${info.language.ifEmpty { "und" }})")
            }
        }

        val items = mutableListOf<String>()
        audioEntries.forEach { items.add(it.second) }
        if (embeddedSubCount > 0) {
            items.add("Embedded Subtitles ($embeddedSubCount found — not displayable by VideoView)")
        }
        items.add(getString(R.string.select_subtitle_file))
        items.add(getString(R.string.disable_subtitles))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tracks_and_subtitles)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which < audioEntries.size -> {
                        val trackIdx = audioEntries[which].first
                        try {
                            mp?.selectTrack(trackIdx)
                            showTransientBanner(audioEntries[which].second)
                        } catch (_: Throwable) {
                            Toast.makeText(this, R.string.track_switch_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                    items[which] == getString(R.string.select_subtitle_file) -> {
                        subtitlePickerLauncher.launch(arrayOf("*/*"))
                    }
                    items[which] == getString(R.string.disable_subtitles) -> {
                        binding.subtitleRenderer.setCues(emptyList())
                    }
                }
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f)
        val labels = speeds.map { "${it}x" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.playback_speed)
            .setItems(labels) { _, which ->
                currentPlaybackSpeed = speeds[which]
                applySpeed(mediaPlayerRef?.get(), currentPlaybackSpeed)
                val scheme = queueManager.currentItem()?.uri?.scheme
                if (currentPlaybackSpeed > 1.0f && (scheme == "http" || scheme == "https")) {
                    Toast.makeText(this, R.string.stream_speed_warning, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showSubtitleDelayDialog() {
        val options = arrayOf("-250 ms", "-50 ms", "Reset (0 ms)", "+50 ms", "+250 ms", "Apply to all videos")
        MaterialAlertDialogBuilder(this)
            .setTitle("Subtitle Delay (${subtitleDelayMs} ms)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> subtitleDelayMs -= 250L
                    1 -> subtitleDelayMs -= 50L
                    2 -> subtitleDelayMs = 0L
                    3 -> subtitleDelayMs += 50L
                    4 -> subtitleDelayMs += 250L
                    5 -> prefs.globalSubtitleDelayMs = subtitleDelayMs
                }
                binding.chipSubtitleDelay.text = "Sub: ${subtitleDelayMs}ms"
            }
            .show()
    }

    private fun showOrientationDialog() {
        val labels = arrayOf(
            "Automatic (Sensor)",
            "Landscape",
            "Landscape Reverse",
            "Landscape (Sensor)",
            "Portrait",
            "Portrait Reverse"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.screen_orientation)
            .setItems(labels) { _, which ->
                requestedOrientation = when (which) {
                    0 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    2 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    4 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                }
            }
            .show()
    }

    private fun showSleepTimerDialog() {
        val presetsMin = intArrayOf(0, 15, 30, 45, 60, 90)
        val labels = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sleep_timer)
            .setItems(labels) { _, which ->
                sleepTimerJob?.cancel()
                val mins = presetsMin[which]
                if (mins > 0) {
                    sleepTimerJob = lifecycleScope.launch {
                        delay(mins * 60_000L)
                        pausePlayback()
                        finish()
                    }
                    showTransientBanner("Sleep timer: $mins min")
                } else {
                    showTransientBanner("Sleep timer off")
                }
            }
            .show()
    }

    private fun showAdvancedOptionsSheet() {
        val options = arrayOf(
            "Jump to time (hh:mm:ss)",
            "A-B Repeat (${formatAbStatus()})",
            "Repeat mode (${queueManager.repeatMode.name})",
            "Shuffle (${if (queueManager.isShuffled) "ON" else "OFF"})",
            "Add Bookmark at current time",
            "View Bookmarks",
            "Equalizer / Audio Boost (${audioEffectController.boostPercent}%)",
            "Play as Background Audio",
            "Picture-in-Picture",
            "Take Screenshot",
            "Media Information"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.advanced_options)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showJumpToTimeDialog()
                    1 -> cycleAbRepeat()
                    2 -> {
                        queueManager.repeatMode = RepeatMode.entries[(queueManager.repeatMode.ordinal + 1) % RepeatMode.entries.size]
                        showTransientBanner("Repeat: ${queueManager.repeatMode.name}")
                    }
                    3 -> {
                        queueManager.toggleShuffle()
                        showTransientBanner("Shuffle: ${if (queueManager.isShuffled) "ON" else "OFF"}")
                    }
                    4 -> addBookmarkAtCurrentPosition()
                    5 -> showBookmarksDialog()
                    6 -> showAudioBoostDialog()
                    7 -> switchToBackgroundAudio()
                    8 -> enterPipMode()
                    9 -> captureVideoScreenshot()
                    10 -> {
                        queueManager.currentItem()?.let { item ->
                            startActivity(Intent(this, MediaInfoActivity::class.java).apply {
                                data = item.uri
                                putExtra(EXTRA_TITLE, item.title)
                            })
                        }
                    }
                }
            }
            .show()
    }

    private fun formatAbStatus(): String = when {
        abPointAMs == null -> "Off"
        abPointBMs == null -> "A set"
        else -> "${TimeUtils.formatMs(abPointAMs!!)} - ${TimeUtils.formatMs(abPointBMs!!)}"
    }

    private fun cycleAbRepeat() {
        val pos = binding.videoView.currentPosition.toLong()
        when {
            abPointAMs == null -> {
                abPointAMs = pos
                if (prefs.alwaysFastSeek) {
                    Toast.makeText(this, R.string.ab_fast_seek_warning, Toast.LENGTH_SHORT).show()
                }
                showTransientBanner("A-B: Point A set (${TimeUtils.formatMs(pos)})")
            }
            abPointBMs == null && pos > abPointAMs!! -> {
                abPointBMs = pos
                showTransientBanner("A-B Loop Active")
            }
            else -> {
                abPointAMs = null
                abPointBMs = null
                showTransientBanner("A-B Loop Cleared")
            }
        }
    }

    private fun showJumpToTimeDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "00:12:30"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.jump_to_time)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ms = TimeUtils.parseHhMmSsToMs(input.text.toString())
                if (ms != null) seekToPosition(ms)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addBookmarkAtCurrentPosition() {
        val current = queueManager.currentItem() ?: return
        val pos = binding.videoView.currentPosition.toLong()
        lifecycleScope.launch {
            dao.addBookmark(Bookmark(mediaUri = current.uri.toString(), timeMs = pos, name = "Bookmark @ ${TimeUtils.formatMs(pos)}"))
            showTransientBanner("Bookmark added")
        }
    }

    private fun showBookmarksDialog() {
        val current = queueManager.currentItem() ?: return
        lifecycleScope.launch {
            dao.observeBookmarks(current.uri.toString()).collect { list ->
                if (list.isEmpty()) {
                    Toast.makeText(this@VideoPlayerActivity, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
                    return@collect
                }
                val labels = list.map { "${it.name} (${TimeUtils.formatMs(it.timeMs)})" }.toTypedArray()
                MaterialAlertDialogBuilder(this@VideoPlayerActivity)
                    .setTitle(R.string.bookmarks)
                    .setItems(labels) { _, idx -> seekToPosition(list[idx].timeMs) }
                    .show()
                return@collect
            }
        }
    }

    private fun showAudioBoostDialog() {
        val levels = intArrayOf(100, 125, 150, 175, 200)
        val labels = levels.map { "$it%" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.audio_boost_equalizer)
            .setItems(labels) { _, which ->
                audioEffectController.setBoostLevel(levels[which])
                showTransientBanner("Audio Volume: ${levels[which]}%")
            }
            .show()
    }

    private fun switchToBackgroundAudio() {
        val current = queueManager.currentItem() ?: return
        val pos = binding.videoView.currentPosition.toLong()
        val serviceIntent = Intent(this, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_START_AUDIO
            data = current.uri
            putExtra(AudioPlaybackService.EXTRA_TITLE, current.title)
            putExtra(AudioPlaybackService.EXTRA_START_MS, pos)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        finish()
    }

    private fun captureVideoScreenshot() {
        val width = binding.videoView.width
        val height = binding.videoView.height
        if (width <= 0 || height <= 0) return

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(
                binding.videoView,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        saveScreenshotToGallery(bitmap)
                    } else {
                        Toast.makeText(this, R.string.screenshot_drm_error, Toast.LENGTH_SHORT).show()
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.screenshot_drm_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScreenshotToGallery(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            val filename = "KinePulse_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KinePulse")
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoPlayerActivity, R.string.screenshot_saved, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUnsupportedFormatDialog(what: Int, extra: Int) {
        val current = queueManager.currentItem() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsupported_format_title)
            .setMessage(getString(R.string.unsupported_format_message, what, extra))
            .setPositiveButton(R.string.open_with_another_app) { _, _ ->
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(current.uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(viewIntent, getString(R.string.open_with_another_app)).apply {
                    putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this@VideoPlayerActivity, VideoPlayerActivity::class.java)))
                }
                startActivity(chooser)
                finish()
            }
            .setNeutralButton(R.string.media_information) { _, _ ->
                startActivity(Intent(this, MediaInfoActivity::class.java).apply {
                    data = current.uri
                    putExtra(EXTRA_TITLE, current.title)
                })
            }
            .setNegativeButton(R.string.skip_to_next) { _, _ ->
                if (queueManager.moveToNext() != null) startCurrentQueueItem() else finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun enterPipMode() {
        val w = binding.videoView.width.coerceAtLeast(16)
        val h = binding.videoView.height.coerceAtLeast(9)
        val ratio = Rational(w, h).let {
            if (it.toFloat() in 0.42f..2.38f) it else Rational(16, 9)
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .build()
        enterPictureInPictureMode(params)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        setHudVisible(!isInPictureInPictureMode)
    }

    override fun onSingleTap() {
        val nowVisible = binding.hudContainer.visibility != View.VISIBLE
        setHudVisible(nowVisible)
    }

    override fun onDoubleTapSeek(forward: Boolean) {
        val jump = prefs.jumpDelaySec * 1000L * (if (forward) 1 else -1)
        seekByDelta(jump)
        showTransientBanner(if (forward) "+${prefs.jumpDelaySec}s" else "-${prefs.jumpDelaySec}s")
    }

    override fun onDoubleTapCenterPlayPause() {
        togglePlayPause()
    }

    override fun onBrightnessSwipe(deltaRatio: Float) {
        val lp = window.attributes
        val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
        val updated = (current + deltaRatio).coerceIn(0.02f, 1.0f)
        lp.screenBrightness = updated
        window.attributes = lp
        if (prefs.saveBrightness) prefs.savedBrightnessValue = updated
        showTransientBanner("Brightness: ${(updated * 100).toInt()}%")
    }

    override fun onVolumeSwipe(deltaRatio: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (curVol == maxVol && deltaRatio > 0 && prefs.audioBoost) {
            val nextBoost = (audioEffectController.boostPercent + (deltaRatio * 100).toInt()).coerceIn(100, 200)
            audioEffectController.setBoostLevel(nextBoost)
            showTransientBanner("Volume Boost: $nextBoost%")
        } else {
            audioEffectController.setBoostLevel(100)
            val step = (deltaRatio * maxVol).toInt()
            val targetVol = (curVol + step).coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            val pct = (targetVol * 100) / maxVol.coerceAtLeast(1)
            showTransientBanner("Volume: $pct%")
        }
    }

    override fun onHorizontalSeekPreview(deltaMs: Long) {
        val previewPos = (binding.videoView.currentPosition + deltaMs).coerceIn(0L, binding.videoView.duration.toLong())
        val sign = if (deltaMs >= 0) "+" else "-"
        binding.tvGestureIndicator.visibility = View.VISIBLE
        binding.tvGestureIndicator.text = "$sign${TimeUtils.formatMs(kotlin.math.abs(deltaMs))} [${TimeUtils.formatMs(previewPos)}]"
    }

    override fun onHorizontalSeekCommit(deltaMs: Long) {
        binding.tvGestureIndicator.visibility = View.GONE
        seekByDelta(deltaMs)
    }

    override fun onPinchZoom(scaleFactor: Float) {
        binding.videoView.userZoomScale *= scaleFactor
        showTransientBanner("Zoom: ${(binding.videoView.userZoomScale * 100).toInt()}%")
    }

    override fun onFastplayStateChanged(active: Boolean, speed: Float) {
        if (active) {
            applySpeed(mediaPlayerRef?.get(), speed)
            binding.tvGestureIndicator.visibility = View.VISIBLE
            binding.tvGestureIndicator.text = "Fastplay ${speed}x"
        } else {
            applySpeed(mediaPlayerRef?.get(), currentPlaybackSpeed)
            binding.tvGestureIndicator.visibility = View.GONE
        }
    }

    override fun onThreeFingerScreenshot() {
        captureVideoScreenshot()
    }

    private fun saveCurrentPosition(completed: Boolean) {
        if (prefs.incognitoMode) return
        val current = queueManager.currentItem() ?: return
        val pos = if (completed) 0L else binding.videoView.currentPosition.toLong()
        val dur = binding.videoView.duration.toLong().coerceAtLeast(0L)
        val seen = completed || (dur > 0 && pos >= (dur * 0.92).toLong())
        val state = PlaybackState(
            uri = current.uri.toString(),
            positionMs = pos,
            durationMs = dur,
            speed = currentPlaybackSpeed,
            subtitleDelayMs = subtitleDelayMs,
            aspectMode = binding.videoView.aspectMode.ordinal,
            seen = seen
        )
        lifecycleScope.launch(Dispatchers.IO) {
            dao.upsertState(state)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (binding.videoView.isPlaying && prefs.videoActionSwitch == "pip") {
            enterPipMode()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            pausePlayback()
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        sleepTimerJob?.cancel()
        hudHandler.removeCallbacksAndMessages(null)
        audioEffectController.release()
        try { unregisterReceiver(noisyReceiver) } catch (_: Throwable) {}
        binding.videoView.stopPlayback()
        mediaPlayerRef?.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUEUE_URIS = "extra_queue_uris"
        const val EXTRA_QUEUE_TITLES = "extra_queue_titles"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_TITLE = "extra_title"
    }
}
