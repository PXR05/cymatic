package com.pxr.cymatic.data.media

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AudioEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class CymaticDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var instance: CymaticDatabase? = null

        fun getInstance(context: Context): CymaticDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CymaticDatabase::class.java,
                    "audio_store.db"
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
