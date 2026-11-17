package ca.cleantvlauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Right-side ECCC weather detail overlay. */
class WeatherDetailsActivity : Activity() {
    private val accent = Color.rgb(220, 50, 68)
    private val panel = Color.rgb(18, 22, 30)
    private val muted = Color.rgb(157, 167, 181)
    private lateinit var content: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.58f)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val scrim = FrameLayout(this)
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(25), dp(24), dp(25), dp(20))
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f, dp(20).toFloat(), dp(20).toFloat())
                setStroke(dp(1), Color.rgb(68, 45, 53))
            }
            elevation = dp(18).toFloat()
        }
        side.addView(label("CLEANTV  /  WEATHER", accent, 12f, true))
        side.addView(label("Brockville", Color.WHITE, 30f, true))
        side.addView(label("ENVIRONMENT AND CLIMATE CHANGE CANADA", muted, 10f, true))
        status = label("Loading latest conditions…", muted, 12f, false)
        status.setPadding(0, dp(10), 0, dp(12))
        side.addView(status)
        val scroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        side.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        side.addView(label("BACK  /  CLOSE", muted, 12f, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })
        val width = minOf(dp(560), (resources.displayMetrics.widthPixels * 0.43f).toInt())
        scrim.addView(side, FrameLayout.LayoutParams(width, -1, Gravity.END))
        setContentView(scrim)
        loadWeatherDetails()
    }

    private fun loadWeatherDetails() {
        Thread {
            try {
                val page = fetchText("https://weather.gc.ca/en/location/index.html?coords=44.597,-75.686")
                val hourlyHtml = fetchRaw("https://weather.gc.ca/en/forecast/hourly/index.html?coords=44.597,-75.686")
                val current = Regex("Temperature\\s*:\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*°\\s*C", RegexOption.IGNORE_CASE)
                    .find(page)?.groupValues?.get(1) ?: "--"
                val humidity = Regex("Humidity\\s*:\\s*(\\d+)%", RegexOption.IGNORE_CASE).find(page)?.groupValues?.get(1) ?: "--"
                val dew = Regex("Dew point:\\s*([+-]?\\d+(?:\\.\\d+)?)°", RegexOption.IGNORE_CASE).find(page)?.groupValues?.get(1) ?: "--"
                val wind = Regex("Wind:\\s*([A-Z]{1,3}\\s*\\([^)]*\\)\\s*\\d+(?:\\s*gusts\\s*\\d+)?\\s*km/h)", RegexOption.IGNORE_CASE)
                    .find(page)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ") ?: "Unavailable"
                val forecast = page.substringAfter("Detailed Forecast", "")
                val dayPattern = Regex("(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s*\\([^)]*\\),?\\s*\\d{1,2}\\s+[A-Za-z]+\\s+([+-]?\\d+)°C\\s+([^\\n]+)", RegexOption.IGNORE_CASE)
                val days = dayPattern.findAll(forecast).take(5).map { it.groupValues[1] to it.groupValues[2].trim() }.toList()
                val hourlyRows = parseHourlyRows(hourlyHtml).take(8)
                runOnUiThread {
                    status.text = "Updated " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    addSection("CURRENT CONDITIONS")
                    addMetric("Temperature", current + "°C")
                    addMetric("Humidity", humidity + "%")
                    addMetric("Dew point", dew + "°C")
                    addMetric("Wind", wind)
                    addSection("HOURLY FORECAST")
                    if (hourlyRows.isEmpty()) addMetric("Hourly data", "Not available right now")
                    hourlyRows.forEach { item -> addMetric(item[0], item[1] + "  " + item[2] + "  ·  " + item[3] + " rain  ·  " + item[4] + " km/h") }
                    addSection("5-DAY OUTLOOK")
                    if (days.isEmpty()) addMetric("Forecast", "Not available right now")
                    days.forEachIndexed { index, item -> addMetric("DAY " + (index + 1), item.first + "°  ·  " + item.second) }
                    // Give the TV remote an obvious starting selection when the panel opens.
                    content.post { content.getChildAt(1)?.requestFocus() }
                }
            } catch (e: Exception) {
                android.util.Log.e("CleanTVWeather", "Expanded forecast failed", e)
                runOnUiThread { status.text = "Weather details unavailable. Check connection and try again." }
            }
        }.start()
    }

    private fun parseHourlyRows(html: String): List<List<String>> {
        val rows = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>").findAll(html)
        return rows.mapNotNull { row ->
            val cells = Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>").findAll(row.groupValues[1])
                .map { cell ->
                    @Suppress("DEPRECATION")
                    android.text.Html.fromHtml(cell.groupValues[1]).toString()
                        .replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()
                }.toList()
            if (cells.size < 6 || !Regex("\\d{2}:\\d{2}").matches(cells[0])) null
            else listOf(cells[0], cells[1] + "°", cells[2], cells[3] + "%", cells[5])
        }.toList()
    }

    private fun fetchRaw(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "CleanTVLauncher/2.0 (Android TV)")
            setRequestProperty("Accept", "text/html")
        }
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    }

    private fun fetchText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "CleanTVLauncher/2.0 (Android TV)")
            setRequestProperty("Accept", "text/html")
        }
        return try {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(connection.inputStream.bufferedReader().use { it.readText() }).toString()
                .replace('\u00A0', ' ')
                .replace(Regex("[ \\t]+"), " ")
        } finally { connection.disconnect() }
    }

    private fun addSection(title: String) {
        content.addView(label(title, accent, 11f, true).apply { setPadding(0, dp(13), 0, dp(7)) })
    }

    private fun addMetric(name: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = metricBackground(false)
            setOnFocusChangeListener { view, hasFocus ->
                view.background = metricBackground(hasFocus)
                view.scaleX = if (hasFocus) 1.025f else 1f
                view.scaleY = if (hasFocus) 1.025f else 1f
                view.elevation = if (hasFocus) dp(8).toFloat() else 0f
            }
        }
        row.addView(label(name.uppercase(Locale.getDefault()), muted, 10f, true))
        row.addView(label(value, Color.WHITE, 15f, false).apply { setPadding(0, dp(3), 0, 0) })
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }

    private fun metricBackground(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(if (focused) Color.rgb(63, 29, 39) else Color.rgb(29, 35, 45))
        setStroke(
            dp(if (focused) 2 else 1),
            if (focused) accent else Color.rgb(55, 64, 78)
        )
    }

    private fun label(value: String, color: Int, size: Float, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Back closes the overlay")
    override fun onBackPressed() { finish() }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
