package org.onionshare.android.tor

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.content.IntentFilter
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.LocalSocketAddress.Namespace.FILESYSTEM
import androidx.annotation.RawRes
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat.startForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.freehaven.tor.control.RawEventListener
import net.freehaven.tor.control.TorControlCommands.EVENT_CIRCUIT_STATUS
import net.freehaven.tor.control.TorControlCommands.EVENT_ERR_MSG
import net.freehaven.tor.control.TorControlCommands.EVENT_HS_DESC
import net.freehaven.tor.control.TorControlCommands.EVENT_STATUS_CLIENT
import net.freehaven.tor.control.TorControlCommands.EVENT_WARN_MSG
import net.freehaven.tor.control.TorControlCommands.HS_ADDRESS
import net.freehaven.tor.control.TorControlConnection
import org.briarproject.moat.MoatApi
import org.onionshare.android.BuildConfig
import org.onionshare.android.R
import org.onionshare.android.server.PORT
import org.onionshare.android.ui.settings.SettingsManager
import org.slf4j.LoggerFactory.getLogger
import org.torproject.jni.TorService
import org.torproject.jni.TorService.ACTION_ERROR
import org.torproject.jni.TorService.ACTION_STATUS
import org.torproject.jni.TorService.EXTRA_SERVICE_PACKAGE_NAME
import org.torproject.jni.TorService.EXTRA_STATUS
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MINUTES
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
private val DEBUG_PARAMS_OBFS4 = if (BuildConfig.DEBUG) " -enableLogging -logLevel INFO" else ""
private val DEBUG_PARAMS_SNOWFLAKE = if (BuildConfig.DEBUG) " -log-to-state-dir -log snowlog" else ""
private val TOR_START_TIMEOUT_SINCE_START = MINUTES.toMillis(5)
private val TOR_START_TIMEOUT_SINCE_LAST_PROGRESS = MINUTES.toMillis(2)
private const val MOAT_URL = "https://onion.azureedge.net/"
private const val MOAT_FRONT = "ajax.aspnetcdn.com"

