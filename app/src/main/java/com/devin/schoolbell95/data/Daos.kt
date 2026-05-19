package com.devin.schoolbell95.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BellDao {
    @Query("SELECT * FROM bells WHERE shift = :shift AND dayKind = :dayKind ORDER BY lessonNumber ASC")
    fun observe(shift: Int, dayKind: Int): Flow<List<Bell>>

    @Query("SELECT * FROM bells WHERE shift = :shift AND dayKind = :dayKind ORDER BY lessonNumber ASC")
    suspend fun list(shift: Int, dayKind: Int): List<Bell>

    @Query("SELECT COUNT(*) FROM bells")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Bell>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Bell): Long

    @Update
    suspend fun update(item: Bell)

    @Delete
    suspend fun delete(item: Bell)

    @Query("DELETE FROM bells WHERE shift = :shift AND dayKind = :dayKind")
    suspend fun deleteShiftDayKind(shift: Int, dayKind: Int)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE dow = :dow ORDER BY lessonNumber ASC")
    fun observe(dow: Int): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE dow = :dow ORDER BY lessonNumber ASC")
    suspend fun list(dow: Int): List<Subject>

    @Query("SELECT * FROM subjects ORDER BY dow ASC, lessonNumber ASC")
    suspend fun all(): List<Subject>

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Subject>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Subject): Long

    @Update
    suspend fun update(item: Subject)

    @Delete
    suspend fun delete(item: Subject)

    @Query("DELETE FROM subjects WHERE dow = :dow")
    suspend fun deleteDay(dow: Int)
}

@Dao
interface LearnedQADao {
    @Query("SELECT * FROM learned_qa ORDER BY ts DESC")
    suspend fun all(): List<LearnedQA>

    @Query("SELECT COUNT(*) FROM learned_qa")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LearnedQA): Long

    @Query("DELETE FROM learned_qa WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM learned_qa")
    suspend fun clear()
}

@Dao
interface MemoryFactDao {
    @Query("SELECT * FROM memory_fact ORDER BY ts DESC")
    suspend fun all(): List<MemoryFact>

    @Query("SELECT * FROM memory_fact WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): MemoryFact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(fact: MemoryFact)

    @Query("DELETE FROM memory_fact WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM memory_fact")
    suspend fun clear()
}
