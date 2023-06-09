package org.onionshare.android

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

//    @OptIn(DelicateCoroutinesApi::class)
//    val shareState: StateFlow<ShareUiState> = combineTransform(
//        flow = fileManager.state,
//        flow2 = torManager.state,
//        flow3 = webserverManager.state,
//        flow4 = shouldStop,
//    ) { f, t, w, shouldStop ->
//        if (LOG.isInfoEnabled) {
//            val s = if (shouldStop) "stop!" else ""
//            LOG.info("New state from: f-${f::class.simpleName} t-${t::class.simpleName} w-${w::class.simpleName} $s")
//        }
//        // initial state: Adding file and services stopped
//        if (f is FilesState.Added && t is TorState.Stopped && w is WebServerState.Stopped && !w.downloadComplete) {
//            if (f.files.isEmpty()) emit(ShareUiState.NoFiles)
//            else emit(ShareUiState.FilesAdded(f.files))
//        } // handle error while adding files while Tor is still starting or started
//        else if (f is FilesState.Error && (t is TorState.Starting || t is TorState.Published)) {
//            stopSharing()
//        } // handle error while adding files when Tor has stopped
//        else if (f is FilesState.Error && t is TorState.Stopped) {
//            // TODO notify the user when the app is not displayed
//            emit(ShareUiState.ErrorAddingFile(f.files, f.errorFile))
//            // special case handling for error state without file left
//            if (f.files.isEmpty()) {
//                delay(1000)
//                emit(ShareUiState.NoFiles)
//            }
//        } // continue with zipping and report state while doing it
//        else if (f is FilesState.Zipping && t is TorState.Starting) {
//            val torPercent = (t as? TorState.Starting)?.progress ?: 0
//            emit(ShareUiState.Starting(f.files, f.progress, torPercent))
//        } // after zipping is complete, and webserver still stopped, start it
//        else if (f is FilesState.Zipped && !shouldStop &&
//            (t is TorState.Starting || t is TorState.Published) && w is WebServerState.Stopped
//        ) {
//            webserverManager.start(f.sendPage)
//            val torPercent = (t as? TorState.Starting)?.progress ?: 0
//            emit(ShareUiState.Starting(f.files, 100, torPercent))
//        } // continue to report Tor progress after files are zipped
//        else if (f is FilesState.Zipped && t is TorState.Starting) {
//            emit(ShareUiState.Starting(f.files, 100, t.progress))
//        } // everything is done, show sharing state with onion address
//        else if (f is FilesState.Zipped && t is TorState.Published && w is WebServerState.Started) {
//            val url = "http://${t.onion}.onion"
//            emit(ShareUiState.Sharing(f.files, url))
//            notificationManager.onSharing()
//        } // if webserver says download is complete, report that back
//        else if (w is WebServerState.Stopping && w.downloadComplete) {
//            this@ShareManager.shouldStop.value = true
//        } // wait with stopping Tor until download has really completed
//        else if (w is WebServerState.Stopped && w.downloadComplete) {
//            stopSharing()
//            emit(ShareUiState.Complete(f.files))
//        } // handle stopping state
//        else if (t is TorState.Stopping) {
//            emit(ShareUiState.Stopping(f.files))
//        } // handle unexpected stopping/stopped only after zipped, because we start webserver only when that happens
//        else if (!shouldStop && f is FilesState.Zipped && (t is TorState.Stopped || w is WebServerState.Stopped)
//        ) {
//            notificationManager.onError()
//            val torFailed = (t as? TorState.Stopping)?.failedToConnect == true ||
//                (t as? TorState.Stopped)?.failedToConnect == true
//            LOG.info("Tor failed: $torFailed")
//            emit(ShareUiState.Error(f.files, torFailed))
//            // state hack to ensure the webserver also stops when tor fails, so we add files again
//            if (webserverManager.state.value !is WebServerState.Stopped) webserverManager.stop()
//        } else {
//            LOG.error("Unhandled state: ↑")
//        }
//    }.distinctUntilChanged().onEach {
//        LOG.debug("New state: ${it::class.simpleName}")
//    }.stateIn(GlobalScope, SharingStarted.Lazily, ShareUiState.NoFiles)

    suspend fun onStateChangeRequested() = when (shareState.value) {
        is ShareUiState.AddingFiles -> startSharing()
        is ShareUiState.Starting -> stopSharing()
        is ShareUiState.Sharing -> stopSharing()
        is ShareUiState.Complete -> startSharing()
        is ShareUiState.ErrorAddingFile -> startSharing()
        is ShareUiState.Error -> startSharing()
        is ShareUiState.Stopping -> error("Pressing sheet button while stopping should not be possible")
    }

    private suspend fun startSharing() {
        if (startSharingJob?.isActive == true) {
            // TODO check if this always works as expected
            startSharingJob?.cancelAndJoin()
        }
        _shareState.value = ShareUiState.Starting(0, 0)
        // the ErrorAddingFile state is transient and needs manual reset to not persist
        // TODO test
//        if (shareState.value is ShareUiState.ErrorAddingFile) fileManager.resetError()
        // Attention: We'll launch sharing in Global scope, so it survives ViewModel death,
        // because this gets called implicitly by the ViewModel in ViewModelScope
        @Suppress("OPT_IN_USAGE")
        startSharingJob = GlobalScope.launch(Dispatchers.IO) {
            coroutineScope mainScope@{
                fun stopOnError(error: ShareUiState.Error) {
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
                        stopOnError(ShareUiState.Error(errorMsg = e.toString()))
                    }
                }
                // wait for tor.start() to return before starting to observe, actual startup happens async
                torTask.await()
                LOG.info("Tor task returned")
                // start progress observer task
                val observerTask = async {
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
                val zipResult = fileTask.await()
                if (zipResult is ZipResult.Zipped) {
                    val port = webserverManager.start(zipResult.sendPage)
                    torManager.publishOnionService(port)
                    observerTask.await()
                } else if (zipResult is ZipResult.Error) {
                    // TODO handle zipResult.errorFile
                    stopOnError(ShareUiState.Error())
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
                ShareUiState.Sharing(torState.onion)
            }

            TorState.FailedToConnect -> {
                ShareUiState.Error(true)
            }

            TorState.Stopped -> {
                ShareUiState.Error(errorMsg = "Tor stopped unexpectedly.")
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

        if (torManager.state.value !is TorState.Stopped) torManager.stop()
        if (webserverManager.state.value !is WebServerState.Stopped) webserverManager.stop()
        fileManager.stop()
        notificationManager.onStopped()

        _shareState.value = errorState ?: ShareUiState.AddingFiles
    }

}
