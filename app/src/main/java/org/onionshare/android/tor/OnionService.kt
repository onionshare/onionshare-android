package org.onionshare.android.tor

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import org.slf4j.LoggerFactory.getLogger
import org.torproject.jni.TorService

private val LOG = getLogger(OnionService::class.java)

class OnionService : TorService() {

    private val binder: IBinder = OnionBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        LOG.debug("onDestroy")
        super.onDestroy()
    }

    inner class OnionBinder : Binder() {
        val service: OnionService get() = this@OnionService
    }
}
