package ca.cleantvlauncher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Clean TV v2 — tvOS-style apps-only Android TV launcher.
 * Full-screen wallpaper, big clock + date top-left, a hero area showing the
 * focused app's banner art, and a bottom-centred dock of app icons.
 * No rows, no recommendations, no ads, no network use at all.
 *
 * Kept from v1.3: <queries> for LAUNCHER + LEANBACK_LAUNCHER, disabled apps
 * skipped, live clock, Settings tile, gunmetal gradient wallpaper + custom
 * cleantv-wallpaper.jpg/.png.
 *
 * v2.3: the dock is a plain horizontal LinearLayout of real focusable tiles
 * (inside a HorizontalScrollView), NOT a GridView — a GridView swallows a
 * short DPAD_CENTER press, which is why v2.0–v2.2 all needed a long press.
 *
 * v2.5: an app drawer. A grid tile at the end of the dock (then Settings,
 * like the launcher in Brian's video) opens AppDrawerActivity, a
 * full-screen grid of every app. The drawer is plain LinearLayout rows in
 * a ScrollView — never a GridView, for the same centre-press reason, and
 * never a RecyclerView either: adding that dependency failed runs 31–38
 * at the build step, so the drawer was rewritten dependency-free.
 *
 * v2.6: the dock only shows Brian's favourites (TiviMate, Play Store,
 * Settings, in that order). Everything else lives in the drawer.
 *
 * v2.7: the dock sits in a frosted-glass pill (like the launcher in
 * Brian's video) and the app tiles are wide banner tiles; the grid and
 * Settings tiles stay small square icons at the end of the pill.
 *
 * v2.8: darker pill, the dock row sits in the vertical centre of the
 * screen, tiles grow Apple TV style when focused (1.25x + a lift), and
 * the favourites are editable on the TV: the drawer has an
 * "Edit favourites" tile that opens FavoritesActivity, where Brian can
 * star apps into the dock and reorder them with up/down buttons.
 * The dock order is stored in SharedPreferences; the clock stays top-left.
 *
 * v2.9: the v2.8 centred dock overlapped the hero — the screen is only
 * 540dp tall (density 2.0), so the 230dp hero ran to ~70% of the screen.
 * Hero shrunk to 128dp and the dock nudged down, so the two clear.
 *
 * v2.10: the dock still read as top-half, so it moved further down — its
 * centre now sits just below the middle of the screen (~320dp of 540).
 *
 * v2.11: the favourites editor opened with nothing focused, so centre
 * presses went nowhere and no edit ever landed (the dock never moved).
 * The editor now focuses the first star on open.
 */
class MainActivity : Activity() {

    private lateinit var dock: LinearLayout
    private lateinit var clock: TextView
    private lateinit var dateView: TextView
    private lateinit var heroBanner: ImageView
    private lateinit var heroLabel: TextView
    private lateinit var heroCard: View
    private lateinit var wallpaperView: ImageView
    private lateinit var wallpaperDim: View
    private lateinit var weatherIcon: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherCondition: TextView
    private lateinit var weatherHighLow: TextView
    // Guard against the same app being launched twice from two handlers
    // firing for one centre press (tile key handler + tile click + Activity).
    private var lastLaunchAt = 0L
    private var lastLaunchPkg = ""
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            applyClockAndDateOptions()
            clockHandler.postDelayed(this, 30_000)
        }
    }
    private val weatherTick = object : Runnable {
        override fun run() {
            loadWeather()
            clockHandler.postDelayed(this, 30 * 60 * 1000L)
        }
    }

    private fun applyClockAndDateOptions() {
        val prefs = getSharedPreferences("clean_tv_prefs", MODE_PRIVATE)
        val now = Date()
        clock.visibility = if (prefs.getBoolean("show_clock", true)) View.VISIBLE else View.GONE
        dateView.visibility = if (prefs.getBoolean("show_date", true)) View.VISIBLE else View.GONE
        val timePattern = if (prefs.getString("clock_format", "12h") == "24h") "HH:mm" else "h:mm a"
        val datePattern = when (prefs.getString("date_format", "full")) {
            "short" -> "MMM d, yyyy"
            "weekday" -> "EEE, MMM d"
            else -> "EEEE, MMM d"
        }
        clock.text = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
        dateView.text = SimpleDateFormat(datePattern, Locale.getDefault()).format(now)
        val clockSize = when (prefs.getString("clock_size", "standard")) {
            "compact" -> 36f
            "large" -> 56f
            else -> 48f
        }
        val dateSize = when (prefs.getString("clock_size", "standard")) {
            "compact" -> 14f
            "large" -> 21f
            else -> 18f
        }
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSize)
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSize)
    }

    private fun applyHomeLayoutOptions() {
        val prefs = getSharedPreferences("clean_tv_prefs", MODE_PRIVATE)
        heroCard.visibility = if (prefs.getBoolean("hero_visible", true)) View.VISIBLE else View.GONE
        val heroSize = prefs.getString("hero_size", "standard") ?: "standard"
        val (widthDp, heightDp) = when (heroSize) {
            "compact" -> 420 to 150
            "large" -> 620 to 210
            else -> 520 to 180
        }
        heroCard.layoutParams = (heroCard.layoutParams as android.widget.RelativeLayout.LayoutParams).apply {
            width = dp(widthDp)
            height = dp(heightDp)
        }
        heroBanner.layoutParams = (heroBanner.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(widthDp - 60)
            height = dp(heightDp - 68)
        }
        positionDockConsistently()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dock = findViewById(R.id.app_dock)
        clock = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        heroBanner = findViewById(R.id.hero_banner)
        heroLabel = findViewById(R.id.hero_label)
        heroCard = findViewById(R.id.hero_card)
        // Match the industrial CleanTV typography across the home screen.
        clock.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        clock.letterSpacing = 0.015f
        dateView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        dateView.letterSpacing = 0.025f
        heroLabel.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        heroLabel.letterSpacing = 0.02f
        wallpaperView = findViewById(R.id.wallpaper)
        wallpaperDim = findViewById(R.id.wallpaper_dim)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherTemp = findViewById(R.id.weather_temp)
        weatherCondition = findViewById(R.id.weather_condition)
        weatherHighLow = findViewById(R.id.weather_highlow)
        loadDock()
        maybeRequestStoragePermission()
        loadWallpaper()
        applyHomeLayoutOptions()
        positionDockConsistently()
        clockHandler.post(clockTick)
        clockHandler.post(weatherTick)
    }

    /** Single launch path, with a 500 ms guard against double-firing. */
    private fun launchApp(app: AppEntry) {
        val now = SystemClock.uptimeMillis()
        if (app.packageName == lastLaunchPkg && now - lastLaunchAt < 500) return
        lastLaunchAt = now
        lastLaunchPkg = app.packageName
        try {
            startActivity(app.launch)
            recordAppLaunch(this, app.packageName)
        } catch (e: Exception) {
            // Never let a bad launch intent kill the launcher.
        }
    }

    /** Backup only: the focused tile's own key/click handlers are primary. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val focused = currentFocus
            if (focused != null && focused.tag is AppEntry) {
                launchApp(focused.tag as AppEntry)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        // Apply clock/date preferences immediately when returning from Settings.
        // The periodic tick remains as a fallback for keeping the clock current.
        applyClockAndDateOptions()
        applyHomeLayoutOptions()
        // Refresh in case apps changed while we were away.
        loadDock()
        loadWallpaper()
    }

    /** Fetch Brockville's current conditions and today's high/low from Open-Meteo. */
    private fun loadWeather() {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=44.5895&longitude=-75.6842&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=America%2FToronto&forecast_days=1"
                )
                connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                val temperature = current.getDouble("temperature_2m").toInt()
                val code = current.getInt("weather_code")
                val high = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
                val low = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()
                val condition = weatherDescription(code)
                val icon = weatherSymbol(code)
                runOnUiThread {
                    weatherIcon.text = icon
                    weatherTemp.text = "${temperature}°C"
                    weatherCondition.text = condition
                    weatherHighLow.text = "H ${high}°   L ${low}°"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    weatherIcon.text = "☁"
                    weatherTemp.text = "--°"
                    weatherCondition.text = "Weather unavailable"
                    weatherHighLow.text = "Check internet connection"
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Current conditions"
    }

    private fun weatherSymbol(code: Int): String = when (code) {
        0, 1 -> "☀"
        2, 3 -> "☁"
        45, 48 -> "≋"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "☁"
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    /**
     * Keep the dock at a stable point on the screen even if Android TV
     * reports a different usable display height during a cold boot.
     * Position its centre at 80% of the content height, matching the
     * intended slightly-below-middle composition.
     */
    private fun positionDockConsistently() {
        val content = findViewById<View>(android.R.id.content)
        val dockScroll = findViewById<View>(R.id.dock_scroll)
        content.post {
            val availableHeight = content.height
            if (availableHeight <= 0 || dockScroll.height <= 0) return@post
            val params = dockScroll.layoutParams as? android.widget.RelativeLayout.LayoutParams
                ?: return@post
            params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            val dockPosition = when (getSharedPreferences("clean_tv_prefs", MODE_PRIVATE).getString("dock_position", "standard")) {\n                "high" -> 0.72f\n                "low" -> 0.88f\n                else -> 0.80f\n            }\n            params.topMargin = (availableHeight * dockPosition - dockScroll.height / 2f)
                .toInt().coerceAtLeast(dp(8))
            params.bottomMargin = 0
            dockScroll.layoutParams = params
        }
    }


    /**
     * (Re)builds the dock: one focusable tile per favourite, then the app
     * drawer tile, then Settings last.
     */
    private fun loadDock() {
        val allApps = loadLaunchableApps(packageManager, packageName)
        val apps = loadFavouritePackages(this).mapNotNull { pkg ->
            allApps.find { it.packageName == pkg }
        }
        // Every dock item uses the same square tile with a readable label.
        val smallTileDp = 78
        dock.removeAllViews()
        for (app in apps) {
            dock.addView(makeDockTile(app, smallTileDp))
        }
        // Dedicated Xbox input tile: use Android TV's passthrough URI
        // directly, bypassing the TV's unreliable INPUT-key/menu path.
        val xboxIcon: Drawable = getDrawable(R.drawable.ic_xbox_tile)!!
        dock.addView(
            makeDockTile(
                AppEntry(
                    "Xbox",
                    "ca.cleantvlauncher.xbox-input",
                    xboxIcon,
                    null,
                    Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(
                            "content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW1"
                        )
                    }
                ),
                smallTileDp
            )
        )
        // Quick Control Panel: translucent right-side overlay.
        val controlIcon: Drawable = getDrawable(R.drawable.ic_control_center)!!
        dock.addView(
            makeDockTile(
                AppEntry(
                    "Control Center",
                    "ca.cleantvlauncher.control-center",
                    controlIcon,
                    null,
                    Intent(this, ControlCenterActivity::class.java)
                ),
                smallTileDp
            )
        )
        // App drawer tile (grid icon), then Settings last.
        val drawerIcon: Drawable = getDrawable(R.drawable.ic_apps_grid)!!
        dock.addView(
            makeDockTile(
                AppEntry(
                    "All Apps",
                    "ca.cleantvlauncher.drawer",
                    drawerIcon,
                    null,
                    Intent(this, AppDrawerActivity::class.java)
                ),
                smallTileDp
            )
        )
        val settingsIcon: Drawable = getDrawable(android.R.drawable.ic_menu_preferences)
            ?: getDrawable(android.R.drawable.ic_menu_manage)!!
        dock.addView(
            makeDockTile(
                AppEntry(
                    "Settings",
                    "com.android.tv.settings",
                    settingsIcon,
                    null,
                    Intent(this, CleanTVSettingsActivity::class.java)
                ),
                smallTileDp
            )
        )
        if (apps.isNotEmpty()) {
            updateHero(apps[0])
            dock.post { dock.getChildAt(0)?.requestFocus() }
        }
    }

    private fun dockAccentColor(): Int =
        when (getSharedPreferences("clean_tv_prefs", MODE_PRIVATE).getString("theme_accent", "crimson")) {
            "blue" -> Color.rgb(55, 145, 220)
            "green" -> Color.rgb(55, 175, 125)
            else -> Color.rgb(205, 48, 62)
        }

    private fun dockTileBackground(focused: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            setColor(if (focused) Color.rgb(42, 34, 43) else Color.rgb(35, 42, 52))
            setStroke(dp(if (focused) 2 else 1),
                if (focused) dockAccentColor() else Color.rgb(75, 88, 105))
        }

    /** One focusable dock tile for an app (or a special tile). */
    private fun makeDockTile(app: AppEntry, tileWidthDp: Int): View {
        val tile = LayoutInflater.from(this).inflate(R.layout.item_app, dock, false)
        tile.background = dockTileBackground(false)
        val iconView = tile.findViewById<ImageView>(R.id.app_icon)
        iconView.setImageDrawable(app.icon)
        val iconSize = dp(38)
        iconView.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        val labelView = tile.findViewById<TextView>(R.id.app_label)
        labelView.text = app.label
        labelView.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        labelView.textSize = 10f
        labelView.maxLines = 1
        labelView.isSingleLine = true
        labelView.ellipsize = android.text.TextUtils.TruncateAt.END
        tile.contentDescription = app.label
        tile.tag = app
        tile.isFocusable = true
        tile.isFocusableInTouchMode = true
        val params = LinearLayout.LayoutParams(dp(tileWidthDp), dp(78))
        params.marginStart = dp(3)
        params.marginEnd = dp(3)
        tile.layoutParams = params
        // A plain tile (no AdapterView above it) gets the centre key
        // directly, so both the key-down handler and the click handler
        // work for a short tap; launchApp()'s guard dedupes them.
        tile.setOnClickListener { launchApp(app) }
        tile.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER) &&
                event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
            ) {
                launchApp(app)
                true
            } else {
                false
            }
        }
        // Tile lifts slightly on focus, over the red focus ring, and the
        // hero follows focus.
        tile.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.12f else 1.0f
            v.animate().scaleX(s).scaleY(s).setDuration(190).start()
            // Apple TV style lift: the focused tile rises above its
            // neighbours (elevation changes draw order, not layout).
            v.elevation = if (hasFocus) dp(6).toFloat() else 0f
            v.background = dockTileBackground(hasFocus)
            if (hasFocus) updateHero(app)
        }
        return tile
    }

    /** One wide banner dock tile for a real app (v2.7 glass pill). */
    private fun makeBannerDockTile(app: AppEntry, bannerWidthDp: Int): View {
        val tile = LayoutInflater.from(this).inflate(R.layout.item_app_banner, dock, false)
        val bannerView = tile.findViewById<ImageView>(R.id.app_banner)
        bannerView.setImageDrawable(app.banner ?: app.icon)
        tile.contentDescription = app.label
        tile.tag = app
        tile.isFocusable = true
        tile.isFocusableInTouchMode = true
        // Trims the banner's square corners to the focus ring's curve.
        tile.clipToOutline = true
        val params = LinearLayout.LayoutParams(dp(bannerWidthDp), dp(64))
        params.marginStart = dp(3)
        params.marginEnd = dp(3)
        tile.layoutParams = params
        // Same wiring as a square tile: a plain tile (no AdapterView above
        // it) gets the centre key directly, so both the key-down handler
        // and the click handler work for a short tap; launchApp()'s guard
        // dedupes them.
        tile.setOnClickListener { launchApp(app) }
        tile.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER) &&
                event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
            ) {
                launchApp(app)
                true
            } else {
                false
            }
        }
        tile.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.25f else 1.0f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
            // Apple TV style lift: the focused tile rises above its
            // neighbours (elevation changes draw order, not layout).
            v.elevation = if (hasFocus) dp(6).toFloat() else 0f
            if (hasFocus) updateHero(app)
        }
        return tile
    }

    private fun updateHero(app: AppEntry) {
        heroBanner.setImageDrawable(app.banner ?: app.icon)
        heroLabel.text = app.label
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadWallpaper()
    }

    private fun maybeRequestStoragePermission() {
        // Runtime storage permissions were introduced in Android 6.0 (API 23).
        // On API 21–22, manifest permissions are granted at install time.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            checkPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                android.os.Process.myPid(),
                android.os.Process.myUid()
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    /**
     * Custom wallpaper: if a file named cleantv-wallpaper.jpg (or .png) sits in
     * Downloads or Pictures, it becomes the background. Otherwise the built-in
     * gunmetal gradient shows. Checked on every resume, so swapping the file
     * and pressing Home picks it up.
     */
    private fun loadWallpaper() {
        // Preferred path: image copied into app-private storage by Settings.
        // This avoids relying on Android TV file-provider URI permissions.
        val prefs = getSharedPreferences("clean_tv_prefs", MODE_PRIVATE)
        val savedFile = prefs.getString("wallpaper_file_path", null)
        if (!savedFile.isNullOrBlank()) {
            try {
                val file = File(savedFile)
                val bmp = if (file.exists()) decodeWallpaperFile(file) else null
                if (bmp != null) {
                    applyWallpaperBitmap(bmp)
                    wallpaperView.visibility = View.VISIBLE
                    wallpaperDim.visibility = View.VISIBLE
                    return
                }
            } catch (_: Exception) {
                // Fall through to older saved URI and legacy wallpaper locations.
            }
        }

        // Backward compatibility: URI wallpapers saved by earlier v2 builds.
        val savedUri = prefs.getString("wallpaper_uri", null)
        if (!savedUri.isNullOrBlank()) {
            try {
                val uri = android.net.Uri.parse(savedUri)
                val bmp = decodeWallpaper(uri)
                if (bmp != null) {
                    applyWallpaperBitmap(bmp)
                    wallpaperView.visibility = View.VISIBLE
                    wallpaperDim.visibility = View.VISIBLE
                    return
                }
            } catch (_: Exception) {
                // If the image was deleted or access revoked, fall back safely.
            }
        }

        // Backward-compatible fallback for the original named wallpaper files.
        if (checkPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                android.os.Process.myPid(),
                android.os.Process.myUid()
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val dirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            )
            for (dir in dirs) {
                for (name in listOf("cleantv-wallpaper.jpg", "cleantv-wallpaper.png")) {
                    val f = File(dir, name)
                    if (f.exists()) {
                        val bmp = decodeWallpaperFile(f)
                        if (bmp != null) {
                            applyWallpaperBitmap(bmp)
                            wallpaperView.visibility = View.VISIBLE
                            wallpaperDim.visibility = View.VISIBLE
                            return
                        }
                    }
                }
            }
        }
        wallpaperView.visibility = View.GONE
        wallpaperDim.visibility = View.GONE
    }

    /** Apply the saved wallpaper display mode consistently to every source. */
    private fun applyWallpaperBitmap(bitmap: Bitmap) {
        val mode = getSharedPreferences("clean_tv_prefs", MODE_PRIVATE)
            .getString("wallpaper_scale_mode", "fill")
        wallpaperView.scaleType = when (mode) {
            "fit" -> ImageView.ScaleType.FIT_CENTER
            "stretch" -> ImageView.ScaleType.FIT_XY
            "center" -> ImageView.ScaleType.CENTER
            else -> ImageView.ScaleType.CENTER_CROP
        }
        wallpaperView.setImageBitmap(bitmap)
    }

    /**
     * Decode wallpaper images near the TV's display resolution instead of
     * loading the original camera-sized file into memory. A 4K phone photo
     * can otherwise consume tens of megabytes just for one bitmap.
     */
    private fun wallpaperDecodeOptions(sourceWidth: Int, sourceHeight: Int): BitmapFactory.Options {
        val targetWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val targetHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth &&
            sourceHeight / (sample * 2) >= targetHeight
        ) {
            sample *= 2
        }
        return BitmapFactory.Options().apply { inSampleSize = sample }
    }

    private fun decodeWallpaper(uri: android.net.Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) return null
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = wallpaperDecodeOptions(bounds.outWidth, bounds.outHeight)
        return contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) null else BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun decodeWallpaperFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(
            file.absolutePath,
            wallpaperDecodeOptions(bounds.outWidth, bounds.outHeight)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.removeCallbacks(weatherTick)
    }

    override fun onBackPressed() {
        // Home screen: Back does nothing.
    }
}

