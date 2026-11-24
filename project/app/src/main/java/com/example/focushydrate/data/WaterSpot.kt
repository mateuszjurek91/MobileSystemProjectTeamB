package com.example.focushydrate.data

import kotlin.math.*

data class WaterSpot(val id: String, val name: String, val latitude: Double, val longitude: Double, val ownerId: String = "") {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank() && name.length <= 80)
        require(validCoordinates(latitude, longitude))
    }
}

fun validCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
            latitude in -85.0..85.0 && longitude in -180.0..180.0

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

fun hasNearbySpot(spots: List<WaterSpot>, latitude: Double, longitude: Double, radiusMeters: Double = 25.0): Boolean =
    spots.any { distanceMeters(it.latitude, it.longitude, latitude, longitude) < radiusMeters }