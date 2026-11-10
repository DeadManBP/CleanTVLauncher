package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean TV — an apps-only Android TV launcher.
 * A grid of installed apps, a clock, and a Settings tile. No rows, no
 * recommendations, no ads, no network use at all.
 */
class MainActivity : Activity() {

    private lateinit var grid: GridView
    private lateinit var clock: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("EEE MMM d  •  h:mm a", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 30_000)
        }
    }

    data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        val launch: Intent
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.app_grid)
        clock = findViewById(R.id.clock)
        grid.adapter = AppAdapter(loadApps())
        clockHandler.post(clockTick)
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case apps changed while we were away.
        grid.adapter = AppAdapter(loadApps())
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
                val label = ri.loadLabel(pm).toString()
                val icon = ri.activityInfo.loadIcon(pm)
                val launch = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                out.add(AppEntry(label, ri.activityInfo.packageName, icon, launch))
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
            view.findViewById<TextView>(R.id.app_label).text = app.label
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setOnClickListener { startActivity(app.launch) }
            return view
        }
    }
}