/** One launchable app (or special tile) in the dock / drawer. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val banner: Drawable?,
    val launch: Intent,
    val hasCustomIcon: Boolean = true
)

/**
 * The launchable apps on the device, A–Z, skipping ourselves and disabled
 * apps. Shared by the home dock and the app drawer.
 */
fun loadLaunchableApps(pm: PackageManager, selfPackage: String): List<AppEntry> {
    val seen = HashSet<String>()
    val out = ArrayList<AppEntry>()
    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    )
    for (intent in intents) {
        val found: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        for (ri in found) {
            val key = ri.activityInfo.packageName + "/" + ri.activityInfo.name
            if (!seen.add(key)) continue
            if (ri.activityInfo.packageName == selfPackage) continue
            // Skip apps disabled by the user (e.g. YouTube Music from debloat batch 2).
            if (!ri.activityInfo.applicationInfo.enabled) continue
            if (!ri.activityInfo.enabled) continue
            val label = ri.loadLabel(pm).toString()
            val icon = ri.activityInfo.loadIcon(pm)
            // Treat icons supplied by the app package as custom artwork.
            // Framework/shared generic icons get a CleanTV fallback in the drawer.
            val iconResource = ri.activityInfo.getIconResource()
            val hasCustomIcon = if (iconResource == 0) {
                false
            } else {
                try {
                    pm.getResourcesForApplication(ri.activityInfo.packageName)
                        .getResourcePackageName(iconResource) == ri.activityInfo.packageName
                } catch (_: Exception) {
                    false
                }
            }
            // Banner art for the hero area and the drawer; not every app has one.
            val banner: Drawable? = try {
                ri.activityInfo.loadBanner(pm)
            } catch (e: Exception) {
                null
            }
            // Use the system's own launch intent for the package: it is
            // the correct Leanback/launcher intent for TV apps. Fall back
            // to an explicit intent if the system has none.
            val launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            out.add(AppEntry(label, ri.activityInfo.packageName, icon, banner, launch, hasCustomIcon))
        }
    }
    out.sortBy { it.label.lowercase(Locale.getDefault()) }
    return out
}

