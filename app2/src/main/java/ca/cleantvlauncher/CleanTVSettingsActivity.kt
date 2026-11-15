package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

/** CleanTV settings and TV-compatible wallpaper import. */
class CleanTVSettingsActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("clean_tv_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(56), dp(28), dp(56), dp(28))
            setBackgroundColor(Color.rgb(15, 16, 20))
            isFocusable = false
        }
        root.addView(TextView(this).apply {
            text = "CleanTV Settings"; textSize = 32f
            setTextColor(Color.WHITE); setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "PERSONALIZE YOUR HOME SCREEN"; textSize = 14f
            setTextColor(Color.rgb(190, 190, 200)); setPadding(0, 0, 0, dp(20))
        })
        root.addView(makeButton("Choose wallpaper") { chooseWallpaper() })
        root.addView(makeButton("Wallpaper display: Fill / crop") { setWallpaperMode("fill") })
        root.addView(makeButton("Wallpaper display: Fit entire image") { setWallpaperMode("fit") })
        root.addView(makeButton("Wallpaper display: Stretch") { setWallpaperMode("stretch") })
        root.addView(makeButton("Wallpaper display: Center original size") { setWallpaperMode("center") })
        root.addView(makeButton("Clock: " + if (prefs.getBoolean("show_clock", true)) "On" else "Off") {
            prefs.edit().putBoolean("show_clock", !prefs.getBoolean("show_clock", true)).apply()
            returnToHome()
        })
        root.addView(makeButton("Date: " + if (prefs.getBoolean("show_date", true)) "On" else "Off") {
            prefs.edit().putBoolean("show_date", !prefs.getBoolean("show_date", true)).apply()
            returnToHome()
        })
        root.addView(makeButton("Time format: " + if (prefs.getString("clock_format", "12h") == "12h") "12-hour" else "24-hour") {
            val next = if (prefs.getString("clock_format", "12h") == "12h") "24h" else "12h"
            prefs.edit().putString("clock_format", next).apply()
            returnToHome()
        })
        root.addView(makeButton("Date format: " + when (prefs.getString("date_format", "full")) {
            "short" -> "Month day, year"
            "weekday" -> "Short weekday"
            else -> "Full weekday"
        }) {
            val next = when (prefs.getString("date_format", "full")) {
                "full" -> "weekday"
                "weekday" -> "short"
                else -> "full"
            }
            prefs.edit().putString("date_format", next).apply()
            returnToHome()
        })
        root.addView(makeButton("Use default CleanTV wallpaper") {
            prefs.edit().remove("wallpaper_uri").remove("wallpaper_file_path").apply()
            File(filesDir, "selected-wallpaper").delete()
            setResult(Activity.RESULT_OK); finish()
        })
        root.addView(makeButton("Open Android TV Settings") {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        })
        root.addView(TextView(this).apply {
            text = "Tip: choose a JPG or PNG image. It will be copied into CleanTV so it remains available."
            textSize = 15f; setTextColor(Color.LTGRAY); setPadding(0, dp(20), 0, 0)
        })
        val scroll = ScrollView(this).apply {
            isFillViewport = true; isFocusable = false; isFocusableInTouchMode = false
            addView(root)
        }
        setContentView(scroll)
        root.post { root.findViewWithTag<View>("first-setting")?.requestFocus() }
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label; textSize = 18f
            isFocusable = true; isFocusableInTouchMode = true
            setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(65, 25, 32))
            layoutParams = LinearLayout.LayoutParams(dp(440), dp(58)).apply { bottomMargin = dp(12) }
            if (label == "Choose wallpaper") tag = "first-setting"
            setOnFocusChangeListener { view, focused ->
                view.setBackgroundColor(if (focused) Color.rgb(190, 42, 55) else Color.rgb(65, 25, 32))
                view.elevation = if (focused) dp(8).toFloat() else 0f
                view.scaleX = if (focused) 1.035f else 1f
                view.scaleY = if (focused) 1.035f else 1f
            }
            setOnClickListener { action() }
        }

    private fun returnToHome() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun setWallpaperMode(mode: String) {
        prefs.edit().putString("wallpaper_scale_mode", mode).apply()
        setResult(Activity.RESULT_OK); finish()
    }

    private fun chooseWallpaper() {
        startActivityForResult(Intent(this, WallpaperBrowserActivity::class.java), REQUEST_WALLPAPER)
    }

    @Deprecated("Receives result from CleanTV's built-in wallpaper browser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WALLPAPER && resultCode == RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_WALLPAPER = 4102 }
}
