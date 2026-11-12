package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** CleanTV's own settings. Wallpaper selection uses Android's document picker
 * and a persisted URI permission, so users can choose images without storage
 * permission or copying files into special folders. */
class CleanTVSettingsActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("clean_tv_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(56), dp(28), dp(56), dp(28))
            setBackgroundColor(Color.rgb(15, 16, 20))
        }

        root.addView(TextView(this).apply {
            text = "CleanTV Settings"
            textSize = 32f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "PERSONALIZE YOUR HOME SCREEN"
            textSize = 14f
            setTextColor(Color.rgb(190, 190, 200))
            setPadding(0, 0, 0, dp(20))
        })

        root.addView(makeButton("Choose wallpaper") { chooseWallpaper() })
        root.addView(makeButton("Use default CleanTV wallpaper") {
            prefs.edit().remove("wallpaper_uri").apply()
            setResult(Activity.RESULT_OK)
            finish()
        })
        root.addView(makeButton("Open Android TV Settings") {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        })
        root.addView(TextView(this).apply {
            text = "Tip: choose a JPG or PNG image. It will be used as the CleanTV background."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(root)
        root.findViewWithTag<View>("first-setting")?.requestFocus()
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 18f
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(65, 25, 32))
            layoutParams = LinearLayout.LayoutParams(dp(440), dp(58)).apply {
                bottomMargin = dp(12)
            }
            if (label == "Choose wallpaper") tag = "first-setting"
            setOnClickListener { action() }
            setOnKeyListener { _, keyCode, event ->
                if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) &&
                    event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    action()
                    true
                } else false
            }
        }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_WALLPAPER)
    }

    @Deprecated("Uses the compatible document-picker result callback for Android TV")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WALLPAPER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
            setResult(Activity.RESULT_OK)
            finish()
        } catch (_: Exception) {
            // Leave the current wallpaper untouched if the provider denies access.
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_WALLPAPER = 4102
    }
}
