package com.kinepulse.player.ui.more

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LicensesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            text = """
                KinePulse Open-Source Notices
                =============================
                Clean-room Android Media Player built on android.widget.VideoView.
                
                Third-Party Dependencies:
                1. AndroidX Core, AppCompat, ConstraintLayout, RecyclerView, Room, Lifecycle, Preference, Media
                   License: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
                
                2. Material Components for Android
                   License: Apache License 2.0
                
                3. Kotlin Standard Library & Kotlinx Coroutines
                   License: Apache License 2.0
                
                4. Coil Image Loader (io.coil-kt:coil, io.coil-kt:coil-video)
                   License: Apache License 2.0
            """.trimIndent()
        }
        setContentView(ScrollView(this).apply { addView(tv) })
    }
}
