package com.example.campernavigator.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficEventDao {
    @Query("SELECT * FROM traffic_events")
    fun getAllTrafficEvents(): Flow<List<TrafficEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<TrafficEventEntity>)

    @Query("DELETE FROM traffic_events")
    suspend fun deleteAll()

    @Transaction
    suspend fun refreshTrafficEvents(events: List<TrafficEventEntity>) {
        deleteAll()
        insertAll(events)
    }
}
