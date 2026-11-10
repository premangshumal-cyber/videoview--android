package com.kinepulse.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.kinepulse.player.R

/**
 * MediaPlayer-backed audio queue; playback continues between tracks in a
 * single foreground service. This is not LibVLC or Media3.
 */
class AudioPlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var queue: List<Pair<Uri, String>> = emptyList()
    private var queueIndex = 0
    private var resumeAfterDuck = false
    private var currentTitle = "Audio"

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterDuck = mediaPlayer?.isPlaying == true
                mediaPlayer?.pause()
                updateNotification()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer?.setVolume(0.2f, 0.2f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1f, 1f)
                if (resumeAfterDuck) {
                    mediaPlayer?.start()
                    resumeAfterDuck = false
                }
                updateNotification()
            }
        }
    }

    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
        mediaSession = MediaSessionCompat(this, "KinePulseAudioSession").apply { isActive = true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_AUDIO -> {
                val uri = intent.data ?: return START_NOT_STICKY
                val firstTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Audio"
                val uris = intent.getStringArrayListExtra(EXTRA_QUEUE_URIS)
                val titles = intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES)
                queue = if (!uris.isNullOrEmpty()) {
                    uris.mapIndexed { index, item ->
                        Uri.parse(item) to (titles?.getOrNull(index) ?: "Audio")
                    }
                } else {
                    listOf(uri to firstTitle)
                }
                queueIndex = 0
                currentTitle = queue.first().second
                startForeground(NOTIFICATION_ID, buildNotification())
                if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    playUri(queue.first().first, intent.getLongExtra(EXTRA_START_MS, 0L))
                } else {
                    stopSelf()
                }
            }
            ACTION_PLAY_PAUSE -> {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) mp.pause() else mp.start()
                    updateNotification()
                }
            }
            ACTION_NEXT -> advance()
            ACTION_STOP_AUDIO -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun playUri(uri: Uri, startMs: Long) {
        mediaPlayer?.release()
        mediaPlayer = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(applicationContext, uri)
                setOnPreparedListener { mp ->
                    if (startMs > 0) mp.seekTo(startMs.toInt())
                    mp.start()
                    updateNotification()
                }
                setOnCompletionListener { advance() }
                setOnErrorListener { _, _, _ ->
                    advance()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            advance()
            null
        }
    }

    private fun advance() {
        if (queueIndex + 1 >= queue.size) {
            stopSelf()
            return
        }
        queueIndex++
        val (uri, title) = queue[queueIndex]
        currentTitle = title
        updateNotification()
        playUri(uri, 0L)
    }

    private fun pending(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this, requestCode,
            Intent(this, AudioPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): Notification {
        val isPlaying = mediaPlayer?.isPlaying == true
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText("KinePulse audio • ${queueIndex + 1}/${queue.size.coerceAtLeast(1)}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                pending(ACTION_PLAY_PAUSE, 1)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", pending(ACTION_NEXT, 2))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), pending(ACTION_STOP_AUDIO, 3))
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "KinePulse Audio Playback", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "kinepulse_audio_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_AUDIO = "com.kinepulse.player.action.START_AUDIO"
        const val ACTION_STOP_AUDIO = "com.kinepulse.player.action.STOP_AUDIO"
        const val ACTION_PLAY_PAUSE = "com.kinepulse.player.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.kinepulse.player.action.NEXT"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_START_MS = "extra_start_ms"
        const val EXTRA_QUEUE_URIS = "queue_uris"
        const val EXTRA_QUEUE_TITLES = "queue_titles"
    }
}
