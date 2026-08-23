package com.renavigator.app.core.model

/**
 * Represents a parsed geographic coordinate.
 */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                !(latitude == 0.0 && longitude == 0.0) // Exclude Null Island
    }

    fun toFormattedString(): String = "%.6f, %.6f".format(latitude, longitude)
}

/**
 * Result of parsing a message text, URL, or place query.
 */
sealed class ParseResult {
    data class Success(
        val coordinate: GeoCoordinate? = null,
        val label: String? = null,
        val searchQuery: String? = null,
        val sourceUrl: String? = null,
        val originalText: String = ""
    ) : ParseResult()

    data class Error(
        val message: String,
        val rawInput: String = ""
    ) : ParseResult()
}

/**
 * Result of validating a destination against distance and sanity rules.
 */
sealed class ValidationResult {
    data class Valid(
        val coordinate: GeoCoordinate?,
        val searchQuery: String?,
        val distanceKm: Double?,
        val label: String? = null
    ) : ValidationResult()

    data class ExceedsMaxDistance(
        val coordinate: GeoCoordinate,
        val distanceKm: Double,
        val maxAllowedKm: Double
    ) : ValidationResult()

    data class InvalidCoordinates(
        val message: String
    ) : ValidationResult()
}

/**
 * Supported navigation application targets.
 */
enum class NavigationApp(val displayName: String, val packageName: String) {
    WAZE("Waze", "com.waze"),
    GOOGLE_MAPS("Google Maps", "com.google.android.apps.maps"),
    PROMPT_BOTH("Ask / Show Both", "")
}

/**
 * Persistent item in the trip/history log.
 */
data class DestinationHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String,
    val sourcePackage: String,
    val coordinate: GeoCoordinate?,
    val searchQuery: String? = null,
    val label: String?,
    val distanceKm: Double?,
    val originalMessage: String,
    val status: String = "RECEIVED" // RECEIVED, NAVIGATED, DISMISSED, OUT_OF_RANGE
)
