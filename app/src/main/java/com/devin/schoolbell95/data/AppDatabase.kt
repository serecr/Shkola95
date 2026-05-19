package com.devin.schoolbell95.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Bell::class, Subject::class, LearnedQA::class, MemoryFact::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bellDao(): BellDao
    abstract fun subjectDao(): SubjectDao
    abstract fun learnedQADao(): LearnedQADao
    abstract fun memoryFactDao(): MemoryFactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v4 → v5: add learned_qa table for the offline self-learning assistant. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS learned_qa (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "question TEXT NOT NULL, " +
                        "answer TEXT NOT NULL, " +
                        "ts INTEGER NOT NULL" +
                        ")",
                )
            }
        }

        /** v5 → v6: long-term memory facts (name, class, favorites …). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS memory_fact (" +
                        "`key` TEXT NOT NULL PRIMARY KEY, " +
                        "value TEXT NOT NULL, " +
                        "ts INTEGER NOT NULL" +
                        ")",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shkola95.db",
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
