package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        TrackPointEntity::class,
        TripStopEntity::class,
        TagCategoryEntity::class,
        OfflineMapRegionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KalyeDatabase : RoomDatabase() {
    abstract fun kalyeDao(): KalyeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN vehicleId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trips ADD COLUMN vehicleName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trips ADD COLUMN movingDurationMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN autoPausedFlag INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN lastMovementTimeMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: KalyeDatabase? = null

        fun getInstance(context: Context): KalyeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalyeDatabase::class.java,
                    "kalye_overland.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
