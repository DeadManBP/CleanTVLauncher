package ca.cleantvlauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Choose which installed apps appear in the App Drawer. Nothing is uninstalled. */
class HiddenAppsActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(24), dp(34), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 25, 34))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(83, 48, 59))
            }
        }
        root.addView(TextView(this).apply {
            text = "APP VISIBILITY"
            textSize = 13f
            letterSpacing = 0.15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(accent())
        })
        root.addView(TextView(this).apply {
            text = "Hide apps"
            textSize = 34f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Hidden apps disappear from All Apps and its categories. They remain installed, and dock favourites stay available."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(10))
        })
        count = TextView(this).apply {
            textSize = 14f
            setTextColor(accent())
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(count)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(list)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(TextView(this).apply {
            text = "BACK TO RETURN"
            textSize = 12f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.GRAY)
            setPadding(0, dp(12), 0, 0)
        })
        setContentView(root)
        render()
    }

    private fun render(focusPackage: String? = null) {
        list.removeAllViews()
        val apps = loadLaunchableApps(packageManager, packageName)
        val hidden = loadHiddenPackages(this).toMutableSet()
        count.text = "${hidden.size} hidden  •  ${apps.size - hidden.size.coerceAtMost(apps.size)} visible"
        var focusTarget: View? = null
        apps.forEach { app ->
            val isHidden = app.packageName in hidden
            val row = TextView(this).apply {
                text = (if (isHidden) "HIDDEN     " else "VISIBLE     ") + app.label
                textSize = 18f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(16), dp(9))
                isFocusable = true
                isFocusableInTouchMode = true
                background = rowBackground(false)
                contentDescription = "${app.label}, ${if (isHidden) "hidden" else "visible"}. Press to toggle."
                setOnClickListener { toggle(app.packageName) }
                setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) &&
                        event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        toggle(app.packageName)
                        true
                    } else false
                }
                onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                    view.background = rowBackground(focused)
                }
            }
            list.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
            ).apply { bottomMargin = dp(6) })
            if (app.packageName == focusPackage) focusTarget = row
        }
        val target = focusTarget ?: list.getChildAt(0)
        target?.post { target.requestFocus() }
    }

    private fun toggle(pkg: String) {
        val hidden = loadHiddenPackages(this).toMutableSet()
        if (!hidden.add(pkg)) hidden.remove(pkg)
        saveHiddenPackages(this, hidden)
        render(pkg)
    }

    private fun rowBackground(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(9).toFloat()
        setColor(if (focused) Color.rgb(62, 24, 34) else Color.rgb(31, 38, 49))
        setStroke(dp(if (focused) 2 else 1), if (focused) accent() else Color.rgb(69, 81, 98))
    }

    private fun accent(): Int =
        when (getSharedPreferences("clean_tv_prefs", MODE_PRIVATE).getString("theme_accent", "crimson")) {
            "blue" -> Color.rgb(55, 145, 220)
            "green" -> Color.rgb(55, 175, 125)
            else -> Color.rgb(205, 48, 62)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
