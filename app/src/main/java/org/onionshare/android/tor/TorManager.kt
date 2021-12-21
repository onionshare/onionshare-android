package org.onionshare.android.tor

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.LocalSocketAddress.Namespace.FILESYSTEM
import androidx.core.content.ContextCompat.startForegroundService
import kotlinx.coroutines.suspendCancellableCoroutine
import net.freehaven.tor.control.TorControlCommands.EVENT_ERR_MSG
import net.freehaven.tor.control.TorControlCommands.EVENT_HS_DESC
import net.freehaven.tor.control.TorControlCommands.EVENT_NEW_DESC
import net.freehaven.tor.control.TorControlCommands.EVENT_OR_CONN_STATUS
import net.freehaven.tor.control.TorControlCommands.EVENT_WARN_MSG
import net.freehaven.tor.control.TorControlCommands.HS_ADDRESS
import net.freehaven.tor.control.TorControlConnection
import org.slf4j.LoggerFactory.getLogger
import org.torproject.jni.TorService
import org.torproject.jni.TorService.ACTION_STATUS
import org.torproject.jni.TorService.EXTRA_STATUS
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val LOG = getLogger(TorManager::class.java)
private val EVENTS = listOf(
    EVENT_OR_CONN_STATUS,
    EVENT_HS_DESC,
    EVENT_NEW_DESC,
    EVENT_WARN_MSG,
    EVENT_ERR_MSG,
)

@Singleton
class TorManager @Inject constructor(
    private val app: Application,
) {
    private var broadcastReceiver: BroadcastReceiver? = null

    /**
     * Starts [TorService] and creates a new onion service.
     * Suspends until the address of the onion service is available.
     */
    suspend fun start(port: Int): String = suspendCancellableCoroutine { continuation ->
        LOG.info("Starting...")
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) {
                when (i.getStringExtra(EXTRA_STATUS)) {
                    TorService.STATUS_STARTING -> LOG.debug("TorService: Starting...")
                    TorService.STATUS_ON -> {
                        LOG.debug("TorService: Started")

                        startControlConnection(context).apply {
                            try {
                                launchThread(true)
                                authenticate(ByteArray(0))
                                takeOwnership()
                                setEvents(EVENTS)
                            } catch (e: Exception) {
                                // gets caught and logged by caller
                                continuation.resumeWithException(e)
                                return
                            }
                            val onion = createOnionService(this, continuation, port)
                            // if onion is null, we resume with an exception in createOnionService()
                            if (onion != null) addRawEventListener { keyword, data ->
                                LOG.debug("$keyword: $data")
                                if (keyword == "HS_DESC" && data.startsWith("UPLOADED ")) {
                                    if (continuation.isActive) continuation.resume(onion)
                                }
                            }
                        }
                    }
                    // FIXME When we stop unplanned, we need to inform the ShareManager
                    //  that we stopped, so it can clear its state up, stopping webserver, etc.
                    TorService.STATUS_STOPPING -> LOG.debug("TorService: Stopping...")
                    TorService.STATUS_OFF -> LOG.debug("TorService: Stopped")
                }
            }
        }
        app.registerReceiver(broadcastReceiver, IntentFilter(ACTION_STATUS))

        Intent(app, ShareService::class.java).also { intent ->
            startForegroundService(app, intent)
        }
        // this method suspends here until it the continuation resumes it
    }

    fun stop() {
        LOG.info("Stopping...")
        Intent(app, ShareService::class.java).also { intent ->
            app.stopService(intent)
        }
        broadcastReceiver?.let { app.unregisterReceiver(it) }
        broadcastReceiver = null
        LOG.info("Stopped")
    }

    /**
     * Creates a new onion service each time it is called
     * and resumes the given [continuation] with its address.
     */
    private fun createOnionService(
        controlConnection: TorControlConnection,
        continuation: Continuation<String>,
        port: Int,
    ): String? {
        LOG.error("Starting hidden service...")
        val portLines = Collections.singletonMap(80, "127.0.0.1:$port")
        val response = try {
            controlConnection.addOnion("NEW:ED25519-V3", portLines, null)
        } catch (e: IOException) {
            LOG.error("Error creation onion service", e)
            continuation.resumeWithException(e)
            return null
        }
        if (!response.containsKey(HS_ADDRESS)) {
            LOG.error("Tor did not return a hidden service address")
            continuation.resumeWithException(IOException("No HS_ADDRESS"))
            return null
        }
        return "${response[HS_ADDRESS]}.onion"
    }

    private fun startControlConnection(context: Context): TorControlConnection {
        val pathname = getControlPath(context)
        val localSocketAddress = LocalSocketAddress(pathname, FILESYSTEM)
        val client = LocalSocket()
        client.connect(localSocketAddress)
        client.receiveBufferSize = 1024
        client.soTimeout = 3000

        val controlFileDescriptor = client.fileDescriptor
        val inputStream = FileInputStream(controlFileDescriptor)
        val outputStream = FileOutputStream(controlFileDescriptor)
        return TorControlConnection(inputStream, outputStream)
    }

    /**
     * Returns the absolute path to the control socket on the local filesystem.
     *
     * Note: Exposing this through TorService was rejected by upstream.
     *       https://github.com/guardianproject/tor-android/pull/61
     */
    private fun getControlPath(context: Context): String {
        val serviceDir = context.getDir(TorService::class.java.simpleName, 0)
        val dataDir = File(serviceDir, "data")
        return File(dataDir, "ControlSocket").absolutePath
    }

}