/**
 * The dock favourites, in dock order, stored in SharedPreferences so
 * Brian can edit them on the TV (FavoritesActivity). A plain comma-joined
 * string, because a string set would not keep the order.
 */
private const val PREFS_NAME = "clean_tv_prefs"
private const val PREF_FAVOURITES = "favourites"
private val DEFAULT_FAVOURITES = listOf(
    "ar.tvplayer.tv",                    // TiviMate
    "com.amazon.amazonvideo.livingroom"   // Prime Video
)

fun loadFavouritePackages(context: Context): List<String> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_FAVOURITES, null)
        ?: return DEFAULT_FAVOURITES
    val list = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return list.ifEmpty { DEFAULT_FAVOURITES }
}

fun saveFavouritePackages(context: Context, packages: List<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_FAVOURITES, packages.joinToString(","))
        .apply()
}

/** Hidden drawer apps are stored separately; hiding never uninstalls an app. */
private const val PREF_HIDDEN_APPS = "hidden_apps"

fun loadHiddenPackages(context: Context): Set<String> =
    context.getSharedPreferences("clean_tv_prefs", Context.MODE_PRIVATE)
        .getString(PREF_HIDDEN_APPS, "")
        .orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

fun saveHiddenPackages(context: Context, packages: Set<String>) {
    context.getSharedPreferences("clean_tv_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_HIDDEN_APPS, packages.sorted().joinToString(","))
        .apply()
}

/** Persist a small, most-recent-first launch history for the App Drawer. */
private const val PREF_RECENT_APPS = "recent_apps"

fun loadRecentPackages(context: Context): List<String> =
    context.getSharedPreferences("clean_tv_prefs", Context.MODE_PRIVATE)
        .getString(PREF_RECENT_APPS, "")
        .orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

fun recordAppLaunch(context: Context, packageName: String) {
    if (packageName.startsWith("ca.cleantvlauncher")) return
    val updated = (listOf(packageName) + loadRecentPackages(context)
        .filterNot { it == packageName }).take(12)
    context.getSharedPreferences("clean_tv_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_RECENT_APPS, updated.joinToString(","))
        .apply()
}
