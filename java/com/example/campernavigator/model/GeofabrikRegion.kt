package com.example.campernavigator.model

data class GeofabrikRegion(
    val id: String,
    val name: String,
    val parentId: String?,
    val pbfUrl: String,
    val fileSize: Long = 0,
    val bbox: String? = null
)