@Singleton
class TorManager @Inject constructor(
    private val app: Application,
    private val settingsManager: SettingsManager,
    private val executableManager: ExecutableManager,
    private val locationUtils: AndroidLocationUtils,
) {
    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    internal val state = _state.asStateFlow()
    private val broadcastReceiver = object : BroadcastReceiver() {
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
                    onStopped()
                }
            }
        }
    }
    private val errorReceiver = object : BroadcastReceiver() {
        @UiThread
        override fun onReceive(context: Context, i: Intent) {
            LOG.error("TorService Error: ${i.getStringExtra(EXTRA_TEXT)}")
        }
    }

    private val obfs4Bridges: List<String> = getResourceLines(R.raw.obfs4)
    private val meekBridges: List<String> = getResourceLines(R.raw.meek)
    private val snowflakeBridges: List<String>
        get() = getResourceLines(R.raw.snowflake).map { "$it $snowflakeParams" }
    private val snowflakeParams: String by lazy {
        app.resources.openRawResource(R.raw.snowflake_params).reader().useLines {
            return@lazy it.first()
        }
    }

    @Volatile
    private var broadcastReceiverRegistered = false

    @Volatile
    private var localSocket: LocalSocket? = null
    private var startLatch: CountDownLatch? = null
    private var startCheckJob: Job? = null

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
                changeStartingState(progress)
            }
            return@RawEventListener
        }
        val onion = (state.value as? TorState.Starting)?.onion
        // descriptor upload counts as 90%
        if (state.value !is TorState.Started) {
            if (onion != null && keyword == EVENT_HS_DESC && data.startsWith("UPLOAD $onion")) {
                changeStartingState(90, onion)
            }
        }
        // We consider already the first upload of the onion descriptor as started (100%).
        // In practice, more uploads are needed for the onion service to be reachable.
        if (onion != null && keyword == EVENT_HS_DESC && data.startsWith("UPLOADED $onion")) {
            onDescriptorUploaded(onion)
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
        val now = System.currentTimeMillis()
        _state.value = TorState.Starting(progress = 0, startTime = now, lastProgressTime = now)
        startLatch = CountDownLatch(1)
        app.registerReceiver(broadcastReceiver, IntentFilter(ACTION_STATUS))
        app.registerReceiver(errorReceiver, IntentFilter(ACTION_ERROR))
        broadcastReceiverRegistered = true

        Intent(app, ShareService::class.java).also { intent ->
            startForegroundService(app, intent)
        }
        try {
            executableManager.installObfs4Executable()
            executableManager.installSnowflakeExecutable()
            startLatch?.await() ?: error("startLatch was null")
            startLatch = null
            onTorServiceStarted()
        } catch (e: Exception) {
            LOG.warn("Error while starting Tor: ", e)
            stop()
        }
    }

    fun stop() {
        LOG.info("Stopping...")
        startCheckJob?.cancel()
        startCheckJob = null
        _state.value = TorState.Stopping
        controlConnection = null
        Intent(app, ShareService::class.java).also { intent ->
            app.stopService(intent)
        }
        // wait for service to stop and broadcast when it gets destroyed
    }

    @UiThread
    fun onStopped() {
        if (broadcastReceiverRegistered) {
            app.unregisterReceiver(broadcastReceiver)
            app.unregisterReceiver(errorReceiver)
            broadcastReceiverRegistered = false
        }
        localSocket = null
        LOG.info("Stopped")
        _state.value = TorState.Stopped
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun onTorServiceStarted() = withContext(Dispatchers.IO) {
        changeStartingState(5)
        val autoMode = settingsManager.automaticBridges.value
        controlConnection = startControlConnection().apply {
            addRawEventListener(onionListener)
            // create listeners as the first thing to prevent modification while already receiving events
            launchThread(true)
            authenticate(ByteArray(0))
            takeOwnership()
            setEvents(EVENTS)
            val obfs4Executable = executableManager.obfs4Executable.absolutePath
            val snowflakeExecutable = executableManager.snowflakeExecutable.absolutePath
            val conf = mutableListOf(
                "ClientTransportPlugin obfs4 exec ${obfs4Executable}$DEBUG_PARAMS_OBFS4",
                "ClientTransportPlugin meek_lite exec ${obfs4Executable}$DEBUG_PARAMS_OBFS4",
                "ClientTransportPlugin snowflake exec ${snowflakeExecutable}$DEBUG_PARAMS_SNOWFLAKE",
            )
            if (!autoMode) {
                val customBridges = settingsManager.customBridges.value
                if (customBridges.isNotEmpty()) {
                    LOG.info("Using ${customBridges.size} custom bridges...")
                    conf += listOf("UseBridges 1") + customBridges.map { "Bridge $it" }
                }
            }
            setConf(conf)
            val onion = createOnionService()
            changeStartingState(10, onion)
        }
        if (autoMode) {
            startCheckJob = launch {
                LOG.info("Starting check job")
                checkStartupProgress()
                LOG.info("Check job finished")
            }
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
        val response = addOnion("NEW:ED25519-V3", portLines, listOf("DiscardPK"))
        response[HS_ADDRESS]
            ?: throw IOException("Tor did not return a hidden service address")
    }

    @Throws(IOException::class)
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun startControlConnection(): TorControlConnection = withContext(Dispatchers.IO) {
        val localSocketAddress = LocalSocketAddress(getControlPath(), FILESYSTEM)
        val client = LocalSocket()
        localSocket = client
        client.connect(localSocketAddress)

        val controlFileDescriptor = client.fileDescriptor
        val inputStream = FileInputStream(controlFileDescriptor)
        val outputStream = FileOutputStream(controlFileDescriptor)
        TorControlConnection(inputStream, outputStream)
    }

    private fun changeStartingState(progress: Int, onion: String? = null) {
        val oldStartingState = state.value as? TorState.Starting
        if (oldStartingState == null) LOG.warn("Old state was not Starting, but ${state.value}")
        val now = System.currentTimeMillis()
        val newState = if (onion == null) oldStartingState?.copy(progress = progress, lastProgressTime = now)
        else oldStartingState?.copy(progress = progress, onion = onion, lastProgressTime = now)
        _state.value = newState ?: TorState.Starting(
            progress = progress,
            startTime = now,
            lastProgressTime = now,
            onion = onion,
        )
    }

    private fun onDescriptorUploaded(onion: String) {
        _state.value = TorState.Started(onion)
        startCheckJob?.cancel()
        startCheckJob = null
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

    private suspend fun checkStartupProgress() {
        // first we try to start Tor without bridges
        if (waitForTorToStart()) return
        LOG.info("Getting bridges from Moat...")
        val bridges: List<String>? = try {
            getBridgesFromMoat()
        } catch (e: IOException) {
            LOG.warn("Error getting bridges from moat: ", e)
            null
        }
        // if Tor finished starting while we were getting bridges from Moat then we don't need them
        if (state.value !is TorState.Starting) return
        // try bridges we got from Moat, if any
        if (!bridges.isNullOrEmpty()) {
            LOG.info("Using bridges from Moat...")
            useBridges(bridges)
            if (waitForTorToStart()) return
        }
        // use built-in bridges
        LOG.info("Using built-in bridges...")
        useBridges(meekBridges + snowflakeBridges + obfs4Bridges)
        if (waitForTorToStart()) return
        // TODO: let the user know that we can't connect and they need to get some custom bridges
        // let's try without bridges again, just in case
        LOG.info("Using no bridges...")
        controlConnection?.setConf(listOf("UseBridges 0"))
        controlConnection?.resetConf(listOf("Bridges"))
    }

    /**
     * Waits for [state] to become something other than [TorState.Starting].
     *
     * @return True if [state] became something other than [TorState.Starting], or false if startup took more than
     * [TOR_START_TIMEOUT_SINCE_START] ms or failed to make progress for more than
     * [TOR_START_TIMEOUT_SINCE_LAST_PROGRESS] ms.
     */
    private suspend fun waitForTorToStart(): Boolean {
        LOG.info("Waiting for Tor to start...")
        // Measure TOR_START_TIMEOUT_SINCE_START from the time when this method was called, rather than the time
        // when Tor was started, otherwise if one connection method times out then all subsequent methods will be
        // considered to have timed out too
        val start = System.currentTimeMillis()
        while (true) {
            val s = state.value
            if (s !is TorState.Starting) return true
            val now = System.currentTimeMillis()
            if (now - start > TOR_START_TIMEOUT_SINCE_START) {
                LOG.info("Tor is taking too long to start")
                return false
            }
            if (now - s.lastProgressTime > TOR_START_TIMEOUT_SINCE_LAST_PROGRESS) {
                LOG.info("Tor startup is not making progress")
                return false
            }
            delay(1_000)
        }
    }

    private fun getBridgesFromMoat(): List<String> {
        val stateDir = app.getDir("state", MODE_PRIVATE)
        val moat = MoatApi(executableManager.obfs4Executable, stateDir, MOAT_URL, MOAT_FRONT)
        val bridges = moat.get().let {
            // if response was empty, try it again with what we think the country should be
            if (it.isEmpty()) moat.getWithCountry(locationUtils.currentCountryIso)
            else it
        }
        return bridges.flatMap { bridge ->
            bridge.bridgeStrings.map {
                // add snowflake params manually, if they are missing
                val line = if (bridge.type == "snowflake" && !it.contains("url=")) {
                    "$it $snowflakeParams"
                } else it
                "Bridge $line"
            }
        }
    }

    private fun useBridges(bridges: List<String>) {
        if (LOG.isInfoEnabled) {
            LOG.info("Using bridges:")
            bridges.forEach { LOG.info("  $it") }
        }
        val conf = listOf(
            "UseBridges 1",
        ) + bridges
        controlConnection?.setConf(conf)
    }

    private fun getResourceLines(@RawRes res: Int): List<String> {
        return app.resources.openRawResource(res).reader().use { reader ->
            reader.readLines()
        }
    }

}
