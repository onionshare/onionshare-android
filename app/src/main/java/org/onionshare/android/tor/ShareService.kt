package org.onionshare.android.tor

import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import org.onionshare.android.ui.NOTIFICATION_ID
import org.onionshare.android.ui.OnionNotificationManager
import org.slf4j.LoggerFactory.getLogger
import org.torproject.jni.TorService
import javax.inject.Inject
import kotlin.system.exitProcess

private val LOG = getLogger(ShareService::class.java)

@AndroidEntryPoint
class ShareService : TorService() {

    @Inject
    internal lateinit var nm: OnionNotificationManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.debug("onStartCommand $intent")
        startForeground(NOTIFICATION_ID, nm.getForegroundNotification())
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LOG.debug("onDestroy")
        stopForeground(true)
        super.onDestroy()
        // ensure this process terminates
        exitProcess(0)
    }
}
