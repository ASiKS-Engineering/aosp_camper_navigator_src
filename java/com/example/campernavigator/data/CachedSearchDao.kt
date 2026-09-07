package com.example.campernavigator.data

import androidx.room.*

@Dao
interface CachedSearchDao {
    @Query("SELECT * FROM cached_searches WHERE `query` = :query LIMIT 1")
    suspend fun getCachedResult(query: String): CachedSearchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cachedSearch: CachedSearchEntity)

    @Query("DELETE FROM cached_searches WHERE timestamp < :threshold")
    suspend fun deleteOldCaches(threshold: Long)
}
