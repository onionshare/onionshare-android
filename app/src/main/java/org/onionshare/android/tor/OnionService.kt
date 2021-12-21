package org.onionshare.android.tor

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import org.onionshare.android.ui.NOTIFICATION_ID
import org.onionshare.android.ui.OnionNotificationManager
import org.slf4j.LoggerFactory.getLogger
import org.torproject.jni.TorService
import javax.inject.Inject

private val LOG = getLogger(OnionService::class.java)

@AndroidEntryPoint
class OnionService : TorService() {

    @Inject
    internal lateinit var nm: OnionNotificationManager
    private val binder: IBinder = OnionBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, nm.getForegroundNotification())
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        LOG.debug("onDestroy")
        stopForeground(true)
        super.onDestroy()
    }

    inner class OnionBinder : Binder() {
        val service: OnionService get() = this@OnionService
    }
}
