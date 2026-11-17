package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
        setContentView(R.layout.activity_drawer)
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

    private fun chipBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (selected) Color.rgb(115, 25, 39) else Color.rgb(25, 31, 41))
            setStroke(dp(if (selected) 2 else 1),
                if (selected) Color.rgb(230, 65, 82) else Color.rgb(91, 105, 122))
        }

    private fun renderApps(focusFirst: Boolean) {
        container.removeAllViews()
        val favourites = loadFavouritePackages(this).toSet()
        val recent = loadRecentPackages(this)
        val filtered = when (activeFilter) {
            Filter.ALL -> allApps
            Filter.FAVOURITES -> allApps.filter { it.packageName in favourites }
            Filter.RECENT -> recent.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
            Filter.MEDIA -> allApps.filter { categoryFor(it) == Filter.MEDIA }
            Filter.GAMES -> allApps.filter { categoryFor(it) == Filter.GAMES }
            Filter.TOOLS -> allApps.filter { categoryFor(it) == Filter.TOOLS }
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
        subtitle.text = "${filtered.size} apps  •  ${allApps.size} available"
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
                    .setImageDrawable(app.banner ?: app.icon)
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

    private fun categoryFor(app: AppEntry): Filter {
        val key = (app.label + " " + app.packageName).lowercase()
        val mediaWords = listOf(
            "tv", "video", "movie", "music", "stream", "prime", "netflix",
            "disney", "plex", "kodi", "youtube", "spotify", "tivimate"
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
