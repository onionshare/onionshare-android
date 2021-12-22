package org.onionshare.android.tor

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import org.onionshare.android.ui.NOTIFICATION_ID
import org.onionshare.android.ui.OnionNotificationManager
import org.slf4j.LoggerFactory.getLogger
import javax.inject.Inject

private val LOG = getLogger(ShareService::class.java)

@AndroidEntryPoint
class ShareService : Service() {

    private var binder: IBinder? = null

    @Inject
    internal lateinit var nm: OnionNotificationManager

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            LOG.info("OnionService connected")
            binder = service
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            LOG.info("OnionService disconnected")
            binder = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.debug("onStartCommand $intent")
        startForeground(NOTIFICATION_ID, nm.getForegroundNotification())
        if (binder == null) {
            Intent(this, OnionService::class.java).also { i ->
                val bindFlags = BIND_AUTO_CREATE or BIND_IMPORTANT
                bindService(i, serviceConnection, bindFlags)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(true)
        if (binder != null) unbindService(serviceConnection)
        super.onDestroy()
    }
}
