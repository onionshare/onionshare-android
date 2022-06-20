package org.onionshare.android.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.CATEGORY_SERVICE
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getSystemService
import org.onionshare.android.R
import javax.inject.Inject

private const val CHANNEL_ID = "torSharingForegroundService"
internal const val NOTIFICATION_ID = 1

class OnionNotificationManager @Inject constructor(
    private val app: Application,
) {

    private val nm = getSystemService(app, NotificationManager::class.java)!!

    init {
        if (SDK_INT >= 26) {
            createNotificationChannels()
        }
    }

    fun getForegroundNotification(): Notification {
        val title = app.getText(R.string.starting_notification_title)
        val text = app.getText(R.string.starting_notification_text)
        return getNotification(title, text)
    }

    fun onSharing() {
        val n = getNotification(app.getText(R.string.sharing_notification_title))
        nm.notify(NOTIFICATION_ID, n)
    }

    private fun getNotification(title: CharSequence, text: CharSequence? = null): Notification {
        val pendingIntent: PendingIntent = Intent(app, MainActivity::class.java).let { i ->
            val pendingFlags = if (SDK_INT < 23) 0 else FLAG_IMMUTABLE
            PendingIntent.getActivity(app, 0, i, pendingFlags)
        }
        return NotificationCompat.Builder(app, CHANNEL_ID)
            .setContentTitle(title)
            .apply { if (!text.isNullOrBlank()) setContentText(text) }
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(app, R.color.purple_onion_light))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(CATEGORY_SERVICE)
            .build()
    }

    @RequiresApi(26)
    private fun createNotificationChannels() {
        val name = app.getString(R.string.sharing_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }
}
