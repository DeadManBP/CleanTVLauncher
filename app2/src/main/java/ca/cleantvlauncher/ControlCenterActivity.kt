package ca.cleantvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Right-side translucent quick panel. The launcher remains visible behind it. */
class ControlCenterActivity : Activity() {

    private val accent = Color.rgb(220, 50, 68)
    private val panel = Color.rgb(18, 22, 30)
    private val muted = Color.rgb(157, 167, 181)
    private var networkStatusView: TextView? = null

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
            setPadding(0, 0, 0, dp(10))
        })
        networkStatusView = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.rgb(130, 190, 150))
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.rgb(27, 39, 38))
                setStroke(dp(1), Color.rgb(55, 82, 68))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).apply {
                bottomMargin = dp(8)
            }
        }
        side.addView(networkStatusView)

        addSection(side, "INPUT")
        val xboxButton = actionButton("XBOX  /  HDMI 1", "Switch directly to the Xbox input") {
            launchXbox()
        }
        side.addView(xboxButton)
        addAvailableTvInputs(side)
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
            startActivity(Intent(this, SystemInformationActivity::class.java))
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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f, dp(20).toFloat(), dp(20).toFloat())
                setStroke(dp(1), Color.rgb(68, 45, 53))
            }
            elevation = dp(18).toFloat()
            addView(side, ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT))
        }
        val width = minOf(dp(430), (resources.displayMetrics.widthPixels * 0.43f).toInt())
        scrim.addView(scroll, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END))
        setContentView(scrim)
        xboxButton.post { xboxButton.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        refreshNetworkStatus()
    }

    private fun refreshNetworkStatus() {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        var connected = false
        var hasInternet = false
        var transport = "NETWORK"
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = manager?.activeNetwork
                val capabilities = network?.let { manager.getNetworkCapabilities(it) }
                connected = capabilities != null
                hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                transport = when {
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WI-FI"
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "MOBILE DATA"
                    else -> "NETWORK"
                }
            } else {
                @Suppress("DEPRECATION")
                connected = manager?.activeNetworkInfo?.isConnected == true
                hasInternet = connected
            }
        } catch (_: Exception) {
            connected = false
            hasInternet = false
        }

        networkStatusView?.apply {
            text = when {
                hasInternet -> "●  INTERNET CONNECTED  ·  $transport"
                connected -> "●  LOCAL NETWORK ONLY  ·  NO INTERNET"
                else -> "●  OFFLINE  ·  NO NETWORK CONNECTION"
            }
            setTextColor(if (hasInternet) Color.rgb(130, 190, 150) else Color.rgb(235, 165, 105))
        }
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

    /**
     * Discover passthrough inputs exposed by the TV instead of assuming every
     * manufacturer uses the same HDMI input identifiers.
     */
    private fun addAvailableTvInputs(root: LinearLayout) {
        val inputs = try {
            val manager = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
            manager?.tvInputList
                ?.filter { it.isPassthroughInput }
                ?.distinctBy { it.id }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        if (inputs.isEmpty()) return

        addSection(root, "AVAILABLE TV INPUTS")
        inputs.forEachIndexed { index, input ->
            val label = try {
                input.loadLabel(this)?.toString()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            } ?: "TV Input ${index + 1}"

            // The existing Xbox shortcut remains pinned above. Avoid showing
            // an identical duplicate when the TV labels that input as HDMI 1.
            if (label.contains("HDMI 1", ignoreCase = true) ||
                label.contains("Xbox", ignoreCase = true)) return@forEachIndexed

            root.addView(actionButton(label.uppercase(), "Switch to this TV input") {
                try {
                    val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    finish()
                } catch (_: Exception) {
                    openSettings(Settings.ACTION_SETTINGS)
                }
            })
        }
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
            // Keep the translucent Control Center underneath system settings.
            // Android Back then returns here instead of dropping to the launcher.
            startActivity(Intent(action))
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
