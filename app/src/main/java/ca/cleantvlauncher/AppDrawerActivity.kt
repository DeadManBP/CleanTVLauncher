package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Clean TV v2.5 — the app drawer: a full-screen sub-menu with every app in
 * a grid, opened from the grid tile at the end of the home dock.
 * D-pad moves, short centre launches, Back closes.
 *
 * Built from plain LinearLayout rows inside a ScrollView, with NO extra
 * dependency. (The first v2.5 cut used a RecyclerView; every build with
 * that dependency failed at the build step, so the drawer was rewritten
 * dependency-free. A GridView was never an option — it swallows short
 * DPAD_CENTER presses, the v2.0–v2.2 long-press bug.)
 */
class AppDrawerActivity : Activity() {

    private var lastLaunchAt = 0L
    private var lastLaunchPkg = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)
        val apps = loadLaunchableApps(packageManager, packageName)
        findViewById<TextView>(R.id.drawer_title).text = "All Apps (${apps.size})"
        val container = findViewById<LinearLayout>(R.id.drawer_container)
        val cols = 5
        // First tile opens the favourites editor (v2.8); the rest are the apps.
        val editorEntry = AppEntry(
            "Edit favourites",
            "ca.cleantvlauncher.favourites",
            getDrawable(android.R.drawable.ic_menu_edit)!!,
            null,
            Intent(this, FavoritesActivity::class.java)
        )
        val entries = listOf(editorEntry) + apps
        var firstTile: View? = null
        for (row in entries.chunked(cols)) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (app in row) {
                val tile = LayoutInflater.from(this)
                    .inflate(R.layout.item_drawer_app, rowLayout, false)
                tile.findViewById<ImageView>(R.id.drawer_app_banner)
                    .setImageDrawable(app.banner ?: app.icon)
                tile.findViewById<TextView>(R.id.drawer_app_label).text = app.label
                tile.contentDescription = app.label
                tile.isFocusable = true
                tile.isFocusableInTouchMode = true
                val params = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                params.marginStart = dp(6)
                params.marginEnd = dp(6)
                params.topMargin = dp(6)
                params.bottomMargin = dp(6)
                tile.layoutParams = params
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
                    val s = if (hasFocus) 1.08f else 1.0f
                    v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                }
                rowLayout.addView(tile)
                // First focus lands on the first real app, not the editor tile.
                if (firstTile == null && app.packageName != "ca.cleantvlauncher.favourites") {
                    firstTile = tile
                }
            }
            // Pad a short last row with spacers so tiles keep equal width.
            repeat(cols - row.size) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                rowLayout.addView(spacer)
            }
            container.addView(rowLayout)
        }
        firstTile?.post { firstTile?.requestFocus() }
    }

    private fun launchApp(app: AppEntry) {
        val now = SystemClock.uptimeMillis()
        if (app.packageName == lastLaunchPkg && now - lastLaunchAt < 500) return
        lastLaunchAt = now
        lastLaunchPkg = app.packageName
        try {
            startActivity(app.launch)
            // Back from the launched app returns to the home dock, not the drawer.
            finish()
        } catch (e: Exception) {
            // Never let a bad launch intent kill the drawer.
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
