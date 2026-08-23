package com.renavigator.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.renavigator.app.core.model.NavigationApp
import com.renavigator.app.data.HistoryRepository

class NavigationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val historyId = intent.getStringExtra(EXTRA_HISTORY_ID)
        val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
        val appTarget = intent.getStringExtra(EXTRA_NAV_APP)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationId != -1) {
            notificationManager.cancel(notificationId)
        }

        if (action == ACTION_DISMISS) {
            if (historyId != null) {
                HistoryRepository.getInstance(context).updateItemStatus(historyId, "DISMISSED")
            }
            return
        }

        if (action == ACTION_NAVIGATE && !lat.isNaN() && !lng.isNaN()) {
            if (historyId != null) {
                HistoryRepository.getInstance(context).updateItemStatus(historyId, "NAVIGATED")
            }
            launchNavigation(context, lat, lng, appTarget)
        }
    }

    private fun launchNavigation(context: Context, lat: Double, lng: Double, appTarget: String?) {
        val navApp = try {
            if (appTarget != null) NavigationApp.valueOf(appTarget) else NavigationApp.WAZE
        } catch (e: Exception) {
            NavigationApp.WAZE
        }

        try {
            val navIntent = when (navApp) {
                NavigationApp.WAZE -> {
                    val uri = Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        setPackage("com.waze")
                    }
                }
                NavigationApp.GOOGLE_MAPS -> {
                    val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        setPackage("com.google.android.apps.maps")
                    }
                }
                else -> {
                    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                }
            }

            // Verify if app is installed or fallback to generic geo intent
            if (navIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(navIntent)
            } else {
                // Fallback to standard geo URI without specific package lock
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open navigation: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_NAVIGATE = "com.renavigator.app.ACTION_NAVIGATE"
        const val ACTION_DISMISS = "com.renavigator.app.ACTION_DISMISS"

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_NAV_APP = "extra_nav_app"
    }
}
