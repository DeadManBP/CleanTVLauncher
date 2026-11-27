package ca.cleantvlauncher

import android.service.notification.NotificationListenerService

/**
 * Declares CleanTV as an approved notification listener so Android TV can
 * grant access to active media sessions. CleanTV only queries media metadata;
 * it does not read or display notification contents.
 */
class MediaSessionBridgeService : NotificationListenerService()
