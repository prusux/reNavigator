package com.renavigator.app.service

import android.Manifest
import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.renavigator.app.core.geocoder.PlaceSearchEngine
import com.renavigator.app.core.model.DestinationHistoryItem
import com.renavigator.app.core.model.GeoCoordinate
import com.renavigator.app.core.model.ParseResult
import com.renavigator.app.core.model.ValidationResult
import com.renavigator.app.core.parser.LocationParser
import com.renavigator.app.core.validator.LocationValidator
import com.renavigator.app.data.HistoryRepository
import com.renavigator.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class IncomingMessagePayload(
    val text: String,
    val senderName: String,
    val timestamp: Long
)

class ReNavNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationParser: LocationParser
    private val locationValidator = LocationValidator()

    private lateinit var prefsManager: PreferencesManager
    private lateinit var historyRepository: HistoryRepository
    private lateinit var driverNotificationManager: DriverNotificationManager

    // Cache processed message signatures with expiration to prevent duplicate triggers
    private val processedSignatures = ConcurrentHashMap<String, Long>()
    private var serviceStartTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        serviceStartTime = System.currentTimeMillis()
        prefsManager = PreferencesManager.getInstance(this)
        historyRepository = HistoryRepository.getInstance(this)
        driverNotificationManager = DriverNotificationManager.getInstance(this)
        locationParser = LocationParser(placeSearchEngine = PlaceSearchEngine(this))
        Log.i(TAG, "ReNavNotificationListenerService started at $serviceStartTime")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Ignore notifications from ourselves
        if (sbn.packageName == packageName) return

        // Ignore if listening is disabled
        if (!prefsManager.serviceEnabled.value) return

        // Filter out notifications posted significantly before service started (older than 2 minutes)
        if (sbn.postTime > 0 && sbn.postTime < (serviceStartTime - 120_000)) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val triggerTag = prefsManager.triggerTag.value

        // Extract the newest message payload from the notification
        val payload = extractLatestMessagePayload(notification, extras, sbn.postTime)
        if (payload == null || payload.text.isBlank()) return

        // Check if the extracted text contains the trigger keyword
        if (!locationParser.hasNavTrigger(payload.text, triggerTag)) {
            return
        }

        // Deduplication check: ignore if the exact same message was processed in the last 60 seconds
        val signature = "${sbn.packageName}|${payload.senderName}|${payload.text.trim()}|${payload.timestamp}"
        val now = System.currentTimeMillis()
        cleanOldSignatures(now)

        if (processedSignatures.containsKey(signature)) {
            Log.d(TAG, "Ignoring duplicate notification signature: $signature")
            return
        }
        processedSignatures[signature] = now

        Log.i(TAG, "Trigger '$triggerTag' matched from ${payload.senderName} (${sbn.packageName}): ${payload.text}")

        // Process in background coroutine
        serviceScope.launch {
            processMessage(
                payload = payload,
                packageName = sbn.packageName,
                triggerTag = triggerTag
            )
        }
    }

    /**
     * Extracts the latest message, sender name, and timestamp from MessagingStyle, InboxStyle, or standard notifications.
     */
    private fun extractLatestMessagePayload(
        notification: Notification,
        extras: Bundle,
        postTime: Long
    ): IncomingMessagePayload? {
        val triggerTag = prefsManager.triggerTag.value
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val defaultTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Passenger"

        // 1. Check NotificationCompat.MessagingStyle (used by WhatsApp, Telegram, Signal, Google Messages)
        try {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                // Find the newest message that contains the trigger tag (searching backwards from newest)
                val targetMessage = messagingStyle.messages.asReversed().firstOrNull { msg ->
                    val txt = msg.text?.toString() ?: ""
                    locationParser.hasNavTrigger(txt, triggerTag)
                } ?: messagingStyle.messages.last()

                val msgText = targetMessage.text?.toString() ?: ""
                val personName = targetMessage.person?.name?.toString()
                    ?: targetMessage.sender?.toString()
                    ?: defaultTitle

                val senderFormatted = if (!conversationTitle.isNullOrBlank() && personName != conversationTitle) {
                    "$personName ($conversationTitle)"
                } else {
                    personName
                }

                val msgTimestamp = if (targetMessage.timestamp > 0) targetMessage.timestamp else postTime

                return IncomingMessagePayload(
                    text = msgText,
                    senderName = senderFormatted,
                    timestamp = msgTimestamp
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "MessagingStyle extraction fallback: ${e.message}")
        }

        // 2. Check InboxStyle (EXTRA_TEXT_LINES)
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (textLines != null && textLines.isNotEmpty()) {
            val lastLine = textLines.last().toString()
            return IncomingMessagePayload(
                text = lastLine,
                senderName = defaultTitle,
                timestamp = postTime
            )
        }

        // 3. Check Standard Notification Text / BigText
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val primaryText = when {
            !bigText.isNullOrBlank() -> bigText
            !text.isNullOrBlank() -> text
            else -> ""
        }

        if (primaryText.isNotBlank()) {
            return IncomingMessagePayload(
                text = primaryText,
                senderName = defaultTitle,
                timestamp = postTime
            )
        }

        return null
    }

    private fun cleanOldSignatures(now: Long) {
        val iterator = processedSignatures.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > 60_000) { // 60 seconds TTL
                iterator.remove()
            }
        }
    }

    private suspend fun processMessage(
        payload: IncomingMessagePayload,
        packageName: String,
        triggerTag: String
    ) {
        val currentLocation = getCurrentDriverLocation()
        val parseResult = locationParser.parse(payload.text, triggerTag, currentLocation)
        if (parseResult !is ParseResult.Success) {
            Log.w(TAG, "Failed to parse coordinates or place from message: ${payload.text}")
            return
        }

        val destination = parseResult.coordinate
        val maxAllowedKm = prefsManager.maxDistanceKm.value

        val validation = locationValidator.validate(
            destination = destination,
            searchQuery = parseResult.searchQuery,
            currentLocation = currentLocation,
            maxDistanceKm = maxAllowedKm,
            label = parseResult.label
        )

        when (validation) {
            is ValidationResult.Valid -> {
                val item = DestinationHistoryItem(
                    senderName = payload.senderName,
                    sourcePackage = packageName,
                    coordinate = destination,
                    searchQuery = parseResult.searchQuery,
                    label = parseResult.label,
                    distanceKm = validation.distanceKm,
                    originalMessage = payload.text,
                    status = "RECEIVED"
                )
                historyRepository.addItem(item)
                driverNotificationManager.showDestinationAlert(item)
                if (prefsManager.floatingBubbleEnabled.value) {
                    FloatingOverlayService.show(
                        context = this@ReNavNotificationListenerService,
                        lat = destination?.latitude,
                        lng = destination?.longitude,
                        searchQuery = parseResult.searchQuery,
                        sender = payload.senderName,
                        label = parseResult.label,
                        distanceKm = validation.distanceKm,
                        historyId = item.id
                    )
                }
                Log.i(TAG, "Valid destination dispatched: ${parseResult.label ?: destination?.toFormattedString()} (${validation.distanceKm} km)")
            }
            is ValidationResult.ExceedsMaxDistance -> {
                val item = DestinationHistoryItem(
                    senderName = payload.senderName,
                    sourcePackage = packageName,
                    coordinate = destination,
                    searchQuery = parseResult.searchQuery,
                    label = parseResult.label,
                    distanceKm = validation.distanceKm,
                    originalMessage = payload.text,
                    status = "OUT_OF_RANGE"
                )
                historyRepository.addItem(item)
                Log.w(TAG, "Destination rejected: ${validation.distanceKm} km exceeds max limit ($maxAllowedKm km)")
            }
            is ValidationResult.InvalidCoordinates -> {
                Log.w(TAG, "Destination coordinates invalid: ${validation.message}")
            }
        }
    }

    private suspend fun getCurrentDriverLocation(): GeoCoordinate? {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            suspendCancellableCoroutine { continuation ->
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(GeoCoordinate(location.latitude, location.longitude))
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        continuation.resume(null)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not fetch driver location: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "ReNavListener"
    }
}
