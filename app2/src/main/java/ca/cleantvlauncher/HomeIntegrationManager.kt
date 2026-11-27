package ca.cleantvlauncher

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Handles CleanTV's status as the device's Home app without assuming every
 * Android TV vendor exposes the same settings screen.
 */
object HomeIntegrationManager {

    /** True when Android currently assigns the HOME role to this app. */
    fun isDefaultHome(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.getRoleHolders(RoleManager.ROLE_HOME)
                    .contains(activity.packageName)
            }
        }

        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = activity.packageManager.resolveActivity(
                homeIntent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            resolved?.activityInfo?.packageName == activity.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens Android's official Home-app selection flow.
     * Returns false only when the device exposes no usable selection screen.
     */
    fun requestDefaultHome(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = activity.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    activity.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return true
                }
            } catch (_: Exception) {
                // Fall through to the vendor's Home settings screen.
            }
        }

        return try {
            activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            true
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
