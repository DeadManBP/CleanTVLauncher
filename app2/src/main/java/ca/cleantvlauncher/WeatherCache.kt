package ca.cleantvlauncher

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small, local-only cache for the home-screen weather summary. */
data class CachedWeather(
    val icon: String,
    val temperature: String,
    val condition: String,
    val highLow: String,
    val savedAt: Long
)

object WeatherCache {
    private const val PREFS = "clean_tv_weather_cache"
    private const val KEY_ICON = "icon"
    private const val KEY_TEMPERATURE = "temperature"
    private const val KEY_CONDITION = "condition"
    private const val KEY_HIGH_LOW = "high_low"
    private const val KEY_SAVED_AT = "saved_at"

    fun save(context: Context, weather: CachedWeather) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ICON, weather.icon)
            .putString(KEY_TEMPERATURE, weather.temperature)
            .putString(KEY_CONDITION, weather.condition)
            .putString(KEY_HIGH_LOW, weather.highLow)
            .putLong(KEY_SAVED_AT, weather.savedAt)
            .apply()
    }

    fun load(context: Context): CachedWeather? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        val temperature = prefs.getString(KEY_TEMPERATURE, null) ?: return null
        if (savedAt <= 0L || temperature.isBlank()) return null
        return CachedWeather(
            icon = prefs.getString(KEY_ICON, "☁") ?: "☁",
            temperature = temperature,
            condition = prefs.getString(KEY_CONDITION, "Conditions") ?: "Conditions",
            highLow = prefs.getString(KEY_HIGH_LOW, "Forecast unavailable") ?: "Forecast unavailable",
            savedAt = savedAt
        )
    }

    fun staleLabel(savedAt: Long): String {
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(savedAt))
        return "Saved weather · $time"
    }
}
