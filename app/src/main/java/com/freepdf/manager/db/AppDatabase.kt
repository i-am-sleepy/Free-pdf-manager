package com.freepdf.manager.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database. All app data stays on-device (no cloud, no telemetry).
 */
@Database(entities = [FileEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fileDao(): FileDao

    companion object {
        private const val NAME = "free_pdf_manager.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                ).build().also { INSTANCE = it }
            }
    }
}
