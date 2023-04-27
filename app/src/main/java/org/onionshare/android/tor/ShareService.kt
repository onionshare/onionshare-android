package org.onionshare.android.tor

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.ServiceCompat.stopForeground
import dagger.hilt.android.AndroidEntryPoint
import org.onionshare.android.ui.NOTIFICATION_ID_FOREGROUND
import org.onionshare.android.ui.OnionNotificationManager
import org.slf4j.LoggerFactory.getLogger
import javax.inject.Inject

private val LOG = getLogger(ShareService::class.java)

@AndroidEntryPoint
class ShareService : Service() {

    @Inject
    internal lateinit var nm: OnionNotificationManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.debug("onStartCommand $intent")
        startForeground(NOTIFICATION_ID_FOREGROUND, nm.getForegroundNotification())
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LOG.debug("onDestroy")
        stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
