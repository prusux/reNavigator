package com.renavigator.app.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.renavigator.app.core.model.NavigationApp
import com.renavigator.app.data.HistoryRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Transparent activity that handles notification action clicks and launches navigation directly.
 * Using an Activity rather than a BroadcastReceiver ensures that Android 10+ Background Activity Launch (BAL)
 * restrictions never block launching Waze or Google Maps.
 */
class NavigationTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            handleIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling navigation trampoline: ${e.message}", e)
        } finally {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            try {
                handleIntent(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling navigation trampoline new intent: ${e.message}", e)
            } finally {
                finish()
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val historyId = intent.getStringExtra(EXTRA_HISTORY_ID)
        val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
        val searchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY)
        val appTarget = intent.getStringExtra(EXTRA_NAV_APP)

        // Cancel the alert notification if requested
        if (notificationId != -1) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        if (action == ACTION_DISMISS) {
            if (historyId != null) {
                HistoryRepository.getInstance(this).updateItemStatus(historyId, "DISMISSED")
            }
            return
        }

        if (action == ACTION_NAVIGATE) {
            if (historyId != null) {
                HistoryRepository.getInstance(this).updateItemStatus(historyId, "NAVIGATED")
            }
            launchNavigation(lat, lng, searchQuery, appTarget)
        }
    }

    private fun launchNavigation(lat: Double, lng: Double, searchQuery: String?, appTarget: String?) {
        val navApp = try {
            if (appTarget != null) NavigationApp.valueOf(appTarget) else NavigationApp.WAZE
        } catch (e: Exception) {
            NavigationApp.WAZE
        }

        val hasCoords = !lat.isNaN() && !lng.isNaN()
        Log.i(TAG, "Launching $navApp for target: coords=($lat, $lng), query=$searchQuery")

        when (navApp) {
            NavigationApp.WAZE -> {
                val wazeUri = if (hasCoords) {
                    Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
                } else {
                    val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
                    Uri.parse("waze://?q=$encoded&navigate=yes")
                }

                val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.waze")
                }

                if (wazeIntent.resolveActivity(packageManager) != null) {
                    startActivity(wazeIntent)
                } else {
                    val webUri = if (hasCoords) {
                        Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")
                    } else {
                        val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
                        Uri.parse("https://waze.com/ul?q=$encoded&navigate=yes")
                    }
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (e: Exception) {
                        val geoUri = if (hasCoords) "geo:$lat,$lng?q=$lat,$lng" else "geo:0,0?q=$searchQuery"
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
            }

            NavigationApp.GOOGLE_MAPS -> {
                val mapsUri = if (hasCoords) {
                    Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                } else {
                    val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
                    Uri.parse("google.navigation:q=$encoded&mode=d")
                }

                val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.google.android.apps.maps")
                }

                if (mapsIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapsIntent)
                } else {
                    val geoUri = if (hasCoords) "geo:$lat,$lng?q=$lat,$lng" else "geo:0,0?q=$searchQuery"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }

            else -> {
                val geoUri = if (hasCoords) "geo:$lat,$lng?q=$lat,$lng" else "geo:0,0?q=$searchQuery"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }

    companion object {
        private const val TAG = "NavTrampoline"
        const val ACTION_NAVIGATE = "com.renavigator.app.ACTION_NAVIGATE"
        const val ACTION_DISMISS = "com.renavigator.app.ACTION_DISMISS"

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_SEARCH_QUERY = "extra_search_query"
        const val EXTRA_NAV_APP = "extra_nav_app"
    }
}
