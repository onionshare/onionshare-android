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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import net.freehaven.tor.control.RawEventListener
import net.freehaven.tor.control.TorControlCommands.EVENT_CIRCUIT_STATUS
import net.freehaven.tor.control.TorControlCommands.EVENT_ERR_MSG
import net.freehaven.tor.control.TorControlCommands.EVENT_HS_DESC
import net.freehaven.tor.control.TorControlCommands.EVENT_STATUS_CLIENT
import net.freehaven.tor.control.TorControlCommands.EVENT_WARN_MSG
import net.freehaven.tor.control.TorControlCommands.HS_ADDRESS
import net.freehaven.tor.control.TorControlConnection
import org.onionshare.android.server.PORT
import org.slf4j.LoggerFactory.getLogger
import org.torproject.jni.TorService
import org.torproject.jni.TorService.ACTION_STATUS
import org.torproject.jni.TorService.EXTRA_SERVICE_PACKAGE_NAME
import org.torproject.jni.TorService.EXTRA_STATUS
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private val LOG = getLogger(TorManager::class.java)
private val EVENTS = listOf(
    EVENT_CIRCUIT_STATUS, // this one is needed for TorService to function
    EVENT_HS_DESC,
    EVENT_STATUS_CLIENT,
    EVENT_WARN_MSG,
    EVENT_ERR_MSG,
)
private val BOOTSTRAP_REGEX = Regex("^NOTICE BOOTSTRAP PROGRESS=([0-9]{1,3}) .*$")

@Singleton
class TorManager @Inject constructor(
    private val app: Application,
) {
    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    internal val state: StateFlow<TorState> = _state
    private var broadcastReceiver = object : BroadcastReceiver() {
        /**
         * Attention: This gets executes on UI Thread
         */
        override fun onReceive(context: Context, i: Intent) {
            val intentPackageName = i.getStringExtra(EXTRA_SERVICE_PACKAGE_NAME)
            if (intentPackageName != context.packageName) return // this intent was not for us
            when (i.getStringExtra(EXTRA_STATUS)) {
                TorService.STATUS_STARTING -> LOG.debug("TorService: Starting...")
                TorService.STATUS_ON -> {
                    LOG.debug("TorService: Started")
                    startLatch?.countDown() ?: LOG.error("startLatch was null when Tor started.")
                }
                TorService.STATUS_STOPPING -> {
                    LOG.debug("TorService: Stopping...")
                    _state.value = TorState.Stopping
                }
                TorService.STATUS_OFF -> {
                    LOG.debug("TorService: Stopped")
                    _state.value = TorState.Stopped
                }
            }
        }
    }

    @Volatile
    private var broadcastReceiverRegistered = false
    private var startLatch: CountDownLatch? = null

    @Volatile
    private var controlConnection: TorControlConnection? = null
    private val onionListener = RawEventListener { keyword, data ->
        // TODO consider removing the logging below before release
        LOG.debug("$keyword: $data")
        // bootstrapping gets 70% of our progress
        if (keyword == EVENT_STATUS_CLIENT) {
            val matchResult = BOOTSTRAP_REGEX.matchEntire(data)
            val percent = matchResult?.groupValues?.get(1)?.toIntOrNull()
            if (percent != null) {
                val progress = (percent * 0.7).roundToInt()
                val newState = (state.value as? TorState.Starting)?.copy(progress = progress)
                    ?: TorState.Starting(progress)
                _state.value = newState
            }
            return@RawEventListener
        }
        val onion = (state.value as? TorState.Starting)?.onion
        // descriptor upload counts as 90%
        if (state.value !is TorState.Started) {
            if (onion != null && keyword == EVENT_HS_DESC && data.startsWith("UPLOAD $onion")) {
                _state.value = TorState.Starting(90, onion)
            }
        }
        // We consider already the first upload of the onion descriptor as started (100%).
        // In practice, more uploads are needed for the onion service to be reachable.
        if (onion != null && keyword == EVENT_HS_DESC && data.startsWith("UPLOADED $onion")) {
            _state.value = TorState.Started(onion)
        }
    }

    /**
     * Starts [TorService] and creates a new onion service.
     * Suspends until the address of the onion service is available.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun start() = withContext(Dispatchers.IO) {
        if (state.value !is TorState.Stopped) stop()

        LOG.info("Starting...")
        _state.value = TorState.Starting(0)
        startLatch = CountDownLatch(1)
        app.registerReceiver(broadcastReceiver, IntentFilter(ACTION_STATUS))
        broadcastReceiverRegistered = true

        Intent(app, ShareService::class.java).also { intent ->
            startForegroundService(app, intent)
        }
        startLatch?.await() ?: error("startLatch was null")
        startLatch = null
        onTorServiceStarted()
    }

    fun stop() {
        LOG.info("Stopping...")
        _state.value = TorState.Stopping
        controlConnection = null
        Intent(app, ShareService::class.java).also { intent ->
            app.stopService(intent)
        }
        if (broadcastReceiverRegistered) {
            app.unregisterReceiver(broadcastReceiver)
            broadcastReceiverRegistered = false
        }
        LOG.info("Stopped")
        _state.value = TorState.Stopped
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun onTorServiceStarted() = withContext(Dispatchers.IO) {
        _state.value = TorState.Starting(5)
        controlConnection = startControlConnection().apply {
            addRawEventListener(onionListener)
            // create listeners as the first thing to prevent modification while already receiving events
            launchThread(true)
            authenticate(ByteArray(0))
            takeOwnership()
            setEvents(EVENTS)
            val onion = createOnionService()
            _state.value = TorState.Starting(10, onion)
        }
    }

    /**
     * Creates a new onion service each time it is called
     * and returns its address (without the final .onion part).
     */
    @Throws(IOException::class)
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun TorControlConnection.createOnionService(): String = withContext(Dispatchers.IO) {
        LOG.error("Starting hidden service...")
        val portLines = Collections.singletonMap(80, "127.0.0.1:$PORT")
        val response = addOnion("NEW:ED25519-V3", portLines, null)
        response[HS_ADDRESS]
            ?: throw IOException("Tor did not return a hidden service address")
    }

    @Throws(IOException::class)
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun startControlConnection(): TorControlConnection = withContext(Dispatchers.IO) {
        val localSocketAddress = LocalSocketAddress(getControlPath(), FILESYSTEM)
        val client = LocalSocket()
        client.connect(localSocketAddress)
        client.receiveBufferSize = 1024
        client.soTimeout = 3000

        val controlFileDescriptor = client.fileDescriptor
        val inputStream = FileInputStream(controlFileDescriptor)
        val outputStream = FileOutputStream(controlFileDescriptor)
        TorControlConnection(inputStream, outputStream)
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
