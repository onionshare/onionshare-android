package org.onionshare.android.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.CATEGORY_SERVICE
import androidx.core.app.NotificationCompat.CATEGORY_STATUS
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getSystemService
import org.onionshare.android.OnionShareApp
import org.onionshare.android.R
import javax.inject.Inject

private const val CHANNEL_ID_FOREGROUND = "torSharingForegroundService"
private const val CHANNEL_ID_SHARING = "torSharingStarted"
internal const val NOTIFICATION_ID_FOREGROUND = 1
internal const val NOTIFICATION_ID_SHARING = 2

class OnionNotificationManager @Inject constructor(
    private val app: Application,
) {

    private val nm = getSystemService(app, NotificationManager::class.java)!!
    private val pendingIntent: PendingIntent
        get() = Intent(app, MainActivity::class.java).let { i ->
            val pendingFlags = if (SDK_INT < 23) 0 else FLAG_IMMUTABLE
            PendingIntent.getActivity(app, 0, i, pendingFlags)
        }

    private val isActive get() = (app as OnionShareApp).isActivityStarted

    init {
        if (SDK_INT >= 26) {
            createNotificationChannels()
        }
    }

    fun onSharing() {
        getForegroundNotification(app.getText(R.string.sharing_notification_title)).let {
            nm.notify(NOTIFICATION_ID_FOREGROUND, it)
        }
        if (!isActive) getSharingNotification().let {
            nm.notify(NOTIFICATION_ID_SHARING, it)
        }
    }

    fun onStopped() {
        nm.cancel(NOTIFICATION_ID_FOREGROUND)
        nm.cancel(NOTIFICATION_ID_SHARING)
    }

    fun getForegroundNotification(): Notification {
        val title = app.getText(R.string.starting_notification_title)
        val text = app.getText(R.string.starting_notification_text)
        return getForegroundNotification(title, text)
    }

    private fun getForegroundNotification(title: CharSequence, text: CharSequence? = null): Notification {
        return NotificationCompat.Builder(app, CHANNEL_ID_FOREGROUND)
            .setContentTitle(title)
            .apply { if (!text.isNullOrBlank()) setContentText(text) }
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(app, R.color.purple_onion_light))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(PRIORITY_LOW)
            .setCategory(CATEGORY_SERVICE)
            .build()
    }

    private fun getSharingNotification(): Notification {
        return NotificationCompat.Builder(app, CHANNEL_ID_SHARING)
            .setContentTitle(app.getText(R.string.notification_sharing_title))
            .setContentText(app.getText(R.string.notification_sharing_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(app, R.color.purple_onion_light))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(PRIORITY_HIGH)
            .setCategory(CATEGORY_STATUS)
            .build()
    }

    @RequiresApi(26)
    private fun createNotificationChannels() {
        NotificationChannel(CHANNEL_ID_FOREGROUND, app.getString(R.string.notification_channel_name_foreground),
            IMPORTANCE_LOW).apply { setShowBadge(false) }.let {
            nm.createNotificationChannel(it)
        }
        NotificationChannel(CHANNEL_ID_SHARING, app.getString(R.string.notification_channel_name_sharing),
            IMPORTANCE_HIGH).let {
            nm.createNotificationChannel(it)
        }
    }
}
