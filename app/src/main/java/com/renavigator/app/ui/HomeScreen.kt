package com.renavigator.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.renavigator.app.core.model.DestinationHistoryItem
import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.data.HistoryRepository
import com.renavigator.app.data.PreferencesManager
import com.renavigator.app.service.DriverNotificationManager
import com.renavigator.app.service.FloatingOverlayService
import com.renavigator.app.ui.theme.StatusGreen
import com.renavigator.app.ui.theme.StatusRed

@Composable
fun HomeScreen(
    isNotificationPermissionGranted: Boolean,
    isLocationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val serviceEnabled by prefsManager.serviceEnabled.collectAsState()
    val persistentCarMode by prefsManager.persistentCarMode.collectAsState()
    val floatingBubbleEnabled by prefsManager.floatingBubbleEnabled.collectAsState()
    val defaultNavApp by prefsManager.defaultNavApp.collectAsState()
    val triggerTag by prefsManager.triggerTag.collectAsState()
    val maxDistance by prefsManager.maxDistanceKm.collectAsState()

    var isOverlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    var isBatteryOptimized by remember {
        val pm = try { context.getSystemService(Context.POWER_SERVICE) as? PowerManager } catch (e: Exception) { null }
        val isOpt = try { pm?.isIgnoringBatteryOptimizations(context.packageName) == false } catch (e: Exception) { false }
        mutableStateOf(isOpt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Service Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (serviceEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (serviceEnabled) "reNavigator Active" else "reNavigator Paused",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (serviceEnabled) "Listening for '$triggerTag' in WhatsApp, Telegram, etc." else "Tap switch to enable listening",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = serviceEnabled,
                    onCheckedChange = { prefsManager.setServiceEnabled(it) }
                )
            }
        }

        // Floating Overlay Bubble Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Floating Action Bubble",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Text(
                        text = "Pops up an interactive floating pill directly over Waze with a 1-tap GO button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = floatingBubbleEnabled,
                    onCheckedChange = { prefsManager.setFloatingBubbleEnabled(it) }
                )
            }
        }

        // Persistent Car Mode Card (Foreground Service)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Persistent Car Mode",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Text(
                        text = "Keeps app running in background while Waze or Maps is navigating.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = persistentCarMode,
                    onCheckedChange = { prefsManager.setPersistentCarMode(it) }
                )
            }
        }

        // Permissions Section
        Text(
            text = "System Permissions & Background Setup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // 1. Notification Access Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isNotificationPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isNotificationPermissionGranted) StatusGreen else StatusRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Notification Access",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isNotificationPermissionGranted) "Granted (Reading #nav)" else "Required to read incoming #nav messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!isNotificationPermissionGranted) {
                        Button(
                            onClick = { openNotificationListenerSettings(context) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant")
                        }
                    }
                }

                if (!isNotificationPermissionGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "💡 Android 13/14+ Restricted Setting Tip:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "If Android says 'Restricted setting', go to App Info > tap 3 dots (top right) > 'Allow restricted settings', then come back and Grant access.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 2. Display Over Other Apps (Overlay Permission) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isOverlayGranted) Icons.Default.CheckCircle else Icons.Default.LayersClear,
                        contentDescription = null,
                        tint = if (isOverlayGranted) StatusGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Display Over Other Apps",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isOverlayGranted) "Granted (Floating bubble active)" else "Required for the floating bubble over Waze",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isOverlayGranted) {
                    Button(
                        onClick = { openOverlayPermissionSettings(context) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Grant")
                    }
                }
            }
        }

        // 3. Battery Optimization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (!isBatteryOptimized) Icons.Default.BatteryChargingFull else Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = if (!isBatteryOptimized) StatusGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Background Battery Policy",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (!isBatteryOptimized) "Unrestricted (Won't be killed in background)" else "Optimized (Android may sleep app during long drives)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isBatteryOptimized) {
                    OutlinedButton(
                        onClick = { requestIgnoreBatteryOptimizations(context) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Unrestrict")
                    }
                }
            }
        }

        // 4. Location Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isLocationPermissionGranted) Icons.Default.CheckCircle else Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = if (isLocationPermissionGranted) StatusGreen else StatusRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Location Permission",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isLocationPermissionGranted) "Granted (Distance checks enabled)" else "Used to calculate distance to stops",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isLocationPermissionGranted) {
                    Button(
                        onClick = onRequestLocationPermission,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Allow")
                    }
                }
            }
        }

        // Quick Test Alert Button
        OutlinedButton(
            onClick = {
                val dummyItem = DestinationHistoryItem(
                    senderName = "Alex (Test)",
                    sourcePackage = "com.whatsapp",
                    coordinate = GeoCoordinate(56.9496, 24.1052),
                    label = "Test Rest Stop",
                    distanceKm = 14.8,
                    originalMessage = "Hey here is the stop #nav https://maps.app.goo.gl/example",
                    status = "TEST"
                )
                HistoryRepository.getInstance(context).addItem(dummyItem)
                DriverNotificationManager.getInstance(context).showDestinationAlert(dummyItem)
                if (prefsManager.floatingBubbleEnabled.value && Settings.canDrawOverlays(context)) {
                    FloatingOverlayService.show(
                        context = context,
                        lat = 56.9496,
                        lng = 24.1052,
                        sender = "Alex (Test)",
                        label = "Test Rest Stop",
                        distanceKm = 14.8,
                        historyId = dummyItem.id
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simulate Heads-Up Nav Alert & Bubble")
        }
    }
}

fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    context.startActivity(intent)
}

fun openOverlayPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}

fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(fallback)
    }
}
