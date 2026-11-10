package com.kinepulse.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.kinepulse.player.R

class AudioPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "KinePulseAudioSession")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_AUDIO -> {
                val uri = intent.data ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Audio"
                val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)
                startForeground(NOTIFICATION_ID, buildNotification(title))
                playUri(uri, startMs)
            }
            ACTION_STOP_AUDIO -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun playUri(uri: Uri, startMs: Long) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
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
            }
            prepareAsync()
        }
    }

    private fun buildNotification(title: String): Notification {
        val stopIntent = Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_STOP_AUDIO }
        val stopPending = android.app.PendingIntent.getService(
            this, 0, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.background_audio_active))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stopPending)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "KinePulse Audio Playback", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "kinepulse_audio_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_AUDIO = "com.kinepulse.player.action.START_AUDIO"
        const val ACTION_STOP_AUDIO = "com.kinepulse.player.action.STOP_AUDIO"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_START_MS = "extra_start_ms"
    }
}
