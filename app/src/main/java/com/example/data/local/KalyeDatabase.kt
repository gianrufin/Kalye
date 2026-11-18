package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        TrackPointEntity::class,
        TripStopEntity::class,
        TagCategoryEntity::class,
        OfflineMapRegionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KalyeDatabase : RoomDatabase() {
    abstract fun kalyeDao(): KalyeDao

    companion object {
        @Volatile
        private var INSTANCE: KalyeDatabase? = null

        fun getInstance(context: Context): KalyeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalyeDatabase::class.java,
                    "kalye_overland.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
