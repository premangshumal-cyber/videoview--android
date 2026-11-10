package com.kinepulse.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.kinepulse.player.data.db.AppDatabase
import com.kinepulse.player.data.media.MediaRepository
import com.kinepulse.player.data.prefs.AppPrefs
import com.kinepulse.player.util.ThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KinePulseApp : Application(), ImageLoaderFactory {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs by lazy { AppPrefs(this) }
    val database by lazy { AppDatabase.get(this) }
    val repository by lazy { MediaRepository(this, database, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        if (!prefs.persistentIncognito) {
            prefs.incognitoMode = false
        }
        ThemeHelper.applyNightMode(prefs.appTheme)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
}
