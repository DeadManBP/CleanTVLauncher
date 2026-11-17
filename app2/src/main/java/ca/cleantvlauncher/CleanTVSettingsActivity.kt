package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

/** CleanTV settings with the dark gunmetal and crimson visual language. */
class CleanTVSettingsActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("clean_tv_prefs", MODE_PRIVATE) }

    private val backgroundColor = Color.rgb(11, 13, 18)
    private val panelColor = Color.rgb(23, 27, 36)
    private val panelFocusColor = Color.rgb(62, 22, 31)
    private val accentColor: Int
        get() = when (prefs.getString("theme_accent", "crimson")) {
            "blue" -> Color.rgb(55, 145, 220)
            "green" -> Color.rgb(55, 175, 125)
            else -> Color.rgb(205, 48, 62)
        }
    private val mutedColor = Color.rgb(155, 164, 178)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(42), dp(28), dp(42), dp(36))
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f, dp(22).toFloat(), dp(22).toFloat())
                setColor(panelColor)
                setStroke(dp(1), Color.rgb(68, 45, 53))
            }
            isFocusable = false
        }

        root.addView(TextView(this).apply {
            text = "CLEANTV  /  CONTROL CENTER"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.18f
            setTextColor(accentColor)
            setPadding(0, 0, 0, dp(7))
        })
        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 36f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Make the screen yours. No clutter. No nonsense."
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(mutedColor)
            setPadding(0, 0, 0, dp(22))
        })

        addSection(root, "01  /  WALLPAPER", "Background image and display behaviour")
        val wallpaperMode = prefs.getString("wallpaper_scale_mode", "fill") ?: "fill"
        root.addView(makeButton("Choose wallpaper", "Browse images stored on your TV") { chooseWallpaper() })
        root.addView(makeButton("Fill / crop", "Use the image to fill the screen" + selected(wallpaperMode == "fill")) { setWallpaperMode("fill") })
        root.addView(makeButton("Fit entire image", "Show the complete image, with possible borders" + selected(wallpaperMode == "fit")) { setWallpaperMode("fit") })
        root.addView(makeButton("Stretch", "Stretch the image to the screen edges" + selected(wallpaperMode == "stretch")) { setWallpaperMode("stretch") })
        root.addView(makeButton("Center original size", "Keep the image at its original pixel size" + selected(wallpaperMode == "center")) { setWallpaperMode("center") })
        root.addView(makeButton("Restore default wallpaper", "Return to the built-in CleanTV background") {
            prefs.edit().remove("wallpaper_uri").remove("wallpaper_file_path").apply()
            File(filesDir, "selected-wallpaper").delete()
            setResult(Activity.RESULT_OK)
            recreate()
        })

        addSection(root, "02  /  HOME SCREEN", "Control the hero panel, dock position and header sizing")
        val heroVisible = prefs.getBoolean("hero_visible", true)
        val heroSize = prefs.getString("hero_size", "standard") ?: "standard"
        val dockPosition = prefs.getString("dock_position", "standard") ?: "standard"
        val clockSize = prefs.getString("clock_size", "standard") ?: "standard"
        root.addView(makeButton("Hero panel  /  " + if (heroVisible) "VISIBLE" else "HIDDEN", "Show or hide the selected-app feature panel") {
            prefs.edit().putBoolean("hero_visible", !prefs.getBoolean("hero_visible", true)).apply()
            refreshSettings()
        })
        root.addView(makeButton("Hero size  /  ${heroSize.uppercase(Locale.getDefault())}", "Cycle compact, standard and large panel sizes") {
            val next = when (prefs.getString("hero_size", "standard")) { "compact" -> "standard"; "standard" -> "large"; else -> "compact" }
            prefs.edit().putString("hero_size", next).apply()
            refreshSettings()
        })
        root.addView(makeButton("Dock position  /  ${dockPosition.uppercase(Locale.getDefault())}", "Move the dock higher or lower on the home screen") {
            val next = when (prefs.getString("dock_position", "standard")) { "high" -> "standard"; "standard" -> "low"; else -> "high" }
            prefs.edit().putString("dock_position", next).apply()
            refreshSettings()
        })
        root.addView(makeButton("Clock and date size  /  ${clockSize.uppercase(Locale.getDefault())}", "Cycle compact, standard and large header text") {
            val next = when (prefs.getString("clock_size", "standard")) { "compact" -> "standard"; "standard" -> "large"; else -> "compact" }
            prefs.edit().putString("clock_size", next).apply()
            refreshSettings()
        })

        addSection(root, "03  /  CLOCK & DATE", "Choose what appears in the home screen header")
        val clockOn = prefs.getBoolean("show_clock", true)
        val dateOn = prefs.getBoolean("show_date", true)
        val timeFormat = prefs.getString("clock_format", "12h") ?: "12h"
        val dateFormat = prefs.getString("date_format", "full") ?: "full"

        root.addView(makeButton("Clock  /  " + if (clockOn) "ON" else "OFF", "Toggle the home screen clock") {
            prefs.edit().putBoolean("show_clock", !prefs.getBoolean("show_clock", true)).apply()
            refreshSettings()
        })
        root.addView(makeButton("Date  /  " + if (dateOn) "ON" else "OFF", "Toggle the date independently") {
            prefs.edit().putBoolean("show_date", !prefs.getBoolean("show_date", true)).apply()
            refreshSettings()
        })
        root.addView(makeButton("Time format  /  " + if (timeFormat == "12h") "12-HOUR" else "24-HOUR", "Switch between 12-hour and 24-hour time") {
            val next = if ((prefs.getString("clock_format", "12h") ?: "12h") == "12h") "24h" else "12h"
            prefs.edit().putString("clock_format", next).apply()
            refreshSettings()
        })
        root.addView(makeButton("Date format  /  " + when (dateFormat) {
            "short" -> "MONTH DAY, YEAR"
            "weekday" -> "SHORT WEEKDAY"
            else -> "FULL WEEKDAY"
        }, "Cycle through the available date styles") {
            val next = when (prefs.getString("date_format", "full")) {
                "full" -> "weekday"
                "weekday" -> "short"
                else -> "full"
            }
            prefs.edit().putString("date_format", next).apply()
            refreshSettings()
        })

        addSection(root, "04  /  APPEARANCE", "Tune CleanTV's colours and overlay behaviour")
        val accentName = when (prefs.getString("theme_accent", "crimson")) {
            "blue" -> "ICE BLUE"
            "green" -> "EMERALD"
            else -> "CRIMSON"
        }
        root.addView(makeButton("Accent colour  /  $accentName", "Cycle crimson, ice blue and emerald") {
            val next = when (prefs.getString("theme_accent", "crimson")) {
                "crimson" -> "blue"
                "blue" -> "green"
                else -> "crimson"
            }
            prefs.edit().putString("theme_accent", next).apply()
            refreshSettings()
        })

        val dimAmount = prefs.getFloat("drawer_dim_amount", 0.62f)
        val dimName = when {
            dimAmount < 0.5f -> "LIGHT"
            dimAmount > 0.7f -> "HEAVY"
            else -> "BALANCED"
        }
        root.addView(makeButton("Drawer background dim  /  $dimName", "Cycle light, balanced and heavy background dimming") {
            val current = prefs.getFloat("drawer_dim_amount", 0.62f)
            val next = when {
                current < 0.5f -> 0.62f
                current < 0.7f -> 0.78f
                else -> 0.42f
            }
            prefs.edit().putFloat("drawer_dim_amount", next).apply()
            refreshSettings()
        })

        val panelAlpha = prefs.getInt("drawer_panel_alpha", 94)
        val panelName = when {
            panelAlpha <= 88 -> "MORE TRANSPARENT"
            panelAlpha >= 98 -> "SOLID"
            else -> "BALANCED"
        }
        root.addView(makeButton("Drawer panel  /  $panelName", "Cycle panel transparency from glassy to solid") {
            val current = prefs.getInt("drawer_panel_alpha", 94)
            val next = when {
                current <= 88 -> 94
                current < 98 -> 100
                else -> 86
            }
            prefs.edit().putInt("drawer_panel_alpha", next).apply()
            refreshSettings()
        })

        addSection(root, "05  /  APP MANAGEMENT", "Choose what appears and arrange your home dock")
        root.addView(makeButton("Hide / show apps", "Keep unwanted apps out of the App Drawer without uninstalling") {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        })
        root.addView(makeButton("Rearrange dock tiles", "Add favourites, remove them or move them up and down") {
            startActivity(Intent(this, FavoritesActivity::class.java))
        })

        addSection(root, "06  /  SYSTEM", "Device-level settings")
        root.addView(makeButton("Android TV Settings", "Open the television's system settings") {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                // Some TV builds do not expose the generic settings activity.
            }
        })

        root.addView(TextView(this).apply {
            text = "CLEANTV 2.0   •   BUILT FOR THE BIG SCREEN"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.12f
            setTextColor(Color.rgb(105, 114, 128))
            setPadding(0, dp(22), 0, dp(8))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isFocusableInTouchMode = false
            addView(root)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val scrim = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
        }
        val panelWidth = minOf(dp(820), (resources.displayMetrics.widthPixels * 0.58f).toInt())
        scrim.addView(scroll, android.widget.FrameLayout.LayoutParams(
            panelWidth, android.widget.FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END
        ))
        setContentView(scrim)
        root.post { root.findViewWithTag<View>("first-setting")?.requestFocus() }
    }

    private fun addSection(root: LinearLayout, title: String, subtitle: String) {
        root.addView(View(this).apply {
            setBackgroundColor(Color.rgb(49, 55, 68))
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(2)).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        })
        root.addView(TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.14f
            setTextColor(accentColor)
            setPadding(0, 0, 0, dp(3))
        })
        root.addView(TextView(this).apply {
            text = subtitle
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(mutedColor)
            setPadding(0, 0, 0, dp(10))
        })
    }

    private fun selected(isSelected: Boolean): String = if (isSelected) "   •   ACTIVE" else ""

    private fun makeButton(title: String, description: String, action: () -> Unit): Button =
        Button(this).apply {
            text = "$title\n$description"
            textSize = 17f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.015f
            isAllCaps = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(18), dp(5), dp(18), dp(5))
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(Color.WHITE)
            minHeight = dp(66)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)
            ).apply {
                bottomMargin = dp(9)
            }
            if (title == "Choose wallpaper") tag = "first-setting"

            fun updateSurface(focused: Boolean) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(if (focused) panelFocusColor else panelColor)
                    setStroke(dp(if (focused) 2 else 1), if (focused) accentColor else Color.rgb(48, 56, 69))
                }
                elevation = if (focused) dp(7).toFloat() else 0f
                scaleX = if (focused) 1.025f else 1f
                scaleY = if (focused) 1.025f else 1f
            }

            updateSurface(false)
            setOnFocusChangeListener { _, focused -> updateSurface(focused) }
            setOnClickListener { action() }
        }

    /** Refresh the visible selections without kicking the user back home. */
    private fun refreshSettings() {
        setResult(Activity.RESULT_OK)
        recreate()
    }

    private fun setWallpaperMode(mode: String) {
        prefs.edit().putString("wallpaper_scale_mode", mode).apply()
        refreshSettings()
    }

    private fun chooseWallpaper() {
        startActivityForResult(Intent(this, WallpaperBrowserActivity::class.java), REQUEST_WALLPAPER)
    }

    @Deprecated("Receives result from CleanTV's built-in wallpaper browser")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WALLPAPER && resultCode == RESULT_OK) {
            setResult(Activity.RESULT_OK)
            recreate()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_WALLPAPER = 4102
    }
}
