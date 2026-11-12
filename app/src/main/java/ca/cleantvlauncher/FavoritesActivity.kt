package ca.cleantvlauncher

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Clean TV v2.8 — edit the dock favourites on the TV.
 *
 * Favourites first, in dock order, then every other app A–Z.
 * Centre on the star toggles an app in/out of the dock; the up/down
 * buttons on a favourite move it in the dock order. Changes save
 * immediately to SharedPreferences; the home dock reloads on resume.
 * D-pad moves, short centre works, Back closes.
 */
class FavoritesActivity : Activity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourites)
        container = findViewById(R.id.favourites_container)
        render(null, null)
    }

    /** Rebuilds the whole list; optionally restores focus to one button. */
    private fun render(focusPkg: String?, focusKind: String?) {
        val all = loadLaunchableApps(packageManager, packageName)
        val byPkg = all.associateBy { it.packageName }
        val favs = loadFavouritePackages(this)
            .filter { byPkg.containsKey(it) }
        container.removeAllViews()
        var focusView: View? = null

        container.addView(sectionHeader("Favourites — in dock order"))
        if (favs.isEmpty()) {
            container.addView(sectionHeader("  (none yet — star an app below)"))
        }
        for ((index, pkg) in favs.withIndex()) {
            val row = makeRow(byPkg.getValue(pkg), isFav = true, index, favs.size)
            container.addView(row)
            if (pkg == focusPkg) {
                focusView = row.findViewWithTag("fav:$focusKind:$pkg")
            }
        }

        container.addView(sectionHeader("All apps"))
        for (app in all) {
            if (app.packageName in favs) continue
            val row = makeRow(app, isFav = false, -1, 0)
            container.addView(row)
            if (app.packageName == focusPkg) {
                focusView = row.findViewWithTag("fav:$focusKind:${app.packageName}")
            }
        }
        focusView?.post { focusView?.requestFocus() }
    }

    private fun sectionHeader(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFFAAAAAA.toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = dp(14)
            p.bottomMargin = dp(4)
            layoutParams = p
        }

    /** One row: star toggle, icon + name, and up/down for favourites. */
    private fun makeRow(app: AppEntry, isFav: Boolean, index: Int, favCount: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val star = TextView(this).apply {
            text = if (isFav) "★" else "☆"
            textSize = 26f
            setTextColor(0xFFFFD34D.toInt())
            tag = "fav:star:${app.packageName}"
            isFocusable = true
            isFocusableInTouchMode = true
            background = getDrawable(R.drawable.app_focus)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        wireButton(star) { toggleFav(app.packageName) }
        row.addView(star)

        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            val s = dp(44)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        }
        row.addView(icon)

        val label = TextView(this).apply {
            text = app.label
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        row.addView(label)

        if (isFav) {
            val up = TextView(this).apply {
                text = "▲"
                textSize = 22f
                setTextColor(0xFFFFFFFF.toInt())
                tag = "fav:up:${app.packageName}"
                isFocusable = index > 0
                isFocusableInTouchMode = true
                alpha = if (index > 0) 1f else 0.25f
                background = getDrawable(R.drawable.app_focus)
                setPadding(dp(12), dp(4), dp(12), dp(4))
            }
            wireButton(up) { moveFav(app.packageName, -1) }
            row.addView(up)

            val down = TextView(this).apply {
                text = "▼"
                textSize = 22f
                setTextColor(0xFFFFFFFF.toInt())
                tag = "fav:down:${app.packageName}"
                isFocusable = index < favCount - 1
                isFocusableInTouchMode = true
                alpha = if (index < favCount - 1) 1f else 0.25f
                background = getDrawable(R.drawable.app_focus)
                setPadding(dp(12), dp(4), dp(12), dp(4))
            }
            wireButton(down) { moveFav(app.packageName, 1) }
            row.addView(down)
        }
        return row
    }

    /** Centre-press wiring for a small button: click + key, short tap. */
    private fun wireButton(view: View, onPress: () -> Unit) {
        view.setOnClickListener { onPress() }
        view.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER) &&
                event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
            ) {
                onPress()
                true
            } else {
                false
            }
        }
    }

    private fun toggleFav(pkg: String) {
        val favs = loadFavouritePackages(this).toMutableList()
        if (favs.contains(pkg)) favs.remove(pkg) else favs.add(pkg)
        saveFavouritePackages(this, favs)
        render(pkg, "star")
    }

    private fun moveFav(pkg: String, delta: Int) {
        val favs = loadFavouritePackages(this).toMutableList()
        val i = favs.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j < 0 || j >= favs.size) return
        val tmp = favs[i]
        favs[i] = favs[j]
        favs[j] = tmp
        saveFavouritePackages(this, favs)
        render(pkg, if (delta < 0) "up" else "down")
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
