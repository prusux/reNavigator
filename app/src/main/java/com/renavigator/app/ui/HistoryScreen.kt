package com.renavigator.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.renavigator.app.core.model.DestinationHistoryItem
import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.core.model.NavigationApp
import com.renavigator.app.data.HistoryRepository
import com.renavigator.app.data.PreferencesManager
import com.renavigator.app.ui.theme.StatusAmber
import com.renavigator.app.ui.theme.StatusGreen
import com.renavigator.app.ui.theme.StatusRed
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository.getInstance(context) }
    val historyList by historyRepo.history.collectAsState()
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val defaultNavApp by prefsManager.defaultNavApp.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Trip History & Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${historyList.size} destinations received",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (historyList.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear History")
                }
            }
        }

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No destinations received yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Incoming WhatsApp/Messenger #nav locations will appear here in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(historyList, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        defaultNavApp = defaultNavApp,
                        onNavigate = { app ->
                            launchNavigationDirect(context, item.coordinate, item.searchQuery, app)
                            historyRepo.updateItemStatus(item.id, "NAVIGATED")
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All History?") },
            text = { Text("This will delete all logged destinations from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyRepo.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HistoryItemCard(
    item: DestinationHistoryItem,
    defaultNavApp: NavigationApp,
    onNavigate: (NavigationApp) -> Unit
) {
    val timeFormatted = remember(item.timestamp) {
        SimpleDateFormat("HH:mm - dd MMM", Locale.getDefault()).format(Date(item.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.senderName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                StatusBadge(status = item.status)
            }

            if (!item.label.isNullOrBlank()) {
                Text(
                    text = "🏷️ ${item.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val locText = when {
                item.coordinate != null -> "📍 ${item.coordinate.toFormattedString()}" +
                        (if (item.distanceKm != null) " (%.1f km away)".format(item.distanceKm) else "")
                !item.searchQuery.isNullOrBlank() -> "🔍 Search: \"${item.searchQuery}\""
                else -> "Destination"
            }

            Text(
                text = locText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (item.originalMessage.isNotBlank()) {
                Text(
                    text = "\"${item.originalMessage.take(100)}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onNavigate(NavigationApp.WAZE) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🚗 Waze", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = { onNavigate(NavigationApp.GOOGLE_MAPS) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🗺️ Maps", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "NAVIGATED" -> Triple(StatusGreen.copy(alpha = 0.15f), StatusGreen, "Navigated")
        "DISMISSED" -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Dismissed")
        "OUT_OF_RANGE" -> Triple(StatusRed.copy(alpha = 0.15f), StatusRed, "Out of Range")
        "TEST" -> Triple(StatusAmber.copy(alpha = 0.15f), StatusAmber, "Test Alert")
        else -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, "Received")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun launchNavigationDirect(
    context: android.content.Context,
    coord: GeoCoordinate?,
    searchQuery: String?,
    navApp: NavigationApp
) {
    val hasCoords = coord != null
    try {
        val intent = when (navApp) {
            NavigationApp.WAZE -> {
                val uri = if (hasCoords) {
                    Uri.parse("waze://?ll=${coord!!.latitude},${coord.longitude}&navigate=yes")
                } else {
                    val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
                    Uri.parse("waze://?q=$encoded&navigate=yes")
                }
                Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.waze")
                }
            }
            NavigationApp.GOOGLE_MAPS -> {
                val uri = if (hasCoords) {
                    Uri.parse("google.navigation:q=${coord!!.latitude},${coord.longitude}&mode=d")
                } else {
                    val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
                    Uri.parse("google.navigation:q=$encoded&mode=d")
                }
                Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.google.android.apps.maps")
                }
            }
            else -> {
                val uri = if (hasCoords) {
                    Uri.parse("geo:${coord!!.latitude},${coord.longitude}?q=${coord.latitude},${coord.longitude}")
                } else {
                    Uri.parse("geo:0,0?q=$searchQuery")
                }
                Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
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
        Toast.makeText(context, "Launch error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
