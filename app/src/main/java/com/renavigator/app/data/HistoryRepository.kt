package com.renavigator.app.data

import android.content.Context
import android.content.SharedPreferences
import com.renavigator.app.core.model.DestinationHistoryItem
import com.renavigator.app.core.model.GeoCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("renav_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<DestinationHistoryItem>>(emptyList())
    val history: StateFlow<List<DestinationHistoryItem>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    @Synchronized
    fun addItem(item: DestinationHistoryItem) {
        val currentList = _history.value.toMutableList()
        currentList.add(0, item)
        // Keep max 50 items
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _history.value = currentList
        saveHistory(currentList)
    }

    @Synchronized
    fun updateItemStatus(itemId: String, newStatus: String) {
        val updated = _history.value.map {
            if (it.id == itemId) it.copy(status = newStatus) else it
        }
        _history.value = updated
        saveHistory(updated)
    }

    @Synchronized
    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history_json").apply()
    }

    private fun loadHistory() {
        val jsonStr = prefs.getString("history_json", null) ?: return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DestinationHistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val lat = if (obj.has("lat") && !obj.isNull("lat")) obj.getDouble("lat") else Double.NaN
                val lng = if (obj.has("lng") && !obj.isNull("lng")) obj.getDouble("lng") else Double.NaN
                val coord = if (!lat.isNaN() && !lng.isNaN()) GeoCoordinate(lat, lng) else null

                list.add(
                    DestinationHistoryItem(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        senderName = obj.getString("senderName"),
                        sourcePackage = obj.getString("sourcePackage"),
                        coordinate = coord,
                        searchQuery = if (obj.has("searchQuery") && !obj.isNull("searchQuery")) obj.getString("searchQuery") else null,
                        label = if (obj.has("label") && !obj.isNull("label")) obj.getString("label") else null,
                        distanceKm = if (obj.has("distanceKm") && !obj.isNull("distanceKm")) obj.getDouble("distanceKm") else null,
                        originalMessage = obj.getString("originalMessage"),
                        status = obj.optString("status", "RECEIVED")
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistory(list: List<DestinationHistoryItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("senderName", item.senderName)
                    put("sourcePackage", item.sourcePackage)
                    if (item.coordinate != null) {
                        put("lat", item.coordinate.latitude)
                        put("lng", item.coordinate.longitude)
                    }
                    put("searchQuery", item.searchQuery)
                    put("label", item.label)
                    put("distanceKm", item.distanceKm)
                    put("originalMessage", item.originalMessage)
                    put("status", item.status)
                }
                array.put(obj)
            }
            prefs.edit().putString("history_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
