package com.example.campernavigator.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations ORDER BY timestamp DESC")
    fun getAllDestinations(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE isFavorite = 0 ORDER BY timestamp DESC LIMIT 20")
    fun getRecentDestinations(): Flow<List<DestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(destination: DestinationEntity): Long

    @Update
    suspend fun update(destination: DestinationEntity): Int

    @Delete
    suspend fun delete(destination: DestinationEntity): Int

    @Query("DELETE FROM destinations WHERE isFavorite = 0 AND name = :name")
    suspend fun deleteRecentByName(name: String): Int

    @Query("SELECT * FROM destinations WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): DestinationEntity?
}
