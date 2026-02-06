package com.example.lakki_phone.navigation

import com.example.lakki_phone.bluetooth.ExternalNavigationProtocol
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.maplibre.android.geometry.LatLng

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun computeDestinationHeader(
    currentLocation: LatLng,
    destination: LatLng,
): ExternalNavigationProtocol.DestinationHeader {
    val distanceMeters = haversineDistanceMeters(currentLocation, destination).roundToInt()
    val bearingDegrees = bearingDegrees(currentLocation, destination).roundToInt()
    return ExternalNavigationProtocol.DestinationHeader(
        direction = bearingDegrees,
        distanceMeters = distanceMeters,
    )
}

private fun haversineDistanceMeters(start: LatLng, end: LatLng): Double {
    val startLat = Math.toRadians(start.latitude)
    val endLat = Math.toRadians(end.latitude)
    val deltaLat = Math.toRadians(end.latitude - start.latitude)
    val deltaLon = Math.toRadians(end.longitude - start.longitude)

    val a = sin(deltaLat / 2).pow2() +
        cos(startLat) * cos(endLat) * sin(deltaLon / 2).pow2()
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

private fun bearingDegrees(start: LatLng, end: LatLng): Double {
    val startLat = Math.toRadians(start.latitude)
    val endLat = Math.toRadians(end.latitude)
    val deltaLon = Math.toRadians(end.longitude - start.longitude)

    val y = sin(deltaLon) * cos(endLat)
    val x = cos(startLat) * sin(endLat) -
        sin(startLat) * cos(endLat) * cos(deltaLon)
    val bearing = Math.toDegrees(atan2(y, x))
    return (bearing + 360.0) % 360.0
}

private fun Double.pow2(): Double = this * this
