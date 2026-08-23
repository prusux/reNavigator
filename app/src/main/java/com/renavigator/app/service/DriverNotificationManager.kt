package com.renavigator.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.renavigator.app.core.model.DestinationHistoryItem
import com.renavigator.app.core.model.NavigationApp
import com.renavigator.app.data.PreferencesManager
import com.renavigator.app.ui.NavigationTrampolineActivity

class DriverNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefsManager = PreferencesManager.getInstance(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "reNavigator Car Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up alerts when new destinations are received from passengers"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDestinationAlert(item: DestinationHistoryItem) {
        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        val defaultApp = prefsManager.defaultNavApp.value

        val distanceText = if (item.distanceKm != null) {
            " • %.1f km away".format(item.distanceKm)
        } else {
            ""
        }

        val title = if (!item.label.isNullOrBlank()) {
            "🚗 ${item.label}"
        } else {
            "🚗 Destination from ${item.senderName}"
        }

        val contentText = when {
            item.coordinate != null -> "${item.coordinate.toFormattedString()}$distanceText"
            !item.searchQuery.isNullOrBlank() -> "Search: \"${item.searchQuery}\" (from ${item.senderName})"
            else -> "From ${item.senderName}"
        }

        // Clicking notification body triggers default navigation
        val contentPendingIntent = createNavPendingIntent(notificationId, item, defaultApp)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\"${item.originalMessage.take(120)}\"")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 150, 250))

        // Navigation Actions
        when (defaultApp) {
            NavigationApp.WAZE -> {
                builder.addAction(
                    android.R.drawable.ic_menu_directions,
                    "🚗 GO (Waze)",
                    createNavPendingIntent(notificationId, item, NavigationApp.WAZE)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_mapmode,
                    "🗺️ Google Maps",
                    createNavPendingIntent(notificationId, item, NavigationApp.GOOGLE_MAPS)
                )
            }
            NavigationApp.GOOGLE_MAPS -> {
                builder.addAction(
                    android.R.drawable.ic_menu_mapmode,
                    "🗺️ GO (Google Maps)",
                    createNavPendingIntent(notificationId, item, NavigationApp.GOOGLE_MAPS)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_directions,
                    "🚗 Waze",
                    createNavPendingIntent(notificationId, item, NavigationApp.WAZE)
                )
            }
            NavigationApp.PROMPT_BOTH -> {
                builder.addAction(
                    android.R.drawable.ic_menu_directions,
                    "🚗 Waze",
                    createNavPendingIntent(notificationId, item, NavigationApp.WAZE)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_mapmode,
                    "🗺️ Google Maps",
                    createNavPendingIntent(notificationId, item, NavigationApp.GOOGLE_MAPS)
                )
            }
        }

        // Dismiss Action
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "✖ Dismiss",
            createDismissPendingIntent(notificationId, item)
        )

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNavPendingIntent(
        notificationId: Int,
        item: DestinationHistoryItem,
        targetApp: NavigationApp
    ): PendingIntent {
        val intent = Intent(context, NavigationTrampolineActivity::class.java).apply {
            action = NavigationTrampolineActivity.ACTION_NAVIGATE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NavigationTrampolineActivity.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NavigationTrampolineActivity.EXTRA_HISTORY_ID, item.id)
            if (item.coordinate != null) {
                putExtra(NavigationTrampolineActivity.EXTRA_LATITUDE, item.coordinate.latitude)
                putExtra(NavigationTrampolineActivity.EXTRA_LONGITUDE, item.coordinate.longitude)
            }
            if (item.searchQuery != null) {
                putExtra(NavigationTrampolineActivity.EXTRA_SEARCH_QUERY, item.searchQuery)
            }
            putExtra(NavigationTrampolineActivity.EXTRA_NAV_APP, targetApp.name)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, notificationId * 10 + targetApp.ordinal, intent, flags)
    }

    private fun createDismissPendingIntent(
        notificationId: Int,
        item: DestinationHistoryItem
    ): PendingIntent {
        val intent = Intent(context, NavigationTrampolineActivity::class.java).apply {
            action = NavigationTrampolineActivity.ACTION_DISMISS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NavigationTrampolineActivity.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NavigationTrampolineActivity.EXTRA_HISTORY_ID, item.id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, notificationId * 10 + 9, intent, flags)
    }

    companion object {
        const val CHANNEL_ID = "renavigator_car_alerts_channel"

        @Volatile
        private var INSTANCE: DriverNotificationManager? = null

        fun getInstance(context: Context): DriverNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DriverNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
