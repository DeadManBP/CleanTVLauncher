package ca.cleantvlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

/** Read-only hardware, Android, display, storage and CleanTV diagnostics. */
class SystemInformationActivity : Activity() {
    private val accent = Color.rgb(220, 50, 68)
    private val muted = Color.rgb(157, 167, 181)
    private val panel = Color.rgb(23, 27, 36)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(38), dp(24), dp(38), dp(28))
            setBackgroundColor(Color.rgb(11, 13, 18))
        }
        root.addView(TextView(this).apply {
            text = "CLEANTV  /  SYSTEM"
            textSize = 12f
            letterSpacing = 0.16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(accent)
        })
        root.addView(TextView(this).apply {
            text = "System Information"
            textSize = 32f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, dp(3))
        })
        root.addView(TextView(this).apply {
            text = "DEVICE DIAGNOSTICS  •  READ ONLY"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(15))
        })

        val metrics = resources.displayMetrics
        val display = windowManager.defaultDisplay
        val size = android.graphics.Point()
        display.getRealSize(size)
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val storage = StatFs(filesDir.absolutePath)
        val totalStorage = storage.totalBytes
        val freeStorage = storage.availableBytes
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val wifiIp = try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo.ipAddress
            if (ip == 0) "Unavailable" else String.format(
                Locale.US, "%d.%d.%d.%d",
                ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
            )
        } catch (_: Exception) { "Unavailable" }

        section(root, "DEVICE")
        info(root, "Manufacturer", Build.MANUFACTURER)
        info(root, "Brand", Build.BRAND)
        info(root, "Model", Build.MODEL)
        info(root, "Device", Build.DEVICE)
        info(root, "Processor ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unavailable")

        section(root, "ANDROID")
        info(root, "Android version", Build.VERSION.RELEASE ?: "Unknown")
        info(root, "API level", Build.VERSION.SDK_INT.toString())
        info(root, "Security patch", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH ?: "Unavailable" else "Not provided by this Android version")
        info(root, "Build number", Build.DISPLAY ?: "Unavailable")

        section(root, "DISPLAY")
        info(root, "Resolution", "${size.x} × ${size.y}")
        info(root, "Density", "${metrics.densityDpi} dpi")
        info(root, "Density scale", String.format(Locale.US, "%.2fx", metrics.density))

        section(root, "MEMORY")
        info(root, "Total RAM", formatBytes(memory.totalMem))
        info(root, "Available RAM", formatBytes(memory.availMem))
        info(root, "Low memory state", if (memory.lowMemory) "Low" else "Normal")

        section(root, "INTERNAL STORAGE")
        info(root, "Total", formatBytes(totalStorage))
        info(root, "Available", formatBytes(freeStorage))
        info(root, "Used", formatBytes((totalStorage - freeStorage).coerceAtLeast(0)))

        section(root, "NETWORK")
        info(root, "Wi-Fi IP address", wifiIp)

        section(root, "CLEANTV")
        info(root, "App version", packageInfo.versionName ?: "Unknown")
        info(root, "Package", packageName)
        info(root, "Build type", if (packageName.endsWith(".dev")) "Development" else "Release")

        root.addView(TextView(this).apply {
            text = "BACK  /  RETURN TO CONTROL CENTER"
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.08f
            setTextColor(muted)
            setPadding(0, dp(18), 0, dp(4))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            clipToPadding = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
            addView(root, ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            ))
            setBackgroundColor(Color.rgb(11, 13, 18))
        }
        setContentView(scroll)
        scroll.requestFocus()
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 12f
            letterSpacing = 0.14f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(accent)
            setPadding(0, dp(13), 0, dp(6))
        })
    }

    private fun info(root: LinearLayout, label: String, value: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(panel)
                setStroke(dp(1), Color.rgb(48, 56, 69))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(43)
            ).apply { bottomMargin = dp(5) }
        }
        card.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(muted)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(this).apply {
            text = value
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.END
        })
        root.addView(card)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "Unavailable"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb)
        else String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
