package com.renavigator.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.renavigator.app.core.geocoder.PlaceSearchEngine
import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.core.model.ParseResult
import com.renavigator.app.core.parser.LocationParser
import com.renavigator.app.data.PreferencesManager
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun SandboxScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val triggerTag by prefsManager.triggerTag.collectAsState()

    var testInput by remember { mutableStateOf("#nav https://maps.app.goo.gl/QRwhuRUJoLnuuKLY8?g_st=aw") }
    var isParsing by remember { mutableStateOf(false) }
    var parseResult by remember { mutableStateOf<ParseResult?>(null) }

    val parser = remember { LocationParser(placeSearchEngine = PlaceSearchEngine(context)) }

    val presetExamples = listOf(
        "Google Short Link" to "#nav https://maps.app.goo.gl/QRwhuRUJoLnuuKLY8?g_st=aw",
        "Place (Sigulda)" to "#nav Sigulda",
        "Venue Search" to "#nav lokāls karbonādes",
        "European Comma" to "#nav 56,9496, 24,1052",
        "Cardinal DMS" to "#nav 56°56'58.6\"N 24°06'18.7\"E",
        "Standard Decimals" to "#nav 56.9496, 24.1052",
        "Waze URL" to "#nav https://waze.com/ul?ll=56.9496,24.1052&navigate=yes",
        "Apple Maps URL" to "#nav https://maps.apple.com/?ll=56.9496,24.1052"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Location Parser Sandbox",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Paste sample WhatsApp messages, place names ('Sigulda', 'lokāls karbonādes'), or map URLs to verify parsing and test launch navigation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Text Input
        OutlinedTextField(
            value = testInput,
            onValueChange = { testInput = it },
            label = { Text("Sample Message / Place Name / Coordinates / URL") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp)
        )

        // Parse Button
        Button(
            onClick = {
                isParsing = true
                coroutineScope.launch {
                    val result = parser.parse(testInput, triggerTag)
                    parseResult = result
                    isParsing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isParsing && testInput.isNotBlank(),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resolving & Geocoding...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Parse & Geocode")
            }
        }

        // Quick Preset Chips
        Text(
            text = "Preset Quick Tests:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            presetExamples.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (name, payload) ->
                        OutlinedButton(
                            onClick = { testInput = payload },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Parse Result Display Card
        parseResult?.let { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (res) {
                        is ParseResult.Success -> MaterialTheme.colorScheme.secondaryContainer
                        is ParseResult.Error -> MaterialTheme.colorScheme.errorContainer
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (res) {
                        is ParseResult.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Location Resolved!",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            if (!res.label.isNullOrBlank()) {
                                Text(
                                    "📍 Place: ${res.label}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            if (res.coordinate != null) {
                                Text(
                                    "Coordinates: %.6f, %.6f".format(
                                        res.coordinate.latitude,
                                        res.coordinate.longitude
                                    ),
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (!res.searchQuery.isNullOrBlank()) {
                                Text(
                                    "Query Target: \"${res.searchQuery}\"",
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (res.sourceUrl != null) {
                                Text(
                                    "Resolved URL: ${res.sourceUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Test Launch Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { launchWaze(context, res.coordinate, res.searchQuery) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("🚗 Open Waze")
                                }
                                Button(
                                    onClick = { launchGoogleMaps(context, res.coordinate, res.searchQuery) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Text("🗺️ Google Maps")
                                }
                            }
                        }
                        is ParseResult.Error -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Could Not Parse Location",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                res.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun launchWaze(context: android.content.Context, coord: GeoCoordinate?, searchQuery: String?) {
    val hasCoords = coord != null
    try {
        val uri = if (hasCoords) {
            Uri.parse("waze://?ll=${coord!!.latitude},${coord.longitude}&navigate=yes")
        } else {
            val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
            Uri.parse("waze://?q=$encoded&navigate=yes")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.waze")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallbackUri = if (hasCoords) "geo:${coord!!.latitude},${coord.longitude}" else "geo:0,0?q=$searchQuery"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun launchGoogleMaps(context: android.content.Context, coord: GeoCoordinate?, searchQuery: String?) {
    val hasCoords = coord != null
    try {
        val uri = if (hasCoords) {
            Uri.parse("google.navigation:q=${coord!!.latitude},${coord.longitude}&mode=d")
        } else {
            val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
            Uri.parse("google.navigation:q=$encoded&mode=d")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallbackUri = if (hasCoords) "geo:${coord!!.latitude},${coord.longitude}" else "geo:0,0?q=$searchQuery"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
