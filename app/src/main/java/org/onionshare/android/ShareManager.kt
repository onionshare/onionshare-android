package org.onionshare.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onionshare.android.files.FileManager
import org.onionshare.android.files.ZipResult
import org.onionshare.android.files.ZipState
import org.onionshare.android.server.WebServerState
import org.onionshare.android.server.WebserverManager
import org.onionshare.android.tor.TorManager
import org.onionshare.android.tor.TorState
import org.onionshare.android.ui.OnionNotificationManager
import org.onionshare.android.ui.share.ShareUiState
import org.slf4j.LoggerFactory.getLogger
import javax.inject.Inject
import javax.inject.Singleton

private val LOG = getLogger(ShareManager::class.java)

@Singleton
class ShareManager @Inject constructor(
    private val fileManager: FileManager,
    private val torManager: TorManager,
    private val webserverManager: WebserverManager,
    private val notificationManager: OnionNotificationManager,
) {

    @Volatile
    private var startSharingJob: Job? = null

    val filesState = fileManager.filesState
    private val _shareState = MutableStateFlow<ShareUiState>(ShareUiState.AddingFiles)
    val shareState: StateFlow<ShareUiState> = _shareState.asStateFlow()

    suspend fun onStateChangeRequested() = when (shareState.value) {
        is ShareUiState.AddingFiles -> startSharing()
        is ShareUiState.Starting -> stopSharing()
        is ShareUiState.Sharing -> stopSharing()
        is ShareUiState.Complete -> startSharing()
        is ShareUiState.ErrorAddingFile -> startSharing()
        is ShareUiState.ErrorStarting -> startSharing()
        is ShareUiState.Stopping -> error("Pressing sheet button while stopping should not be possible")
    }

    private suspend fun startSharing() {
        if (startSharingJob?.isActive == true) {
            startSharingJob?.cancelAndJoin()
        }
        _shareState.value = ShareUiState.Starting(0, 0)
        // Attention: We'll launch sharing in Global scope, so it survives ViewModel death,
        // because this gets called implicitly by the ViewModel in ViewModelScope
        @Suppress("OPT_IN_USAGE")
        startSharingJob = GlobalScope.launch(Dispatchers.IO) {
            coroutineScope mainScope@{
                fun stopOnError(error: ShareUiState.Error) {
                    notificationManager.onError()
                    _shareState.value = error
                    // stop in a new scope to not cause deadlock when waiting for startSharingJob to complete
                    GlobalScope.launch {
                        stopSharing(error)
                    }
                    this@mainScope.cancel()
                }
                // call ensureActive() before any heavy work to ensure we don't continue when cancelled
                ensureActive()
                // When the current scope gets cancelled, the async routine gets cancelled as well
                val fileTask = async { fileManager.zipFiles() }
                // start tor and onion service
                val torTask = async {
                    try {
                        torManager.start()
                    } catch (e: Exception) {
                        LOG.error("Error starting Tor: ", e)
                        if (e !is CancellationException) {
                            stopOnError(ShareUiState.ErrorStarting(errorMsg = e.toString()))
                        }
                    }
                }
                // wait for tor.start() to return before starting to observe, actual startup happens async
                torTask.await()
                LOG.info("Tor task returned")
                // start progress observer task
                val observerTask = async {
                    LOG.info("Starting Observer task...")
                    fileManager.zipState.combine(torManager.state) { zipState, torState ->
                        onStarting(zipState, torState)
                    }.transformWhile { shareUiState ->
                        emit(shareUiState)
                        // only continue collecting while we are starting (otherwise would never stop collecting)
                        shareUiState is ShareUiState.Starting
                    }.collect { shareUiState ->
                        LOG.info("New share state: $shareUiState")
                        _shareState.value = shareUiState
                        if (shareUiState is ShareUiState.Error) stopOnError(shareUiState)
                    }
                    LOG.info("Observer task finished.")
                }
                ensureActive()
                LOG.info("Awaiting file task...")
                when (val zipResult = fileTask.await()) {
                    is ZipResult.Zipped -> {
                        val port = webserverManager.start(zipResult.sendPage)
                        torManager.publishOnionService(port)
                        observerTask.await()
                    }

                    is ZipResult.Error -> {
                        stopOnError(ShareUiState.ErrorAddingFile(zipResult.errorFile))
                    }
                }
            }
        }
    }

    private fun onStarting(zipState: ZipState?, torState: TorState): ShareUiState {
        return when (torState) {
            is TorState.Starting -> {
                // Tor stays in Starting state as long as the HS descriptor wasn't published.
                val torPercent = (torState as? TorState.Starting)?.progress ?: 0
                ShareUiState.Starting(zipState?.progress ?: 0, torPercent)
            }

            is TorState.Published -> {
                // We only create the hidden service after files have been zipped and webserver was started,
                // so we are in sharing state once the first HS descriptor has been published.
                notificationManager.onSharing()
                ShareUiState.Sharing(torState.onion)
            }

            TorState.FailedToConnect -> {
                ShareUiState.ErrorStarting(true)
            }

            TorState.Stopping -> error("Still observing TorState after calling stop().")

            TorState.Stopped -> {
                ShareUiState.ErrorStarting(errorMsg = "Tor stopped unexpectedly.")
            }
        }
    }

    private suspend fun stopSharing(errorState: ShareUiState.Error? = null) = withContext(Dispatchers.IO) {
        LOG.info("Stopping sharing...")
        _shareState.value = ShareUiState.Stopping
        if (startSharingJob?.isActive == true) {
            LOG.info("Wait for start job to finish...")
            startSharingJob?.cancelAndJoin()
            LOG.info("Start job to finished.")
        }
        startSharingJob = null

        torManager.stop()
        if (webserverManager.state.value !is WebServerState.Stopped) webserverManager.stop()
        fileManager.stop()
        notificationManager.onStopped()

        _shareState.value = errorState ?: ShareUiState.AddingFiles
    }

}
