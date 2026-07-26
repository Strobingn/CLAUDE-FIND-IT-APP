package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TargetSignalEntity::class,
        Setting::class,
        AnalyzedDatasetEntity::class,
        SurveyLayerEntity::class,
        OfflineBasemapRegionEntity::class,
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetSignalDao(): TargetSignalDao
    abstract fun settingDao(): SettingDao
    abstract fun analyzedDatasetDao(): AnalyzedDatasetDao
    abstract fun surveyLayerDao(): SurveyLayerDao
    abstract fun offlineBasemapRegionDao(): OfflineBasemapRegionDao

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
        private val migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_signals ADD COLUMN terrainKey TEXT")
            }
        }
        private val migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS survey_layers (
                        id TEXT PRIMARY KEY NOT NULL,
                        terrainKey TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        format TEXT NOT NULL,
                        sourceXml TEXT NOT NULL,
                        importedAtMillis INTEGER NOT NULL
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_survey_layers_terrainKey " +
                        "ON survey_layers (terrainKey)",
                )
            }
        }
        private val migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS offline_basemap_regions (
                        id TEXT PRIMARY KEY NOT NULL,
                        terrainKey TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        minLat REAL NOT NULL,
                        maxLat REAL NOT NULL,
                        minLon REAL NOT NULL,
                        maxLon REAL NOT NULL,
                        zoom INTEGER NOT NULL,
                        tileCount INTEGER NOT NULL,
                        completedTiles INTEGER NOT NULL,
                        estimatedBytes INTEGER NOT NULL,
                        storedBytes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        lastError TEXT,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_offline_basemap_regions_terrainKey " +
                        "ON offline_basemap_regions (terrainKey)",
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "find-it.db",
            )
                .addMigrations(
                    migration1To2,
                    migration2To3,
                    migration3To4,
                    migration4To5,
                    migration5To6,
                    migration6To7,
                )
                .build()
                .also { instance = it }
        }
    }
}
