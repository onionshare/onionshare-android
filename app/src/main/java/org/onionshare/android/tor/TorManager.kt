package org.onionshare.android.tor

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.core.content.ContextCompat.startForegroundService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.briarproject.onionwrapper.CircumventionProvider
import org.briarproject.onionwrapper.LocationUtils
import org.briarproject.onionwrapper.TorWrapper
import org.briarproject.onionwrapper.TorWrapper.TorState.CONNECTED
import org.briarproject.onionwrapper.TorWrapper.TorState.STOPPED
import org.onionshare.android.Clock
import org.onionshare.android.DefaultClock
import org.onionshare.android.ui.settings.SettingsManager
import org.slf4j.LoggerFactory.getLogger
import java.io.IOException
import java.util.Locale.US
import java.util.concurrent.TimeUnit.MINUTES
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.reflect.KClass

private val LOG = getLogger(TorManager::class.java)
private val TOR_START_TIMEOUT_SINCE_START = MINUTES.toMillis(5)
private val TOR_START_TIMEOUT_SINCE_LAST_PROGRESS = MINUTES.toMillis(2)

@Singleton
class TorManager(
    private val app: Application,
    private val tor: TorWrapper,
    private val settingsManager: SettingsManager,
    private val circumventionProvider: CircumventionProvider,
    private val locationUtils: LocationUtils,
    private val moatApiFactory: MoatApiFactory,
    private val clock: Clock,
    private val dispatcher: CoroutineContext,
) : TorWrapper.Observer {

    @Inject
    constructor(
        app: Application,
        tor: TorWrapper,
        settingsManager: SettingsManager,
        circumventionProvider: CircumventionProvider,
        locationUtils: LocationUtils,
    ) : this(
        app = app,
        tor = tor,
        settingsManager = settingsManager,
        circumventionProvider = circumventionProvider,
        locationUtils = locationUtils,
        moatApiFactory = DefaultMoatApiFactory,
        clock = DefaultClock,
        dispatcher = Dispatchers.IO
    )

    /**
     * Attention: Only use [updateTorState] to update this state.
     */
    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    internal val state = _state.asStateFlow()

    private var startCheckJob: Job? = null

    init {
        tor.setObserver(this@TorManager)
    }

    /**
     * Updates the [_state] with the given new [state] preventing concurrent modifications.
     * The state only gets updated when [_state] was in [expectedState].
     *
     * Note that the underlying [MutableStateFlow] may reject updates that are equal to the previous state.
     *
     * @return true if the expected state was either null or matched the previous state.
     */
    @Synchronized
    private fun updateTorState(expectedState: KClass<*>?, newState: TorState, warn: Boolean = true): Boolean {
        if (expectedState != null && _state.value::class != expectedState) {
            if (warn) LOG.warn("Expected state $expectedState, but was ${state.value}")
            return false
        }
        _state.value = newState
        return true
    }

    /**
     * Starts [ShareService] and creates a new onion service.
     * Suspends until the address of the onion service is available.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Throws(IOException::class, IllegalArgumentException::class, InterruptedException::class)
    suspend fun start() = withContext(Dispatchers.IO) {
        LOG.info("Starting...")
        val now = clock.currentTimeMillis()
        updateTorState(null, TorState.Starting(progress = 0, lastProgressTime = now))
        Intent(app, ShareService::class.java).also { intent ->
            startForegroundService(app, intent)
        }

        tor.start()
        changeStartingState(5)
        if (settingsManager.automaticBridges.value) {
            // start the check job in global scope, so this method can return without waiting for it
            startCheckJob = GlobalScope.launch(dispatcher) {
                LOG.info("Starting check job")
                checkStartupProgress()
                LOG.info("Check job finished")
            }
        } else {
            val customBridges = settingsManager.customBridges.value
            if (customBridges.isNotEmpty()) {
                LOG.info("Using ${customBridges.size} custom bridges...")
                tor.enableBridges(customBridges.map { "Bridge $it" })
            }
        }
        tor.enableNetwork(true)
    }

    fun stop() {
        if (updateTorState(TorState.Stopping::class, TorState.Stopping, warn = false)) {
            LOG.info("Was already stopping. Not stopping again.")
        } else {
            LOG.info("Stopping...")
            startCheckJob?.cancel()
            startCheckJob = null
            try {
                tor.stop()
            } catch (e: Exception) {
                LOG.warn("Error stopping Tor: ", e)
            }
            Intent(app, ShareService::class.java).also { intent ->
                app.stopService(intent)
            }
        }
    }

    override fun onState(s: TorWrapper.TorState) {
        LOG.info("new state: $s")
        if (s == CONNECTED) updateTorState(TorState.Starting::class, TorState.Started)
        else if (s == STOPPED) updateTorState(null, TorState.Stopped)
    }

    @Throws(IOException::class)
    fun publishOnionService(port: Int) {
        LOG.info("Starting hidden service...")
        tor.publishHiddenService(port, 80, null)
    }

    override fun onBootstrapPercentage(percentage: Int) {
        changeStartingState(5 + (percentage * 0.9).roundToInt())
    }

    override fun onHsDescriptorUpload(onion: String) {
        if (updateTorState(TorState.Started::class, TorState.Published(onion), warn = false)) {
            startCheckJob?.cancel()
            startCheckJob = null
        }
    }

    override fun onClockSkewDetected(skewSeconds: Long) {
        // TODO
    }

    private fun changeStartingState(progress: Int) {
        val newState = TorState.Starting(
            progress = progress,
            lastProgressTime = clock.currentTimeMillis(),
        )
        updateTorState(TorState.Starting::class, newState)
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
            try {
                useBridges(bridges)
            } catch (e: IOException) {
                LOG.error("Error setting bridges: ", e)
                stop()
                return
            }
            if (waitForTorToStart()) return
        } else {
            LOG.info("No bridges received from Moat. Continuing...")
        }
        // use built-in bridges
        LOG.info("Using built-in bridges...")
        val countryCode = locationUtils.currentCountry
        val builtInBridges = circumventionProvider.getSuitableBridgeTypes(countryCode).flatMap { type ->
            circumventionProvider.getBridges(type, countryCode)
        }
        try {
            useBridges(builtInBridges)
        } catch (e: IOException) {
            LOG.error("Error setting bridges: ", e)
            stop()
            return
        }
        if (waitForTorToStart()) return
        LOG.info("Could not connect to Tor")
        updateTorState(TorState.Starting::class, TorState.FailedToConnect)
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
        val start = clock.currentTimeMillis()
        val oldState = state.value as? TorState.Starting ?: return true
        // reset time of last progress to current as well to measure progress since last changing bridge settings
        if (!updateTorState(TorState.Starting::class, oldState.copy(lastProgressTime = start))) {
            return true
        }
        while (true) {
            val s = state.value
            if (s !is TorState.Starting) return true
            val now = clock.currentTimeMillis()
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
        val moat = moatApiFactory.createMoatApi(
            lyrebirdExecutable = tor.lyrebirdExecutableFile,
            lyrebirdDir = app.getDir("state", MODE_PRIVATE)
        )
        val bridges = moat.get().let {
            // if response was empty, try it again with what we think the country should be
            if (it.isEmpty()) moat.getWithCountry(locationUtils.currentCountry.lowercase(US))
            else it
        }
        return bridges.flatMap { bridge ->
            bridge.bridgeStrings.map { line ->
                "Bridge $line"
            }
        }.distinct()
    }

    @Throws(IOException::class)
    private fun useBridges(bridges: List<String>) {
        if (LOG.isInfoEnabled) {
            LOG.info("Using bridges:")
            bridges.forEach { LOG.info("  $it") }
        }
        tor.enableBridges(bridges)
    }

}
