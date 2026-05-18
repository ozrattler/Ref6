package com.refsix.wear.data

data class GpsPoint(
    val timestamp: Long,
    val matchMinute: Int,
    val half: Int,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val speedMs: Float
)
