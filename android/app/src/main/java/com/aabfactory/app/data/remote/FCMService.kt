package com.aabfactory.app.data.remote

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

/**
 * Firebase Cloud Messaging service.
 * Handles push notifications for build completion and subscription events.
 */
class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM token refreshed: $token")
        // TODO: Send token to backend for user association
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.d("FCM message received: ${remoteMessage.data}")

        when (remoteMessage.data["type"]) {
            "build_complete" -> handleBuildComplete(remoteMessage.data)
            "subscription_update" -> handleSubscriptionUpdate(remoteMessage.data)
            else -> Timber.w("Unknown FCM message type: ${remoteMessage.data["type"]}")
        }
    }

    private fun handleBuildComplete(data: Map<String, String>) {
        val projectId = data["projectId"] ?: return
        val downloadUrl = data["downloadUrl"] ?: return
        Timber.i("Build complete for project $projectId — URL: $downloadUrl")
        // Broadcast to UI layer via local notification or SharedFlow
    }

    private fun handleSubscriptionUpdate(data: Map<String, String>) {
        val plan = data["plan"] ?: return
        Timber.i("Subscription updated to plan: $plan")
    }
}
