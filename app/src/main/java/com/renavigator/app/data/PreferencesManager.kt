package com.renavigator.app.data

import android.content.Context
import android.content.SharedPreferences
import com.renavigator.app.core.model.NavigationApp
import com.renavigator.app.service.ReNavForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("renav_prefs", Context.MODE_PRIVATE)

    private val _defaultNavApp = MutableStateFlow(loadDefaultNavApp())
    val defaultNavApp: StateFlow<NavigationApp> = _defaultNavApp.asStateFlow()

    private val _triggerTag = MutableStateFlow(loadTriggerTag())
    val triggerTag: StateFlow<String> = _triggerTag.asStateFlow()

    private val _maxDistanceKm = MutableStateFlow(loadMaxDistanceKm())
    val maxDistanceKm: StateFlow<Double> = _maxDistanceKm.asStateFlow()

    private val _serviceEnabled = MutableStateFlow(loadServiceEnabled())
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    private val _persistentCarMode = MutableStateFlow(loadPersistentCarMode())
    val persistentCarMode: StateFlow<Boolean> = _persistentCarMode.asStateFlow()

    private val _floatingBubbleEnabled = MutableStateFlow(loadFloatingBubbleEnabled())
    val floatingBubbleEnabled: StateFlow<Boolean> = _floatingBubbleEnabled.asStateFlow()

    fun setDefaultNavApp(app: NavigationApp) {
        prefs.edit().putString(KEY_NAV_APP, app.name).apply()
        _defaultNavApp.value = app
    }

    fun setTriggerTag(tag: String) {
        val cleanTag = tag.trim().ifEmpty { "#nav" }
        prefs.edit().putString(KEY_TRIGGER_TAG, cleanTag).apply()
        _triggerTag.value = cleanTag
    }

    fun setMaxDistanceKm(km: Double) {
        val value = km.coerceIn(10.0, 5000.0)
        prefs.edit().putFloat(KEY_MAX_DISTANCE, value.toFloat()).apply()
        _maxDistanceKm.value = value
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        _serviceEnabled.value = enabled
        try {
            if (enabled && _persistentCarMode.value) {
                ReNavForegroundService.startService(context)
            } else if (!enabled) {
                ReNavForegroundService.stopService(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPersistentCarMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERSISTENT_CAR_MODE, enabled).apply()
        _persistentCarMode.value = enabled
        try {
            if (enabled && _serviceEnabled.value) {
                ReNavForegroundService.startService(context)
            } else {
                ReNavForegroundService.stopService(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_BUBBLE, enabled).apply()
        _floatingBubbleEnabled.value = enabled
    }

    private fun loadDefaultNavApp(): NavigationApp {
        val name = prefs.getString(KEY_NAV_APP, NavigationApp.WAZE.name)
        return try {
            NavigationApp.valueOf(name ?: NavigationApp.WAZE.name)
        } catch (e: Exception) {
            NavigationApp.WAZE
        }
    }

    private fun loadTriggerTag(): String = prefs.getString(KEY_TRIGGER_TAG, "#nav") ?: "#nav"
    private fun loadMaxDistanceKm(): Double = prefs.getFloat(KEY_MAX_DISTANCE, 400.0f).toDouble()
    private fun loadServiceEnabled(): Boolean = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
    private fun loadPersistentCarMode(): Boolean = prefs.getBoolean(KEY_PERSISTENT_CAR_MODE, false)
    private fun loadFloatingBubbleEnabled(): Boolean = prefs.getBoolean(KEY_FLOATING_BUBBLE, true)

    companion object {
        private const val KEY_NAV_APP = "pref_default_nav_app"
        private const val KEY_TRIGGER_TAG = "pref_trigger_tag"
        private const val KEY_MAX_DISTANCE = "pref_max_distance"
        private const val KEY_SERVICE_ENABLED = "pref_service_enabled"
        private const val KEY_PERSISTENT_CAR_MODE = "pref_persistent_car_mode"
        private const val KEY_FLOATING_BUBBLE = "pref_floating_bubble"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
