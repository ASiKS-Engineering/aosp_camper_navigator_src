package com.example.campernavigator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_searches")
data class CachedSearchEntity(
    @PrimaryKey val query: String,
    val resultsJson: String, // Storing as JSON for simplicity in this demo
    val timestamp: Long = System.currentTimeMillis()
)
