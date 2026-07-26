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
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetSignalDao(): TargetSignalDao
    abstract fun settingDao(): SettingDao
    abstract fun analyzedDatasetDao(): AnalyzedDatasetDao
    abstract fun surveyLayerDao(): SurveyLayerDao

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
                )
                .build()
                .also { instance = it }
        }
    }
}
