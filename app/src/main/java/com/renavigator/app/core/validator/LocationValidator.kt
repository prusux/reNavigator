package com.renavigator.app.core.validator

import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.core.model.ValidationResult
import kotlin.math.*

/**
 * Validates coordinates against sanity bounds and computes distance from driver.
 */
class LocationValidator {

    companion object {
        const val DEFAULT_MAX_DISTANCE_KM = 400.0
        private const val EARTH_RADIUS_KM = 6371.0

        /**
         * Computes Haversine distance in kilometers between two coordinates.
         */
        fun calculateDistanceKm(from: GeoCoordinate, to: GeoCoordinate): Double {
            val dLat = Math.toRadians(to.latitude - from.latitude)
            val dLon = Math.toRadians(to.longitude - from.longitude)

            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)

            val a = sin(dLat / 2).pow(2) +
                    sin(dLon / 2).pow(2) * cos(lat1) * cos(lat2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return EARTH_RADIUS_KM * c
        }
    }

    /**
     * Validates a destination against driver's current location and maximum allowed distance.
     */
    fun validate(
        destination: GeoCoordinate?,
        searchQuery: String? = null,
        currentLocation: GeoCoordinate?,
        maxDistanceKm: Double = DEFAULT_MAX_DISTANCE_KM,
        label: String? = null
    ): ValidationResult {
        if (destination == null) {
            return if (!searchQuery.isNullOrBlank()) {
                ValidationResult.Valid(
                    coordinate = null,
                    searchQuery = searchQuery,
                    distanceKm = null,
                    label = label ?: searchQuery
                )
            } else {
                ValidationResult.InvalidCoordinates("Destination coordinates and search query are missing.")
            }
        }

        if (!destination.isValid()) {
            return ValidationResult.InvalidCoordinates("Coordinates out of range or invalid (0,0).")
        }

        if (currentLocation == null) {
            // Location unknown (e.g. GPS disabled), assume valid coordinate
            return ValidationResult.Valid(
                coordinate = destination,
                searchQuery = searchQuery,
                distanceKm = null,
                label = label
            )
        }

        val distanceKm = calculateDistanceKm(currentLocation, destination)

        return if (distanceKm <= maxDistanceKm) {
            ValidationResult.Valid(
                coordinate = destination,
                searchQuery = searchQuery,
                distanceKm = distanceKm,
                label = label
            )
        } else {
            ValidationResult.ExceedsMaxDistance(
                coordinate = destination,
                distanceKm = distanceKm,
                maxAllowedKm = maxDistanceKm
            )
        }
    }
}
