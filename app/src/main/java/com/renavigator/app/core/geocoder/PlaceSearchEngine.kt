package com.renavigator.app.core.geocoder

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.core.validator.LocationValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PlaceSearchResult(
    val coordinate: GeoCoordinate,
    val displayName: String
)

/**
 * High-speed place name / venue geocoder engine with proximity bias, unicode transliteration, and smart query decomposition.
 */
class PlaceSearchEngine(
    private val context: Context? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Resolves a free-text location query or extracted place address
     * into exact geographic coordinates, biased towards the driver's current position.
     */
    suspend fun search(
        query: String,
        driverLocation: GeoCoordinate? = null
    ): PlaceSearchResult? = withContext(Dispatchers.IO) {
        val cleanQuery = query.replace('\uFFFD', ' ').replace(Regex("""\s+"""), " ").trim()
        if (cleanQuery.isBlank() || cleanQuery.length < 2) return@withContext null

        // Generate smart query candidates
        val candidates = buildSearchCandidates(cleanQuery)

        for (candidate in candidates) {
            val result = executeSingleSearch(candidate, driverLocation)
            if (result != null) {
                return@withContext result
            }
        }

        return@withContext null
    }

    private suspend fun executeSingleSearch(
        query: String,
        driverLocation: GeoCoordinate?
    ): PlaceSearchResult? = withContext(Dispatchers.IO) {
        val cleanQuery = query.replace('\uFFFD', ' ').replace(Regex("""\s+"""), " ").trim()
        if (cleanQuery.isBlank()) return@withContext null

        // 1. Try Android Native Geocoder (Google Play Services geocoder)
        if (context != null && Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableGeocoder(geocoder, cleanQuery, driverLocation)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(cleanQuery, 5)
                }

                if (!addresses.isNullOrEmpty()) {
                    val bestAddress = if (driverLocation != null) {
                        addresses.minByOrNull { addr ->
                            LocationValidator.calculateDistanceKm(
                                driverLocation,
                                GeoCoordinate(addr.latitude, addr.longitude)
                            )
                        } ?: addresses.first()
                    } else {
                        addresses.first()
                    }

                    val coord = GeoCoordinate(bestAddress.latitude, bestAddress.longitude)
                    val label = bestAddress.featureName
                        ?: bestAddress.locality
                        ?: bestAddress.getAddressLine(0)
                        ?: cleanQuery

                    return@withContext PlaceSearchResult(coord, label)
                }
            } catch (e: Exception) {
                safeLog("Native geocoder lookup failed for '$cleanQuery': ${e.message}")
            }
        }

        // 2. Fallback to Photon OpenStreetMap API with driver proximity bias
        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
            val urlBuilder = StringBuilder("https://photon.komoot.io/api/?q=$encodedQuery&limit=3")
            if (driverLocation != null) {
                urlBuilder.append("&lat=${driverLocation.latitude}&lon=${driverLocation.longitude}")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "reNavigator-Android-Companion/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    val features = json.optJSONArray("features")
                    if (features != null && features.length() > 0) {
                        val firstFeature = features.getJSONObject(0)
                        val geometry = firstFeature.optJSONObject("geometry")
                        val coordinates = geometry?.optJSONArray("coordinates")
                        val properties = firstFeature.optJSONObject("properties")

                        if (coordinates != null && coordinates.length() >= 2) {
                            val lng = coordinates.getDouble(0)
                            val lat = coordinates.getDouble(1)
                            val name = properties?.optString("name") ?: cleanQuery
                            val city = properties?.optString("city") ?: ""
                            val street = properties?.optString("street") ?: ""
                            val houseNum = properties?.optString("housenumber") ?: ""

                            val displayLabel = when {
                                name.isNotBlank() && city.isNotBlank() && !name.contains(city) -> "$name, $city"
                                street.isNotBlank() && houseNum.isNotBlank() -> "$street $houseNum, $city"
                                else -> name
                            }

                            val coord = GeoCoordinate(lat, lng)
                            if (coord.isValid()) {
                                return@withContext PlaceSearchResult(coord, displayLabel)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            safeLog("Photon fallback geocoding failed for '$cleanQuery': ${e.message}")
        }

        return@withContext null
    }

    /**
     * Splits long composite addresses (e.g. from Google Maps URLs) into smart search candidates.
     */
    private fun buildSearchCandidates(raw: String): List<String> {
        val cleanRaw = raw.replace('+', ' ').replace('\uFFFD', ' ').replace(Regex("""\s+"""), " ").trim()
        val parts = cleanRaw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val rawCandidates = mutableListOf<String>()

        if (parts.size <= 1) {
            val words = cleanRaw.split(' ').filter { it.isNotBlank() }
            if (words.size > 1) {
                rawCandidates.add(cleanRaw)
                rawCandidates.add(words.take(2).joinToString(" "))
                rawCandidates.add(words[0])
            } else {
                rawCandidates.add(cleanRaw)
            }
        } else {
            rawCandidates.add(cleanRaw) // Full query

            val city = parts.firstOrNull { part ->
                val p = transliterate(part).lowercase(Locale.ROOT)
                p.contains("riga") || p.contains("sigulda") || p.contains("jurmala") ||
                p.contains("liepaja") || p.contains("ventspils") || p.contains("jelgava") ||
                p.contains("valmiera") || p.contains("daugavpils") || p.contains("ogre") ||
                p.contains("cesis") || p.contains("tukums") || p.contains("bauska") ||
                p.contains("adazi") || p.contains("carnikava")
            } ?: if (parts.size >= 2) parts[parts.size - 2] else ""

            val cleanCity = city.replace(Regex("""LV-\d+"""), "").trim()

            if (cleanCity.isNotBlank()) {
                rawCandidates.add("${parts[0]}, $cleanCity")

                val brand = parts[0].split(' ').firstOrNull() ?: ""
                if (brand.length > 2) {
                    rawCandidates.add("$brand, $cleanCity")
                    rawCandidates.add(brand)
                }

                if (parts.size > 2) {
                    rawCandidates.add("${parts[1]}, $cleanCity")
                }
            }

            rawCandidates.add(parts[0])
        }

        // Add transliterated ASCII forms of all candidates
        val finalCandidates = mutableListOf<String>()
        for (c in rawCandidates) {
            finalCandidates.add(c)
            val ascii = transliterate(c)
            if (ascii.isNotBlank() && ascii != c) {
                finalCandidates.add(ascii)
            }
        }

        return finalCandidates.distinct()
    }

    private fun transliterate(text: String): String {
        val latvianMap = mapOf(
            '\u0101' to 'a', '\u0100' to 'A', // ā, Ā
            '\u010D' to 'c', '\u010C' to 'C', // č, Č
            '\u0113' to 'e', '\u0112' to 'E', // ē, Ē
            '\u0123' to 'g', '\u0122' to 'G', // ģ, Ģ
            '\u012B' to 'i', '\u012A' to 'I', // ī, Ī
            '\u0137' to 'k', '\u0136' to 'K', // ķ, Ķ
            '\u013C' to 'l', '\u013B' to 'L', // ļ, Ļ
            '\u0146' to 'n', '\u0145' to 'N', // ņ, Ņ
            '\u0161' to 's', '\u0160' to 'S', // š, Š
            '\u016B' to 'u', '\u016A' to 'U', // ū, Ū
            '\u017E' to 'z', '\u017D' to 'Z'  // ž, Ž
        )
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(latvianMap[ch] ?: ch)
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .replace('\uFFFD', ' ')
            .replace(Regex("""[^\x20-\x7E]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun suspendCancellableGeocoder(
        geocoder: Geocoder,
        query: String,
        driverLocation: GeoCoordinate?
    ): List<android.location.Address>? = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        try {
            geocoder.getFromLocationName(query, 5)
        } catch (e: Exception) {
            null
        }
    }

    private fun safeLog(msg: String) {
        System.err.println("[$TAG] $msg")
    }

    companion object {
        private const val TAG = "PlaceSearchEngine"
    }
}
