package com.kylecorry.trail_sense.tools.weather.infrastructure.persistence

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PressureReadingDao {
    @Query("SELECT * FROM pressures")
    fun getAll(): LiveData<List<PressureReadingEntity>>

    @Query("SELECT * FROM pressures")
    suspend fun getAllSync(): List<PressureReadingEntity>

    @Query("SELECT * FROM pressures WHERE _id = :id LIMIT 1")
    suspend fun get(id: Long): PressureReadingEntity?

    @Query("SELECT * FROM pressures ORDER BY _id DESC LIMIT 1")
    suspend fun getLast(): PressureReadingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pressure: PressureReadingEntity): Long

    @Delete
    suspend fun delete(pressure: PressureReadingEntity)

    @Query("DELETE FROM pressures WHERE time < :minEpochMillis")
    suspend fun deleteOlderThan(minEpochMillis: Long)

    @Update
    suspend fun update(pressure: PressureReadingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkInsert(pressures: List<PressureReadingEntity>)

    @Update
    suspend fun bulkUpdate(pressures: List<PressureReadingEntity>)
}