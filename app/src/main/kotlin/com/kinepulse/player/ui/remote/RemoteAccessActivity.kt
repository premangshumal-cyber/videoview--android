package com.kinepulse.player.ui.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.remote.RemoteAccessService
import com.kinepulse.player.remote.RemoteAccessState
import com.kinepulse.player.util.ThemeHelper

class RemoteAccessActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var pin: TextView
    private lateinit var address: TextView
    private lateinit var button: Button

    private val updater = object : Runnable {
        override fun run() {
            status.text = when {
                RemoteAccessState.error.isNotEmpty() -> "Error: ${RemoteAccessState.error}"
                RemoteAccessState.running -> "Local catalog: active"
                else -> "Local catalog: inactive"
            }
            pin.text = if (RemoteAccessState.pin.isNotBlank())
                "Pairing PIN: ${RemoteAccessState.pin}" else "Pairing PIN: —"
            address.text = RemoteAccessState.localAddress()
            button.text = if (RemoteAccessState.running) "Stop remote access" else "Enable remote access"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyActivityOverlay(this, (application as KinePulseApp).prefs.appTheme)
        super.onCreate(savedInstanceState)
        title = "Wi-Fi remote access"
        val density = resources.displayMetrics.density
        val margin = (20 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, margin, margin, margin)
        }
        root.addView(TextView(this).apply {
            text = "KinePulse remote catalog"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "EXPERIMENTAL — read-only videos and audio catalog. On a trusted Wi-Fi network only. " +
                "Connections use HTTP, not HTTPS. Do not enable on public Wi-Fi."
            textSize = 14f
            setPadding(0, margin / 2, 0, margin / 2)
        })
        status = TextView(this).apply { textSize = 17f }
        pin = TextView(this).apply { textSize = 21f }
        address = TextView(this).apply { textSize = 17f }
        root.addView(status)
        root.addView(pin)
        root.addView(address)
        button = Button(this).apply {
            setOnClickListener {
                if (RemoteAccessState.running) {
                    stopService(Intent(this@RemoteAccessActivity, RemoteAccessService::class.java))
                } else {
                    ContextCompat.startForegroundService(
                        this@RemoteAccessActivity,
                        Intent(this@RemoteAccessActivity, RemoteAccessService::class.java)
                    )
                }
            }
        }
        root.addView(button)
        root.addView(Button(this).apply {
            text = "Copy address"
            setOnClickListener {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("KinePulse catalog", RemoteAccessState.localAddress()))
            }
        })
        root.addView(TextView(this).apply {
            text = "Pair by entering the six-digit PIN in your computer's browser. " +
                "Sessions expire after 15 minutes. Ten unsuccessful attempts disable new pairing until the service is restarted. " +
                "For Android 15+, the system may stop this data-transfer service after its foreground-service time allowance."
            setPadding(0, margin / 2, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(updater)
    }

    override fun onPause() {
        handler.removeCallbacks(updater)
        super.onPause()
    }
}
