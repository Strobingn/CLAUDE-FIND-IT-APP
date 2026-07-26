package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [TargetSignalEntity::class, Setting::class, AnalyzedDatasetEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetSignalDao(): TargetSignalDao
    abstract fun settingDao(): SettingDao
    abstract fun analyzedDatasetDao(): AnalyzedDatasetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_signals ADD COLUMN photoUris TEXT NOT NULL DEFAULT ''")
            }
        }
        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY NOT NULL,
                        value TEXT NOT NULL
                    )
                """)
            }
        }
        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_signals ADD COLUMN outcome TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("ALTER TABLE target_signals ADD COLUMN datasetKey TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS analyzed_datasets (
                        datasetKey TEXT PRIMARY KEY NOT NULL,
                        displayName TEXT NOT NULL,
                        analyzedAtMillis INTEGER NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        cellSizeMeters REAL NOT NULL,
                        siteName TEXT NOT NULL,
                        crs TEXT NOT NULL,
                        boundsJson TEXT,
                        targetsJson TEXT NOT NULL
                    )
                """)
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "find-it.db",
            )
                .addMigrations(migration1To2, migration2To3, migration3To4)
                .build()
                .also { instance = it }
        }
    }
}
