package ca.cleantvlauncher

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Clean TV v2.5 — the app drawer: a full-screen sub-menu with every app in
 * a grid, opened from the grid tile at the end of the home dock.
 * D-pad moves, short centre launches, Back closes.
 *
 * Uses a RecyclerView, NOT a GridView — a GridView swallows short
 * DPAD_CENTER presses (the v2.0–v2.2 long-press bug), while RecyclerView
 * items are real focusable views that get the key directly.
 */
class AppDrawerActivity : Activity() {

    private var lastLaunchAt = 0L
    private var lastLaunchPkg = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)
        val apps = loadLaunchableApps(packageManager, packageName)
        findViewById<TextView>(R.id.drawer_title).text = "All Apps (${apps.size})"
        val grid = findViewById<RecyclerView>(R.id.drawer_grid)
        grid.layoutManager = GridLayoutManager(this, 5)
        grid.adapter = DrawerAdapter(apps) { app -> launchApp(app) }
        grid.setHasFixedSize(true)
        grid.post { grid.getChildAt(0)?.requestFocus() }
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

    private class DrawerAdapter(
        private val apps: List<AppEntry>,
        private val onLaunch: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<DrawerAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val banner: ImageView = view.findViewById(R.id.drawer_app_banner)
            val label: TextView = view.findViewById(R.id.drawer_app_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drawer_app, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = apps[position]
            holder.banner.setImageDrawable(app.banner ?: app.icon)
            holder.label.text = app.label
            val view = holder.itemView
            view.contentDescription = app.label
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setOnClickListener { onLaunch(app) }
            view.setOnKeyListener { _, keyCode, event ->
                if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER) &&
                    event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
                ) {
                    onLaunch(app)
                    true
                } else {
                    false
                }
            }
            view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                val s = if (hasFocus) 1.08f else 1.0f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }

        override fun getItemCount(): Int = apps.size
    }
}
