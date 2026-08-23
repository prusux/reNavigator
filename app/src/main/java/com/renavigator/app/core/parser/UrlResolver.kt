package com.renavigator.app.core.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Resolves shortened and redirect URLs asynchronously to uncover true map coordinates or place names.
 */
class UrlResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    /**
     * Resolves a URL to its final destination and extracts explicit coordinates or place name.
     */
    suspend fun resolveUrl(url: String): ResolvedUrlResult = withContext(Dispatchers.IO) {
        try {
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            val request = Request.Builder()
                .url(formattedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val responseBody = response.body?.string() ?: ""

                // Extract place name label if in URL (e.g. /place/Kalngale,+Carnikava/...)
                val placeName = extractPlaceNameFromUrl(finalUrl)

                // 1. Check for coordinates in final redirected URL
                val coordsFromUrl = LocationParser.extractCoordinatesFromUrl(finalUrl)
                if (coordsFromUrl != null) {
                    return@withContext ResolvedUrlResult(finalUrl, coordsFromUrl, label = placeName)
                }

                // 2. Check HTML for explicit pin coordinates (!3dlat!4dlng or geo meta tags)
                val coordsFromBody = extractExplicitCoordinatesFromHtml(responseBody)
                return@withContext ResolvedUrlResult(finalUrl, coordsFromBody, label = placeName)
            }
        } catch (e: Exception) {
            ResolvedUrlResult(url, null, error = e.localizedMessage)
        }
    }

    private fun extractPlaceNameFromUrl(url: String): String? {
        val placePattern = Pattern.compile("""/place/([^/@?]+)""").matcher(url)
        if (placePattern.find()) {
            val rawName = placePattern.group(1) ?: return null
            val decoded = try {
                if (rawName.contains("%")) {
                    URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
                } else {
                    rawName
                }
            } catch (e: Exception) {
                rawName
            }
            return decoded
                .replace('+', ' ')
                .replace('\uFFFD', ' ')
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
        return null
    }

    private fun extractExplicitCoordinatesFromHtml(html: String): Pair<Double, Double>? {
        // ONLY match explicit pinned coordinates, never generic staticmap center
        val explicitPatterns = listOf(
            // 1. !3dlat!4dlng (Google Maps pinned entity coordinates)
            Pattern.compile("""!3d([+-]?\d+\.\d+)!4d([+-]?\d+\.\d+)"""),
            // 2. JSON latitude / longitude
            Pattern.compile("""["']latitude["']:\s*([+-]?\d+\.\d+),\s*["']longitude["']:\s*([+-]?\d+\.\d+)""")
        )

        for (pattern in explicitPatterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val lat = matcher.group(1)?.toDoubleOrNull()
                val lng = matcher.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return Pair(lat, lng)
                }
            }
        }
        return null
    }
}

data class ResolvedUrlResult(
    val finalUrl: String,
    val coordinates: Pair<Double, Double>?,
    val label: String? = null,
    val error: String? = null
)
