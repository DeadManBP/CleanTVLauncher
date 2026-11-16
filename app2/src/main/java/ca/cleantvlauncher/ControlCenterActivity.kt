package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Right-side translucent quick panel. The launcher remains visible behind it. */
class ControlCenterActivity : Activity() {

    private val accent = Color.rgb(220, 50, 68)
    private val panel = Color.rgb(18, 22, 30)
    private val muted = Color.rgb(157, 167, 181)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.58f)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
        }
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(26), dp(26), dp(26), dp(22))
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f, dp(20).toFloat(), dp(20).toFloat())
                setStroke(dp(1), Color.rgb(68, 45, 53))
            }
            elevation = dp(18).toFloat()
        }

        side.addView(TextView(this).apply {
            text = "CLEANTV  /  QUICK CONTROLS"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.14f
            setTextColor(accent)
        })
        side.addView(TextView(this).apply {
            text = "Control Center"
            textSize = 30f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(3), 0, dp(2))
        })
        side.addView(TextView(this).apply {
            text = "Fast access. No menu diving."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(17))
        })

        addSection(side, "INPUT")
        side.addView(actionButton("XBOX  /  HDMI 1", "Switch directly to the Xbox input") {
            launchXbox()
        })
        addSection(side, "TV CONTROLS")
        side.addView(actionButton("NETWORK", "Wi-Fi and network connections") {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        })
        side.addView(actionButton("PICTURE", "Display and picture settings") {
            openSettings(Settings.ACTION_DISPLAY_SETTINGS)
        })
        side.addView(actionButton("SOUND", "Audio output and sound settings") {
            openSettings(Settings.ACTION_SOUND_SETTINGS)
        })
        addSection(side, "SYSTEM")
        side.addView(actionButton("ANDROID TV SETTINGS", "Open the television's system settings") {
            openSettings(Settings.ACTION_SETTINGS)
        })
        side.addView(actionButton("DEVICE INFORMATION", "About this TV and device details") {
            openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
        })
        side.addView(TextView(this).apply {
            text = "BACK  /  CLOSE"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })

        val width = minOf(dp(430), (resources.displayMetrics.widthPixels * 0.43f).toInt())
        scrim.addView(side, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END))
        setContentView(scrim)
        side.post { side.getChildAt(4)?.requestFocus() }
    }

    private fun addSection(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 11f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.16f
            setTextColor(accent)
            setPadding(0, dp(8), 0, dp(7))
        })
    }

    private fun actionButton(title: String, description: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = "$title\n$description"
            textSize = 15f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(5), dp(12), dp(5))
            isFocusable = true
            isFocusableInTouchMode = true
            background = surface(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)
            ).apply { bottomMargin = dp(7) }
            setOnFocusChangeListener { view, focused ->
                view.background = surface(focused)
                view.scaleX = if (focused) 1.025f else 1f
                view.scaleY = if (focused) 1.025f else 1f
            }
            setOnClickListener { action() }
        }

    private fun surface(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(9).toFloat()
        setColor(if (focused) Color.rgb(63, 25, 34) else Color.rgb(29, 35, 45))
        setStroke(dp(if (focused) 2 else 1), if (focused) accent else Color.rgb(55, 64, 78))
    }

    private fun launchXbox() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(
                    "content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW1"
                )
            })
            finish()
        } catch (_: Exception) {
            openSettings(Settings.ACTION_SETTINGS)
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
            finish()
        } catch (_: Exception) {
            if (action != Settings.ACTION_SETTINGS) openSettings(Settings.ACTION_SETTINGS)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Back closes the overlay")
    override fun onBackPressed() {
        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
