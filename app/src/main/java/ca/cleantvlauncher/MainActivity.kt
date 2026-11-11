package ca.cleantvlauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
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
 * skipped, GridView onItemClickListener for D-pad centre (a TV remote's
 * D-pad centre fires GridView's item click, NOT the item view's own click
 * listener — that is why taps did nothing in v1.2), live clock, Settings
 * tile, gunmetal gradient wallpaper + custom cleantv-wallpaper.jpg/.png.
 */
class MainActivity : Activity() {

    private lateinit var grid: GridView
    private lateinit var clock: TextView
    private lateinit var dateView: TextView
    private lateinit var heroBanner: ImageView
    private lateinit var heroLabel: TextView
    private lateinit var wallpaperView: ImageView
    private lateinit var wallpaperDim: View
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
            dateView.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now)
            clockHandler.postDelayed(this, 30_000)
        }
    }

    data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        val banner: Drawable?,
        val launch: Intent
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.app_grid)
        clock = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        heroBanner = findViewById(R.id.hero_banner)
        heroLabel = findViewById(R.id.hero_label)
        wallpaperView = findViewById(R.id.wallpaper)
        wallpaperDim = findViewById(R.id.wallpaper_dim)
        loadDock()
        // D-pad center on a TV remote fires GridView's item click, NOT the
        // item view's own click listener — that is why taps did nothing in v1.2.
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val app = (grid.adapter as AppAdapter).getItem(position)
            startActivity(app.launch)
        }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateHero((grid.adapter as AppAdapter).getItem(position))
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        maybeRequestStoragePermission()
        loadWallpaper()
        clockHandler.post(clockTick)
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case apps changed while we were away.
        loadDock()
        loadWallpaper()
    }

    /** (Re)builds the dock: one row, one column per app, hero on the first app. */
    private fun loadDock() {
        val apps = loadApps()
        grid.numColumns = apps.size.coerceAtLeast(1)
        grid.adapter = AppAdapter(apps)
        if (apps.isNotEmpty()) {
            grid.setSelection(0)
            updateHero(apps[0])
        }
        grid.requestFocus()
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

    private fun loadApps(): List<AppEntry> {
        val pm: PackageManager = packageManager
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
                if (ri.activityInfo.packageName == packageName) continue
                // Skip apps disabled by the user (e.g. YouTube Music from debloat batch 2).
                if (!ri.activityInfo.applicationInfo.enabled) continue
                if (!ri.activityInfo.enabled) continue
                val label = ri.loadLabel(pm).toString()
                val icon = ri.activityInfo.loadIcon(pm)
                // Banner art for the hero area; not every app has one.
                val banner: Drawable? = try {
                    ri.activityInfo.loadBanner(pm)
                } catch (e: Exception) {
                    null
                }
                val launch = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                out.add(AppEntry(label, ri.activityInfo.packageName, icon, banner, launch))
            }
        }
        // Settings tile, so Wi-Fi and the rest stay reachable from the home screen.
        val settingsIcon: Drawable = getDrawable(android.R.drawable.ic_menu_preferences)
            ?: getDrawable(android.R.drawable.ic_menu_manage)!!
        out.add(
            AppEntry(
                "Settings",
                "com.android.tv.settings",
                settingsIcon,
                null,
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        )
        out.sortBy { it.label.lowercase(Locale.getDefault()) }
        return out
    }

    private inner class AppAdapter(private val apps: List<AppEntry>) : BaseAdapter() {
        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): AppEntry = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.icon)
            view.contentDescription = app.label
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setOnClickListener { startActivity(app.launch) }
            // Tile lifts slightly on focus, over the red focus ring, and the
            // hero follows focus — this is the reliable hero trigger.
            view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                val s = if (hasFocus) 1.12f else 1.0f
                view.animate().scaleX(s).scaleY(s).setDuration(120).start()
                if (hasFocus) updateHero(app)
            }
            return view
        }
    }
}
