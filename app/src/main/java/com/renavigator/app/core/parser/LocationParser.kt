package com.renavigator.app.core.parser

import com.renavigator.app.core.geocoder.PlaceSearchEngine
import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.core.model.ParseResult
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Universal location parser that extracts coordinates from unstructured text,
 * map URLs, European comma notation, DMS, cardinal formats, and place name search queries.
 */
class LocationParser(
    private val urlResolver: UrlResolver = UrlResolver(),
    private val placeSearchEngine: PlaceSearchEngine = PlaceSearchEngine()
) {

    companion object {
        private val URL_REGEX = Pattern.compile(
            """https?://[^\s<>"]+|geo:[^\s<>"]+""",
            Pattern.CASE_INSENSITIVE
        )

        // DMS format: 56°56'58.6"N 24°06'18.7"E or 56 56 58.6 N, 24 06 18.7 E
        private val DMS_REGEX = Pattern.compile(
            """(\d{1,3})[°\s](\d{1,2})['\s](\d{1,2}(?:\.\d+)?)["]?\s*([NSEWnsew])[,\s]+(\d{1,3})[°\s](\d{1,2})['\s](\d{1,2}(?:\.\d+)?)["]?\s*([NSEWnsew])"""
        )

        // Cardinal prefix: N56.9496, E24.1052 or N56,9496 E24,1052
        private val CARDINAL_PREFIX_REGEX = Pattern.compile(
            """([NSns])\s*(\d{1,2}[.,]\d+)[,\s]+([EWew])\s*(\d{1,3}[.,]\d+)"""
        )

        // Cardinal suffix: 56.9496N, 24.1052E or 56,9496N 24,1052E
        private val CARDINAL_SUFFIX_REGEX = Pattern.compile(
            """(\d{1,2}[.,]\d+)\s*([NSns])[,\s]+(\d{1,3}[.,]\d+)\s*([EWew])"""
        )

        // Comma decimal format: 56,9496, 24,1052 or 56,9496 24,1052
        private val COMMA_DECIMAL_REGEX = Pattern.compile(
            """([+-]?\d{1,2},\d{3,10})[,\s]+([+-]?\d{1,3},\d{3,10})"""
        )

        // Standard decimal format: 56.9496, 24.1052 or 56.9496 24.1052
        private val STANDARD_DECIMAL_REGEX = Pattern.compile(
            """([+-]?\d{1,2}\.\d{3,10})[,\s]+([+-]?\d{1,3}\.\d{3,10})"""
        )

        /**
         * Extracts coordinates directly from known map URL structures.
         */
        fun extractCoordinatesFromUrl(rawUrl: String): Pair<Double, Double>? {
            val decodedUrl = try {
                URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                rawUrl
            }

            // 1. Google Maps @lat,lng,zoom or !3dlat!4dlng
            val gmapsAt = Pattern.compile("""@([+-]?\d+\.\d+),([+-]?\d+\.\d+)""").matcher(decodedUrl)
            if (gmapsAt.find()) {
                val lat = gmapsAt.group(1)?.toDoubleOrNull()
                val lng = gmapsAt.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            val gmaps3d4d = Pattern.compile("""!3d([+-]?\d+\.\d+)!4d([+-]?\d+\.\d+)""").matcher(decodedUrl)
            if (gmaps3d4d.find()) {
                val lat = gmaps3d4d.group(1)?.toDoubleOrNull()
                val lng = gmaps3d4d.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            // 2. Query param ?q=lat,lng or ?ll=lat,lng or ?daddr=lat,lng
            val queryCoord = Pattern.compile("""[?&](?:q|ll|sll|daddr|destination)=([+-]?\d+\.\d+),([+-]?\d+\.\d+)""").matcher(decodedUrl)
            if (queryCoord.find()) {
                val lat = queryCoord.group(1)?.toDoubleOrNull()
                val lng = queryCoord.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            // 3. Waze to=ll.lat,lng
            val wazeTo = Pattern.compile("""to=ll\.([+-]?\d+\.\d+),([+-]?\d+\.\d+)""").matcher(decodedUrl)
            if (wazeTo.find()) {
                val lat = wazeTo.group(1)?.toDoubleOrNull()
                val lng = wazeTo.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            // 4. OpenStreetMap #map=zoom/lat/lng or ?mlat=lat&mlon=lng
            val osmHash = Pattern.compile("""#map=\d+/([+-]?\d+\.\d+)/([+-]?\d+\.\d+)""").matcher(decodedUrl)
            if (osmHash.find()) {
                val lat = osmHash.group(1)?.toDoubleOrNull()
                val lng = osmHash.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            val osmMlat = Pattern.compile("""mlat=([+-]?\d+\.\d+).*?mlon=([+-]?\d+\.\d+)""").matcher(decodedUrl)
            if (osmMlat.find()) {
                val lat = osmMlat.group(1)?.toDoubleOrNull()
                val lng = osmMlat.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            // 5. Geo URI: geo:lat,lng
            val geoUri = Pattern.compile("""geo:([+-]?\d+\.\d+),([+-]?\d+\.\d+)""").matcher(decodedUrl)
            if (geoUri.find()) {
                val lat = geoUri.group(1)?.toDoubleOrNull()
                val lng = geoUri.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }

            return null
        }
    }

    /**
     * Checks if a notification/message has a navigation trigger tag.
     */
    fun hasNavTrigger(message: String, triggerTag: String = "#nav"): Boolean {
        return message.contains(triggerTag, ignoreCase = true)
    }

    /**
     * Parses a raw text message (extracting URLs, coordinates, or place search queries).
     */
    suspend fun parse(
        rawText: String,
        triggerTag: String = "#nav",
        driverLocation: GeoCoordinate? = null
    ): ParseResult {
        // Strip out the trigger tag for cleaner extraction
        val cleanText = rawText.replace(Regex("""(?i)\Q$triggerTag\E"""), " ").trim()

        // 1. Check for URLs
        val urlMatcher = URL_REGEX.matcher(rawText)
        if (urlMatcher.find()) {
            val url = urlMatcher.group(0) ?: ""
            // First attempt direct URL parsing
            val directCoords = extractCoordinatesFromUrl(url)
            if (directCoords != null) {
                val coord = GeoCoordinate(directCoords.first, directCoords.second)
                if (coord.isValid()) {
                    return ParseResult.Success(
                        coordinate = coord,
                        sourceUrl = url,
                        originalText = rawText
                    )
                }
            }

            // If direct parsing didn't find coordinates (e.g. maps.app.goo.gl/xxx), resolve redirect
            val resolved = urlResolver.resolveUrl(url)
            if (resolved.coordinates != null) {
                val coord = GeoCoordinate(resolved.coordinates.first, resolved.coordinates.second)
                if (coord.isValid()) {
                    return ParseResult.Success(
                        coordinate = coord,
                        label = resolved.label,
                        sourceUrl = resolved.finalUrl,
                        originalText = rawText
                    )
                }
            } else if (!resolved.label.isNullOrBlank()) {
                // Resolved a Google Maps Place URL (e.g. PetCity Ķīšezers Rimi) - geocode the place name!
                val geocoded = placeSearchEngine.search(resolved.label, driverLocation)
                if (geocoded != null) {
                    return ParseResult.Success(
                        coordinate = geocoded.coordinate,
                        label = geocoded.displayName,
                        searchQuery = resolved.label,
                        sourceUrl = resolved.finalUrl,
                        originalText = rawText
                    )
                } else {
                    return ParseResult.Success(
                        coordinate = null,
                        label = resolved.label,
                        searchQuery = resolved.label,
                        sourceUrl = resolved.finalUrl,
                        originalText = rawText
                    )
                }
            }
        }

        // 2. Check DMS (Degrees, Minutes, Seconds)
        val dmsMatcher = DMS_REGEX.matcher(cleanText)
        if (dmsMatcher.find()) {
            val latD = dmsMatcher.group(1)!!.toDouble()
            val latM = dmsMatcher.group(2)!!.toDouble()
            val latS = dmsMatcher.group(3)!!.toDouble()
            val latDir = dmsMatcher.group(4)!!

            val lngD = dmsMatcher.group(5)!!.toDouble()
            val lngM = dmsMatcher.group(6)!!.toDouble()
            val lngS = dmsMatcher.group(7)!!.toDouble()
            val lngDir = dmsMatcher.group(8)!!

            var lat = latD + (latM / 60.0) + (latS / 3600.0)
            if (latDir.equals("S", ignoreCase = true)) lat = -lat

            var lng = lngD + (lngM / 60.0) + (lngS / 3600.0)
            if (lngDir.equals("W", ignoreCase = true)) lng = -lng

            val coord = GeoCoordinate(lat, lng)
            if (coord.isValid()) {
                return ParseResult.Success(coord, originalText = rawText)
            }
        }

        // 3. Cardinal Prefix (e.g. N56.9496 E24.1052)
        val cardPrefix = CARDINAL_PREFIX_REGEX.matcher(cleanText)
        if (cardPrefix.find()) {
            val latDir = cardPrefix.group(1)!!
            var lat = cardPrefix.group(2)!!.replace(',', '.').toDouble()
            val lngDir = cardPrefix.group(3)!!
            var lng = cardPrefix.group(4)!!.replace(',', '.').toDouble()

            if (latDir.equals("S", ignoreCase = true)) lat = -lat
            if (lngDir.equals("W", ignoreCase = true)) lng = -lng

            val coord = GeoCoordinate(lat, lng)
            if (coord.isValid()) {
                return ParseResult.Success(coord, originalText = rawText)
            }
        }

        // 4. Cardinal Suffix (e.g. 56.9496N 24.1052E)
        val cardSuffix = CARDINAL_SUFFIX_REGEX.matcher(cleanText)
        if (cardSuffix.find()) {
            var lat = cardSuffix.group(1)!!.replace(',', '.').toDouble()
            val latDir = cardSuffix.group(2)!!
            var lng = cardSuffix.group(3)!!.replace(',', '.').toDouble()
            val lngDir = cardSuffix.group(4)!!

            if (latDir.equals("S", ignoreCase = true)) lat = -lat
            if (lngDir.equals("W", ignoreCase = true)) lng = -lng

            val coord = GeoCoordinate(lat, lng)
            if (coord.isValid()) {
                return ParseResult.Success(coord, originalText = rawText)
            }
        }

        // 5. Comma Decimal Notation (e.g. 56,9496, 24,1052)
        val commaDecimal = COMMA_DECIMAL_REGEX.matcher(cleanText)
        if (commaDecimal.find()) {
            val lat = commaDecimal.group(1)!!.replace(',', '.').toDoubleOrNull()
            val lng = commaDecimal.group(2)!!.replace(',', '.').toDoubleOrNull()
            if (lat != null && lng != null) {
                val coord = GeoCoordinate(lat, lng)
                if (coord.isValid()) {
                    return ParseResult.Success(coord, originalText = rawText)
                }
            }
        }

        // 6. Standard Decimal Notation (e.g. 56.9496, 24.1052)
        val standardDecimal = STANDARD_DECIMAL_REGEX.matcher(cleanText)
        if (standardDecimal.find()) {
            val lat = standardDecimal.group(1)!!.toDoubleOrNull()
            val lng = standardDecimal.group(2)!!.toDoubleOrNull()
            if (lat != null && lng != null) {
                val coord = GeoCoordinate(lat, lng)
                if (coord.isValid()) {
                    return ParseResult.Success(coord, originalText = rawText)
                }
            }
        }

        // 7. Free-text place name / venue search (e.g. "#nav Sigulda" or "#nav lokāls karbonādes")
        val searchQuery = cleanText.trim()
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            val geocoded = placeSearchEngine.search(searchQuery, driverLocation)
            if (geocoded != null) {
                return ParseResult.Success(
                    coordinate = geocoded.coordinate,
                    label = geocoded.displayName,
                    searchQuery = searchQuery,
                    originalText = rawText
                )
            } else {
                // If geocoder couldn't resolve, pass query string directly to Waze/Maps search
                return ParseResult.Success(
                    coordinate = null,
                    label = searchQuery,
                    searchQuery = searchQuery,
                    originalText = rawText
                )
            }
        }

        return ParseResult.Error("No valid coordinates, map link, or place name found.", rawInput = rawText)
    }
}
