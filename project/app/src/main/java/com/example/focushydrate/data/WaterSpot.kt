package com.example.focushydrate.data

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
