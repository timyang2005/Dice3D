package com.dice3d.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: RollResult): Long

    @Delete
    suspend fun delete(result: RollResult)

    @Query("DELETE FROM roll_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM roll_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<RollResult>

    @Query("SELECT * FROM roll_history WHERE diceType = :diceType ORDER BY timestamp DESC")
    suspend fun getByType(diceType: String): List<RollResult>

    @Query("SELECT * FROM roll_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<RollResult>
}
