package ca.cleantvlauncher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
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
 */
class MainActivity : Activity() {

    private lateinit var dock: LinearLayout
    private lateinit var clock: TextView
    private lateinit var dateView: TextView
    private lateinit var heroBanner: ImageView
    private lateinit var heroLabel: TextView
    private lateinit var wallpaperView: ImageView
    private lateinit var wallpaperDim: View
    // Guard against the same app being launched twice from two handlers
    // firing for one centre press (tile key handler + tile click + Activity).
    private var lastLaunchAt = 0L
    private var lastLaunchPkg = ""
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
            dateView.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now)
            clockHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dock = findViewById(R.id.app_dock)
        clock = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        heroBanner = findViewById(R.id.hero_banner)
        heroLabel = findViewById(R.id.hero_label)
        wallpaperView = findViewById(R.id.wallpaper)
        wallpaperDim = findViewById(R.id.wallpaper_dim)
        loadDock()
        maybeRequestStoragePermission()
        loadWallpaper()
        clockHandler.post(clockTick)
    }

    /** Single launch path, with a 500 ms guard against double-firing. */
    private fun launchApp(app: AppEntry) {
        val now = SystemClock.uptimeMillis()
        if (app.packageName == lastLaunchPkg && now - lastLaunchAt < 500) return
        lastLaunchAt = now
        lastLaunchPkg = app.packageName
        try {
            startActivity(app.launch)
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
        // Refresh in case apps changed while we were away.
        loadDock()
        loadWallpaper()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    /**
     * (Re)builds the dock: one focusable tile per favourite, then the app
     * drawer tile, then Settings last.
     */
    private fun loadDock() {
        val allApps = loadLaunchableApps(packageManager, packageName)
        val apps = loadFavouritePackages(this).mapNotNull { pkg ->
            allApps.find { it.packageName == pkg }
        }
        // v2.7: the two apps get wide banner tiles; the grid and Settings
        // tiles stay small square icons. Banner width is the screen width
        // minus 32dp of side breathing room, minus the pill's 24dp of
        // padding, minus 6dp of margin per tile, minus the two small tiles,
        // split between the banners and capped at 176dp.
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val smallTileDp = 64
        val totalTiles = apps.size + 2
        val bannerWidthDp = minOf(
            176,
            ((screenWidthDp - 32 - 24 - totalTiles * 6 - 2 * smallTileDp) /
                apps.size.coerceAtLeast(1)).toInt()
        ).coerceAtLeast(120)
        dock.removeAllViews()
        for (app in apps) {
            dock.addView(makeBannerDockTile(app, bannerWidthDp))
        }
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
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ),
                smallTileDp
            )
        )
        if (apps.isNotEmpty()) {
            updateHero(apps[0])
            dock.post { dock.getChildAt(0)?.requestFocus() }
        }
    }

    /** One focusable dock tile for an app (or a special tile). */
    private fun makeDockTile(app: AppEntry, tileWidthDp: Int): View {
        val tile = LayoutInflater.from(this).inflate(R.layout.item_app, dock, false)
        val iconView = tile.findViewById<ImageView>(R.id.app_icon)
        iconView.setImageDrawable(app.icon)
        val iconSize = dp((tileWidthDp - 8).coerceAtLeast(36))
        iconView.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        tile.contentDescription = app.label
        tile.tag = app
        tile.isFocusable = true
        tile.isFocusableInTouchMode = true
        val params = LinearLayout.LayoutParams(dp(tileWidthDp), dp(64))
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
            val s = if (hasFocus) 1.25f else 1.0f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
            // Apple TV style lift: the focused tile rises above its
            // neighbours (elevation changes draw order, not layout).
            v.elevation = if (hasFocus) dp(6).toFloat() else 0f
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
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
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
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            wallpaperView.visibility = View.GONE
            wallpaperDim.visibility = View.GONE
            return
        }
        val dirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        )
        for (dir in dirs) {
            for (name in listOf("cleantv-wallpaper.jpg", "cleantv-wallpaper.png")) {
                val f = File(dir, name)
                if (f.exists()) {
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    if (bmp != null) {
                        wallpaperView.setImageBitmap(bmp)
                        wallpaperView.visibility = View.VISIBLE
                        wallpaperDim.visibility = View.VISIBLE
                        return
                    }
                }
            }
        }
        wallpaperView.visibility = View.GONE
        wallpaperDim.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockTick)
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
    val launch: Intent
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
            out.add(AppEntry(label, ri.activityInfo.packageName, icon, banner, launch))
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
    "ar.tvplayer.tv",        // TiviMate
    "com.android.vending"    // Google Play Store
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
