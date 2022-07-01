package org.onionshare.android

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onionshare.android.files.FileManager
import org.onionshare.android.files.FilesState
import org.onionshare.android.server.WebServerState
import org.onionshare.android.server.WebserverManager
import org.onionshare.android.tor.TorManager
import org.onionshare.android.tor.TorState
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
) {

    @Volatile
    private var startSharingJob: Job? = null
    private val shouldStop = MutableStateFlow(false)

    @OptIn(DelicateCoroutinesApi::class)
    val shareState: StateFlow<ShareUiState> = combineTransform(
        flow = fileManager.state,
        flow2 = torManager.state,
        flow3 = webserverManager.state,
        flow4 = shouldStop,
    ) { f, t, w, shouldStop ->
        if (LOG.isInfoEnabled) {
            val s = if (shouldStop) "stop!" else ""
            LOG.info("New state from: f-${f::class.simpleName} t-${t::class.simpleName} w-${w::class.simpleName} $s")
        }
        // initial state: Adding file and services stopped
        if (f is FilesState.Added && t is TorState.Stopped && w is WebServerState.Stopped) {
            if (f.files.isEmpty()) emit(ShareUiState.NoFiles)
            else emit(ShareUiState.FilesAdded(f.files))
        } // handle error while adding files
        else if (f is FilesState.Error) {
            emit(ShareUiState.ErrorAddingFile(f.files, f.errorFile))
            // special case handling for error state without file left
            if (f.files.isEmpty()) {
                delay(1000)
                emit(ShareUiState.NoFiles)
            }
        } // continue with zipping and report state while doing it
        else if (f is FilesState.Zipping && t is TorState.Starting) {
            val torPercent = (t as? TorState.Starting)?.progress ?: 0
            emit(ShareUiState.Starting(f.files, f.progress, torPercent))
        } // after zipping is complete, and webserver still stopped, start it
        else if (f is FilesState.Zipped && !shouldStop &&
            (t is TorState.Starting || t is TorState.Started) && w is WebServerState.Stopped
        ) {
            webserverManager.start(f.sendPage)
            val torPercent = (t as? TorState.Starting)?.progress ?: 0
            emit(ShareUiState.Starting(f.files, 100, torPercent))
        } // continue to report Tor progress after files are zipped
        else if (f is FilesState.Zipped && t is TorState.Starting) {
            emit(ShareUiState.Starting(f.files, 100, t.progress))
        } // everything is done, show sharing state with onion address
        else if (f is FilesState.Zipped && t is TorState.Started && w is WebServerState.Started) {
            val url = "http://${t.onion}.onion"
            emit(ShareUiState.Sharing(f.files, url))
        } // if webserver says download is complete, report that back
        else if (w is WebServerState.DownloadComplete) {
            stopSharing()
            emit(ShareUiState.Complete(f.files))
        } // handle stopping state
        else if (t is TorState.Stopping) {
            emit(ShareUiState.Stopping(f.files))
        } // handle unexpected stopping/stopped only after zipped, because we start webserver only when that happens
        else if (!shouldStop && f is FilesState.Zipped &&
            (t is TorState.Stopping || t is TorState.Stopped || w is WebServerState.Stopped)
        ) {
            emit(ShareUiState.Error(f.files))
        } else {
            LOG.error("Unhandled state: ↑")
        }
    }.distinctUntilChanged().onEach {
        LOG.debug("New state: ${it::class.simpleName}")
    }.stateIn(GlobalScope, SharingStarted.Lazily, ShareUiState.NoFiles)

    suspend fun onStateChangeRequested() = when (shareState.value) {
        is ShareUiState.FilesAdded -> startSharing()
        is ShareUiState.Starting -> stopSharing()
        is ShareUiState.Sharing -> stopSharing()
        is ShareUiState.Complete -> startSharing()
        is ShareUiState.ErrorAddingFile -> startSharing()
        is ShareUiState.Error -> startSharing()
        is ShareUiState.Stopping -> error("Pressing sheet button while stopping should not be possible")
        is ShareUiState.NoFiles -> error("Sheet button should not be visible with no files")
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun startSharing() {
        if (startSharingJob?.isActive == true) {
            // TODO check if this always works as expected
            startSharingJob?.cancelAndJoin()
        }
        shouldStop.value = false
        // Attention: We'll launch sharing in Global scope, so it survives ViewModel death
        startSharingJob = coroutineScope {
            launch(Dispatchers.IO) {
                // call ensureActive() before any heavy work to ensure we don't continue when cancelled
                ensureActive()
                // When the current scope gets cancelled, the async routine gets cancelled as well
                val fileTask = async { fileManager.zipFiles() }
                // start tor and onion service
                val torTask = async { torManager.start() }
                fileTask.await()
                torTask.await()
            }
        }
    }

    private suspend fun stopSharing() = withContext(Dispatchers.IO) {
        shouldStop.value = true
        LOG.info("Stopping sharing...")
        if (startSharingJob?.isActive == true) {
            // TODO check if this always works as expected
            startSharingJob?.cancelAndJoin()
        }
        startSharingJob = null

        if (torManager.state.value !is TorState.Stopped) torManager.stop()
        if (webserverManager.state.value !is WebServerState.Stopped) webserverManager.stop()
        fileManager.stop()
    }

}
