package com.kinepulse.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackState::class,
        HistoryEntry::class,
        Favorite::class,
        ExternalSubtitle::class,
        CustomDirectory::class,
        Playlist::class,
        PlaylistItem::class,
        VideoGroupEntity::class,
        Bookmark::class,
        SavedStream::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kinepulse.db"
                ).build().also { INSTANCE = it }
            }
    }
}
