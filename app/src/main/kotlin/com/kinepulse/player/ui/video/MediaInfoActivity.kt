package com.kinepulse.player.ui.video

import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kinepulse.player.databinding.ActivityMediaInfoBinding
import com.kinepulse.player.ui.player.VideoPlayerActivity
import com.kinepulse.player.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent.data ?: run { finish(); return }
        val title = intent.getStringExtra(VideoPlayerActivity.EXTRA_TITLE) ?: uri.lastPathSegment ?: "Media"
        binding.tvInfoTitle.text = title

        binding.btnPlayFromInfo.setOnClickListener {
            startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                putExtra(VideoPlayerActivity.EXTRA_TITLE, title)
            })
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val details = StringBuilder()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@MediaInfoActivity, uri)
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "Unknown"
                val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)

                details.appendLine("URI: $uri")
                details.appendLine("Container / MIME: $mime")
                details.appendLine("Duration: ${TimeUtils.formatMs(dur)}")
                details.appendLine("Resolution: ${w}x${h}")
                if (bitrate != null) details.appendLine("Bitrate: ${bitrate / 1000} kbps")
                if (frameRate != null) details.appendLine("Frame Rate: $frameRate fps")
                val frame = retriever.getFrameAtTime(dur * 100L)
                withContext(Dispatchers.Main) {
                    if (frame != null) binding.ivHeaderThumb.setImageBitmap(frame)
                }
            } catch (_: Throwable) {
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(this@MediaInfoActivity, uri, null)
                details.appendLine("\n--- Tracks (${extractor.trackCount}) ---")
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val trackMime = fmt.getString(MediaFormat.KEY_MIME) ?: "unknown"
                    val lang = fmt.getString(MediaFormat.KEY_LANGUAGE) ?: "und"
                    details.append("Track #$i: $trackMime [$lang]")
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        details.append(" • ${fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)} Hz")
                    }
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        details.append(" • ${fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)} ch")
                    }
                    details.appendLine()
                }
            } catch (_: Throwable) {
            } finally {
                try { extractor.release() } catch (_: Throwable) {}
            }

            withContext(Dispatchers.Main) {
                binding.tvTechnicalSpecs.text = details.toString()
            }
        }
    }
}
