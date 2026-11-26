package ca.cleantvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import java.text.Collator
import java.util.Locale

/**
 * Centralized installed-app discovery for the home screen and app-management
 * screens. Results are cached for the process lifetime and invalidated when
 * Android reports that an application package has changed.
 */
object AppDiscoveryManager {

    @Volatile
    private var cachedApps: List<AppEntry>? = null

    @Volatile
    private var receiverRegistered = false

    private val cacheLock = Any()

    /**
     * Register once against the application context, not an Activity, so the
     * listener cannot leak a screen. Each screen refreshes from this cache in
     * onResume; package broadcasts simply invalidate the stale snapshot.
     */
    @Synchronized
    fun ensurePackageChangeListener(context: Context) {
        if (receiverRegistered) return

        val appContext = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                invalidate()
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {
            // Discovery still works on devices that restrict dynamic receivers.
        }
    }

    fun invalidate() {
        synchronized(cacheLock) {
            cachedApps = null
        }
    }

    fun getLaunchableApps(pm: PackageManager, selfPackage: String): List<AppEntry> {
        cachedApps?.let { return it }

        synchronized(cacheLock) {
            cachedApps?.let { return it }
            val discovered = discover(pm, selfPackage)
            cachedApps = discovered
            return discovered
        }
    }

    private fun discover(pm: PackageManager, selfPackage: String): List<AppEntry> {
        // Prefer Leanback activities when a package exposes both TV and
        // standard launcher entries. One package should appear only once.
        val byPackage = LinkedHashMap<String, AppEntry>()
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )

        for (intent in intents) {
            val found: List<ResolveInfo> = try {
                pm.queryIntentActivities(intent, 0)
            } catch (_: Exception) {
                emptyList()
            }

            for (ri in found) {
                val info = ri.activityInfo ?: continue
                val packageName = info.packageName ?: continue
                if (packageName == selfPackage || byPackage.containsKey(packageName)) continue
                if (!info.applicationInfo.enabled || !info.enabled) continue

                try {
                    val label = ri.loadLabel(pm)?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() } ?: packageName
                    val icon: Drawable = ri.loadIcon(pm)
                    val iconResource = info.getIconResource()
                    val hasCustomIcon = if (iconResource == 0) {
                        false
                    } else {
                        try {
                            pm.getResourcesForApplication(packageName)
                                .getResourcePackageName(iconResource) == packageName
                        } catch (_: Exception) {
                            false
                        }
                    }
                    val banner: Drawable? = try {
                        info.loadBanner(pm)
                    } catch (_: Exception) {
                        null
                    }
                    val launch = pm.getLaunchIntentForPackage(packageName)
                        ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        ?: Intent(Intent.ACTION_MAIN).apply {
                            setClassName(packageName, info.name)
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }

                    byPackage[packageName] = AppEntry(
                        label, packageName, icon, banner, launch, hasCustomIcon
                    )
                } catch (_: Exception) {
                    // A broken/unavailable package must not abort discovery
                    // for every other installed application.
                }
            }
        }

        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        return byPackage.values.sortedWith { first, second ->
            collator.compare(first.label, second.label)
        }
    }
}

/** Categories shared by the drawer and future app-management controls. */
enum class AppCategory(val preferenceValue: String) {
    MEDIA("media"),
    GAMES("games"),
    TOOLS("tools");

    companion object {
        fun fromPreference(value: String?): AppCategory? =
            values().firstOrNull { it.preferenceValue == value }
    }
}

/**
 * Automatic categorization with package-specific rules before broader name
 * matching. Manual overrides are stored separately from discovery metadata,
 * so refreshing the installed-app list never erases user choices.
 */
object AppCategoryClassifier {
    private const val PREF_OVERRIDES = "app_category_overrides"

    fun categoryFor(context: Context, app: AppEntry): AppCategory {
        loadOverrides(context)[app.packageName]?.let { return it }

        val packageName = app.packageName.lowercase(Locale.ROOT)
        val label = app.label.lowercase(Locale.ROOT)
        val compact = (label + " " + packageName).replace(Regex("[^a-z0-9]"), "")

        if (packageName in setOf(
                "com.google.android.play.games",
                "com.valvesoftware.android.steam.community"
            ) || listOf("game", "arcade", "minecraft", "xbox", "steam")
                .any { it in compact }
        ) return AppCategory.GAMES

        if (packageName.contains("sendfilestotv") ||
            packageName.contains("filemanager") ||
            packageName.contains("file.manager") ||
            listOf("sendfilestotv", "filemanager", "filebrowser", "settings",
                "browser", "utility", "cleaner", "calculator")
                .any { it in compact }
        ) return AppCategory.TOOLS

        val mediaPackages = listOf(
            "netflix", "primevideo", "amazonvideo", "disney", "youtube",
            "tivimate", "plex", "kodi", "spotify", "crunchyroll",
            "fandango", "globaltv", "cbc", "ctv", "bellmedia"
        )
        if (mediaPackages.any { it in packageName } ||
            listOf("video", "movie", "music", "stream", "netflix", "disney",
                "plex", "kodi", "youtube", "spotify", "tivimate", "crunchyroll",
                "prime video", "global tv", "cbc gem", "ctv")
                .any { label.contains(it) }
        ) return AppCategory.MEDIA

        return AppCategory.TOOLS
    }

    fun saveOverride(context: Context, packageName: String, category: AppCategory?) {
        val overrides = loadOverrides(context).toMutableMap()
        if (category == null) overrides.remove(packageName)
        else overrides[packageName] = category

        val encoded = overrides.entries.joinToString(",") {
            "${it.key}=${it.value.preferenceValue}"
        }
        context.getSharedPreferences("clean_tv_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_OVERRIDES, encoded)
            .apply()
    }

    private fun loadOverrides(context: Context): Map<String, AppCategory> {
        val raw = context.getSharedPreferences("clean_tv_prefs", Context.MODE_PRIVATE)
            .getString(PREF_OVERRIDES, "")
            .orEmpty()
        if (raw.isBlank()) return emptyMap()

        return raw.split(",").mapNotNull { entry ->
            val separator = entry.lastIndexOf('=')
            if (separator <= 0) return@mapNotNull null
            val packageName = entry.substring(0, separator)
            val category = AppCategory.fromPreference(entry.substring(separator + 1))
                ?: return@mapNotNull null
            packageName to category
        }.toMap()
    }
}
