package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * CleanTV App Drawer 2.0.
 * Category filters, favourites, recent launches and a DPAD-first grid.
 * Kept dependency-free: plain LinearLayouts avoid the TV GridView centre-key
 * issue and the RecyclerView dependency that broke earlier builds.
 */
class AppDrawerActivity : Activity() {

    private enum class Filter(val title: String) {
        ALL("ALL"), FAVOURITES("FAVOURITES"), RECENT("RECENT"),
        MEDIA("MEDIA"), GAMES("GAMES"), TOOLS("TOOLS")
    }

    private var activeFilter = Filter.ALL
    private var lastLaunchAt = 0L
    private var lastLaunchPkg = ""
    private lateinit var allApps: List<AppEntry>
    private lateinit var container: LinearLayout
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var filterRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val prefs = getSharedPreferences("clean_tv_prefs", MODE_PRIVATE)
        window.setDimAmount(prefs.getFloat("drawer_dim_amount", 0.62f).coerceIn(0.25f, 0.85f))
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.activity_drawer)
        findViewById<View>(R.id.drawer_panel).apply {
            background?.alpha = (prefs.getInt("drawer_panel_alpha", 94).coerceIn(80, 100) * 255 / 100)
            layoutParams =
            android.widget.FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        }
        allApps = loadLaunchableApps(packageManager, packageName)
        container = findViewById(R.id.drawer_container)
        title = findViewById(R.id.drawer_title)
        subtitle = findViewById(R.id.drawer_subtitle)
        filterRow = findViewById(R.id.drawer_filters)
        buildFilters()
        renderApps(true)
    }

    private fun buildFilters() {
        filterRow.removeAllViews()
        Filter.values().forEach { filter ->
            val chip = TextView(this).apply {
                text = filter.title
                textSize = 13f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                letterSpacing = 0.045f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(17), dp(10), dp(17), dp(10))
                isFocusable = true
                isFocusableInTouchMode = true
                contentDescription = "Show ${filter.title.lowercase()} apps"
                background = chipBackground(false)
                setOnClickListener {
                    activeFilter = filter
                    buildFilters()
                    renderApps(true)
                    filterRow.getChildAt(Filter.values().indexOf(filter))?.requestFocus()
                }
                onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                    view.animate().scaleX(if (focused) 1.06f else 1f)
                        .scaleY(if (focused) 1.06f else 1f).setDuration(110).start()
                    view.background = chipBackground(focused || filter == activeFilter)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)
            )
            params.marginEnd = dp(9)
            filterRow.addView(chip, params)
        }
        filterRow.getChildAt(Filter.values().indexOf(activeFilter))?.background =
            chipBackground(true)
    }

    private fun accentColor(): Int =
        when (getSharedPreferences("clean_tv_prefs", MODE_PRIVATE).getString("theme_accent", "crimson")) {
            "blue" -> Color.rgb(55, 145, 220)
            "green" -> Color.rgb(55, 175, 125)
            else -> Color.rgb(205, 48, 62)
        }

    private fun chipBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (selected) Color.rgb(35, 42, 55) else Color.rgb(25, 31, 41))
            setStroke(dp(if (selected) 2 else 1),
                if (selected) accentColor() else Color.rgb(91, 105, 122))
        }

    private fun renderApps(focusFirst: Boolean) {
        container.removeAllViews()
        val hidden = loadHiddenPackages(this)
        val visibleApps = allApps.filter { it.packageName !in hidden }
        val favourites = loadFavouritePackages(this).toSet()
        val recent = loadRecentPackages(this)
        val filtered = when (activeFilter) {
            Filter.ALL -> visibleApps
            Filter.FAVOURITES -> visibleApps.filter { it.packageName in favourites }
            Filter.RECENT -> recent.mapNotNull { pkg -> visibleApps.find { it.packageName == pkg } }
            Filter.MEDIA -> visibleApps.filter { categoryFor(it) == Filter.MEDIA }
            Filter.GAMES -> visibleApps.filter { categoryFor(it) == Filter.GAMES }
            Filter.TOOLS -> visibleApps.filter { categoryFor(it) == Filter.TOOLS }
        }

        val entries = ArrayList<AppEntry>()
        if (activeFilter == Filter.ALL || activeFilter == Filter.FAVOURITES) {
            entries.add(AppEntry(
                "Edit favourites", "ca.cleantvlauncher.favourites",
                getDrawable(android.R.drawable.ic_menu_edit)!!, null,
                Intent(this, FavoritesActivity::class.java)
            ))
        }
        entries.addAll(filtered)

        title.text = when (activeFilter) {
            Filter.ALL -> "ALL APPS"
            Filter.FAVOURITES -> "FAVOURITES"
            Filter.RECENT -> "RECENTLY USED"
            else -> activeFilter.title
        }
        subtitle.text = "${filtered.size} apps  •  ${visibleApps.size} visible"
        if (entries.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (activeFilter == Filter.RECENT) "Apps you launch will appear here."
                    else "No apps in this category."
                setTextColor(Color.LTGRAY)
                textSize = 19f
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(36), dp(24), dp(36))
            }
            container.addView(empty)
            return
        }

        val cols = 5
        var firstTile: View? = null
        entries.chunked(cols).forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.forEach { app ->
                val tile = LayoutInflater.from(this)
                    .inflate(R.layout.item_drawer_app, rowLayout, false)
                tile.findViewById<ImageView>(R.id.drawer_app_banner)
                    .setImageDrawable(if (app.hasCustomIcon) app.icon else createFallbackIcon(app.label))
                tile.findViewById<TextView>(R.id.drawer_app_label).apply {
                    text = app.label
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                }
                tile.contentDescription = app.label
                tile.isFocusable = true
                tile.isFocusableInTouchMode = true
                val params = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                params.marginStart = dp(6)
                params.marginEnd = dp(6)
                params.topMargin = dp(7)
                params.bottomMargin = dp(7)
                tile.layoutParams = params
                tile.setOnClickListener { launchApp(app) }
                tile.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == KeyEvent.KEYCODE_ENTER) &&
                        event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
                    ) {
                        launchApp(app)
                        true
                    } else false
                }
                tile.onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                    val scale = if (focused) 1.07f else 1f
                    view.animate().scaleX(scale).scaleY(scale).setDuration(130).start()
                    view.elevation = if (focused) dp(7).toFloat() else 0f
                }
                rowLayout.addView(tile)
                if (firstTile == null && app.packageName != "ca.cleantvlauncher.favourites") {
                    firstTile = tile
                }
            }
            repeat(cols - row.size) {
                rowLayout.addView(View(this), LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ))
            }
            container.addView(rowLayout)
        }
        if (focusFirst) firstTile?.post { firstTile?.requestFocus() }
    }

    /** Draw a consistent CleanTV monogram for apps with generic system icons. */
    private fun createFallbackIcon(label: String): BitmapDrawable {
        val size = dp(96).coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(2f, 2f, size - 2f, size - 2f)
        val radius = size * 0.22f

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(27, 34, 45)
        }
        canvas.drawRoundRect(bounds, radius, radius, background)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor()
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        canvas.drawRoundRect(bounds, radius, radius, border)

        val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.48f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        val baseline = size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(initial, size / 2f, baseline, textPaint)
        return BitmapDrawable(resources, bitmap)
    }

    private fun categoryFor(app: AppEntry): Filter {
        val key = (app.label + " " + app.packageName).lowercase()
        // File-transfer utilities are tools, even though their name contains "TV".
        // Match both spaced labels and compact package-name forms.
        val compactKey = key.replace(Regex("[^a-z0-9]"), "")
        if ("sendfilestotv" in compactKey) return Filter.TOOLS

        val mediaWords = listOf(
            "tv", "video", "movie", "music", "stream", "prime", "netflix",
            "disney", "plex", "kodi", "youtube", "spotify", "tivimate",
            "crunchyroll", "fandango"
        )
        if (mediaWords.any { key.contains(it) }) return Filter.MEDIA

        val gameWords = listOf(
            "game", "play games", "arcade", "xbox", "steam", "minecraft"
        )
        if (gameWords.any { key.contains(it) }) return Filter.GAMES
        return Filter.TOOLS
    }

    private fun launchApp(app: AppEntry) {
        val now = SystemClock.uptimeMillis()
        if (app.packageName == lastLaunchPkg && now - lastLaunchAt < 500) return
        lastLaunchAt = now
        lastLaunchPkg = app.packageName
        try {
            startActivity(app.launch)
            recordAppLaunch(this, app.packageName)
            // Preserve the existing behaviour: returning from an app goes home.
            finish()
        } catch (_: Exception) {
            // A broken launch intent must never close the drawer.
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
