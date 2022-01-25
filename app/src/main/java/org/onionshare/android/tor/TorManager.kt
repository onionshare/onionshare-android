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
import net.freehaven.tor.control.RawEventListener
import net.freehaven.tor.control.TorControlCommands.EVENT_CIRCUIT_STATUS
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val LOG = getLogger(TorManager::class.java)
private val EVENTS = listOf(
    EVENT_CIRCUIT_STATUS, // this one is needed for TorService to function
    EVENT_OR_CONN_STATUS, // can be removed before release
    EVENT_HS_DESC,
    EVENT_NEW_DESC, // can be removed before release
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
    suspend fun start(port: Int): String {
        startAndGetPath()
        return onTorStarted(port)
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

    private suspend fun startAndGetPath(): Unit = suspendCancellableCoroutine { continuation ->
        LOG.info("Starting...")
        broadcastReceiver = object : BroadcastReceiver() {
            /**
             * Attention: This gets executes on UI Thread
             */
            override fun onReceive(context: Context, i: Intent) {
                when (i.getStringExtra(EXTRA_STATUS)) {
                    TorService.STATUS_STARTING -> LOG.debug("TorService: Starting...")
                    TorService.STATUS_ON -> {
                        LOG.debug("TorService: Started")
                        if (continuation.isActive) continuation.resume(Unit)
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
        // this method suspends here until it the continuation in the broadcastReceiver resumes it
    }

    private suspend fun onTorStarted(port: Int): String = suspendCancellableCoroutine { cont ->
        try {
            var onion: String? = null
            val controlConnection = startControlConnection().apply {
                val onionListener = RawEventListener { keyword, data ->
                    if (onion != null && keyword == EVENT_HS_DESC && data.startsWith("UPLOADED $onion")) {
                        if (cont.isActive) cont.resume("$onion.onion")
                    }
                    // TODO consider removing the logging below before release
                    LOG.debug("$keyword: $data")
                }
                addRawEventListener(onionListener)
                // create listeners as the first thing to prevent modification while already receiving events
                launchThread(true)
                authenticate(ByteArray(0))
                takeOwnership()
                setEvents(EVENTS)
            }
            onion = createOnionService(controlConnection, port)
        } catch (e: Exception) {
            // gets caught and logged by caller
            if (cont.isActive) cont.resumeWithException(e)
            else LOG.error("Error when starting Tor", e)
        }
        // this method suspends here until it the continuation in onionListener resumes it
    }

    /**
     * Creates a new onion service each time it is called
     * and returns its address (without the final .onion part).
     */
    @Throws(IOException::class)
    private fun createOnionService(
        controlConnection: TorControlConnection,
        port: Int,
    ): String {
        LOG.error("Starting hidden service...")
        val portLines = Collections.singletonMap(80, "127.0.0.1:$port")
        val response = controlConnection.addOnion("NEW:ED25519-V3", portLines, null)
        return response[HS_ADDRESS]
            ?: throw IOException("Tor did not return a hidden service address")
    }

    private fun startControlConnection(): TorControlConnection {
        val localSocketAddress = LocalSocketAddress(getControlPath(), FILESYSTEM)
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
    private fun getControlPath(): String {
        val serviceDir = app.applicationContext.getDir(TorService::class.java.simpleName, 0)
        val dataDir = File(serviceDir, "data")
        return File(dataDir, "ControlSocket").absolutePath
    }

}
