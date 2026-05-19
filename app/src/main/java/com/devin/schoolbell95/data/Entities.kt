package com.devin.schoolbell95.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One bell row. Times are minutes from midnight in local Ufa time.
 * dayKind: 0 = Monday, 1 = Tuesday–Saturday (МАОУ ЦО №95 has two distinct schedules).
 */
@Entity(tableName = "bells")
data class Bell(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val shift: Int,
    val dayKind: Int,
    val lessonNumber: Int,
    val startMin: Int,
    val endMin: Int,
)

/**
 * Subject for a given ISO day-of-week and lesson number.
 * dow follows java.util.Calendar: MONDAY=2, TUESDAY=3, ... FRIDAY=6, SATURDAY=7, SUNDAY=1.
 */
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dow: Int,
    val lessonNumber: Int,
    val name: String,
)

/**
 * A question/answer pair the user has taught to the offline assistant.
 * Used for the "научи: <вопрос> => <ответ>" feature so the bot can be extended
 * without an internet connection.
 */
@Entity(tableName = "learned_qa")
data class LearnedQA(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val question: String,
    val answer: String,
    val ts: Long = System.currentTimeMillis(),
)

/**
 * Long-term memory: structured facts the bot remembers about the user.
 * `key` is canonical ("name", "age", "class", "favorite_subject", "like:football", ...),
 * `value` stores the actual answer ("Радмир", "14", "7В", "физика"). A NULL value just
 * marks the user has the trait (e.g. "like:football" → "yes").
 */
@Entity(tableName = "memory_fact")
data class MemoryFact(
    @PrimaryKey val key: String,
    val value: String,
    val ts: Long = System.currentTimeMillis(),
)

object DayKind {
    const val MONDAY = 0
    const val TUE_SAT = 1

    fun fromCalendarDow(calendarDow: Int): Int = when (calendarDow) {
        java.util.Calendar.MONDAY -> MONDAY
        else -> TUE_SAT
    }

    fun label(kind: Int): String = when (kind) {
        MONDAY -> "Понедельник"
        else -> "Вт–Сб"
    }
}
