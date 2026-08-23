package com.renavigator.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.renavigator.app.core.model.NavigationApp
import com.renavigator.app.data.HistoryRepository
import com.renavigator.app.data.PreferencesManager
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_HIDE_OVERLAY) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_SHOW_OVERLAY) {
            val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
            val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
            val searchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY)
            val sender = intent.getStringExtra(EXTRA_SENDER) ?: "Passenger"
            val label = intent.getStringExtra(EXTRA_LABEL)
            val distanceKm = intent.getDoubleExtra(EXTRA_DISTANCE_KM, -1.0)
            val historyId = intent.getStringExtra(EXTRA_HISTORY_ID)

            showOverlay(
                lat = if (lat.isNaN()) null else lat,
                lng = if (lng.isNaN()) null else lng,
                searchQuery = searchQuery,
                sender = sender,
                label = label,
                distanceKm = if (distanceKm >= 0) distanceKm else null,
                historyId = historyId
            )
        }

        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(
        lat: Double?,
        lng: Double?,
        searchQuery: String?,
        sender: String,
        label: String?,
        distanceKm: Double?,
        historyId: String?
    ) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        removeOverlay()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Set card width to 280dp for a clean, consistent car HUD widget
        val cardWidthPx = dpToPx(280)

        val wmLayoutParams = WindowManager.LayoutParams(
            cardWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        // Two-row card container
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))

            background = GradientDrawable().apply {
                setColor(0xF0181825.toInt()) // Deep dark slate background
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(1), 0xFF434460.toInt())
            }
            elevation = dpToPx(10).toFloat()
        }

        // --- ROW 1: Header (Car Icon + Place/Sender Title (single row, ellipsized) + Close Button) ---
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val carIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setColorFilter(0xFF388AF6.toInt())
        }
        val carIconParams = LinearLayout.LayoutParams(dpToPx(18), dpToPx(18)).apply {
            marginEnd = dpToPx(6)
        }
        row1.addView(carIcon, carIconParams)

        val titleText = when {
            !label.isNullOrBlank() -> label
            !searchQuery.isNullOrBlank() -> searchQuery
            else -> "From $sender"
        }

        val titleView = TextView(this).apply {
            text = titleText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val titleParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            marginEnd = dpToPx(8)
        }
        row1.addView(titleView, titleParams)

        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFAAAAAA.toInt())
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))

            setOnClickListener {
                if (historyId != null) {
                    HistoryRepository.getInstance(this@FloatingOverlayService)
                        .updateItemStatus(historyId, "DISMISSED")
                }
                removeOverlay()
                stopSelf()
            }
        }
        val closeBtnParams = LinearLayout.LayoutParams(dpToPx(22), dpToPx(22))
        row1.addView(closeBtn, closeBtnParams)

        card.addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Spacer between rows
        val spacer = View(this)
        card.addView(spacer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6)))

        // --- ROW 2: Detail/Distance (Left) + Prominent GO Button (Right) ---
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val distanceText = when {
            distanceKm != null -> "📍 %.1f km away".format(distanceKm)
            lat != null && lng != null -> "📍 %.4f, %.4f".format(lat, lng)
            !searchQuery.isNullOrBlank() -> "🔍 Search query"
            else -> "📍 From $sender"
        }

        val distanceView = TextView(this).apply {
            text = distanceText
            setTextColor(0xFF388AF6.toInt()) // Clean light blue accent
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val distanceParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            marginEnd = dpToPx(8)
        }
        row2.addView(distanceView, distanceParams)

        // GO Button (Always present, always right-aligned, high contrast)
        val prefs = PreferencesManager.getInstance(this)
        val defaultApp = prefs.defaultNavApp.value

        val goButton = Button(this).apply {
            text = if (defaultApp == NavigationApp.GOOGLE_MAPS) "GO Maps" else "GO Waze"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(0xFF2E7D32.toInt()) // Vibrant Green
                cornerRadius = dpToPx(14).toFloat()
            }
            setPadding(dpToPx(14), 0, dpToPx(14), 0)

            setOnClickListener {
                if (historyId != null) {
                    HistoryRepository.getInstance(this@FloatingOverlayService)
                        .updateItemStatus(historyId, "NAVIGATED")
                }
                launchNavFromOverlay(lat, lng, searchQuery, defaultApp)
                removeOverlay()
                stopSelf()
            }
        }
        val goButtonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dpToPx(34)
        )
        row2.addView(goButton, goButtonParams)

        card.addView(row2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Add drag-to-move touch listener to the card
        card.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = wmLayoutParams.x
                        initialY = wmLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false // Allow clicks on children (buttons)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            wmLayoutParams.x = initialX + dx
                            wmLayoutParams.y = initialY + dy
                            try {
                                windowManager?.updateViewLayout(card, wmLayoutParams)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(card, wmLayoutParams)
            overlayView = card
            Log.i(TAG, "Floating overlay displayed for: $titleText ($distanceText)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating overlay view: ${e.message}", e)
        }
    }

    private fun launchNavFromOverlay(lat: Double?, lng: Double?, searchQuery: String?, navApp: NavigationApp) {
        val hasCoords = lat != null && lng != null
        val intent = when (navApp) {
            NavigationApp.GOOGLE_MAPS -> {
                val uri = if (hasCoords) {
                    Uri.parse("google.navigation:q=$lat,$lng&mode=d")
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
                    Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
                } else {
                    val encoded = URLEncoder.encode(searchQuery ?: "", StandardCharsets.UTF_8.name())
                    Uri.parse("waze://?q=$encoded&navigate=yes")
                }
                Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.waze")
                }
            }
        }

        try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                val fallbackUri = if (hasCoords) "geo:$lat,$lng?q=$lat,$lng" else "geo:0,0?q=$searchQuery"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch navigation app from overlay: ${e.message}")
        }
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "FloatingOverlay"
        const val ACTION_SHOW_OVERLAY = "com.renavigator.app.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.renavigator.app.ACTION_HIDE_OVERLAY"

        const val EXTRA_LATITUDE = "extra_lat"
        const val EXTRA_LONGITUDE = "extra_lng"
        const val EXTRA_SEARCH_QUERY = "extra_search_query"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_DISTANCE_KM = "extra_distance_km"
        const val EXTRA_HISTORY_ID = "extra_history_id"

        fun show(
            context: Context,
            lat: Double? = null,
            lng: Double? = null,
            searchQuery: String? = null,
            sender: String = "Passenger",
            label: String? = null,
            distanceKm: Double? = null,
            historyId: String? = null
        ) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingOverlayService::class.java).apply {
                    action = ACTION_SHOW_OVERLAY
                    if (lat != null && lng != null) {
                        putExtra(EXTRA_LATITUDE, lat)
                        putExtra(EXTRA_LONGITUDE, lng)
                    }
                    if (searchQuery != null) putExtra(EXTRA_SEARCH_QUERY, searchQuery)
                    putExtra(EXTRA_SENDER, sender)
                    if (label != null) putExtra(EXTRA_LABEL, label)
                    if (distanceKm != null) putExtra(EXTRA_DISTANCE_KM, distanceKm)
                    putExtra(EXTRA_HISTORY_ID, historyId)
                }
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.startService(intent)
        }
    }
}
