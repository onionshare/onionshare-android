package org.onionshare.android.tor

import org.slf4j.LoggerFactory.getLogger
import org.torproject.jni.TorService
import kotlin.system.exitProcess

private val LOG = getLogger(OnionService::class.java)

class OnionService : TorService() {

    override fun onDestroy() {
        LOG.debug("onDestroy")
        super.onDestroy()
        // ensure this process terminates
        exitProcess(0)
    }
}
