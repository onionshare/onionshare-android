package org.onionshare.android

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onionshare.android.files.FileManager
import org.onionshare.android.server.PORT
import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
import org.onionshare.android.server.WebserverManager
import org.onionshare.android.tor.TorManager
import org.onionshare.android.ui.ShareUiState
import org.slf4j.LoggerFactory.getLogger
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val LOG = getLogger(ShareManager::class.java)

@Singleton
class ShareManager @Inject constructor(
    private val app: Application,
    private val torManager: TorManager,
    private val webserverManager: WebserverManager,
    private val fileManager: FileManager,
) {

    private val _shareState = MutableStateFlow<ShareUiState>(ShareUiState.NoFiles)
    val shareState: StateFlow<ShareUiState> = _shareState

    @Volatile
    private var startSharingJob: Job? = null

    suspend fun addFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext // user backed out of select activity

        // not supporting selecting entire folders with sub-folders
        val filesAdded = fileManager.addFiles(uris, shareState.value.files)
        val totalSize = filesAdded.files.sumOf { it.size }
        _shareState.value = ShareUiState.FilesAdded(filesAdded.files, totalSize)
    }

    fun removeFile(file: SendFile) {
        val newList = shareState.value.files.filterNot { it == file }
        if (newList.isEmpty()) {
            _shareState.value = ShareUiState.NoFiles
        } else {
            val totalSize = newList.sumOf { it.size }
            _shareState.value = ShareUiState.FilesAdded(newList, totalSize)
        }
    }

    fun removeAll() {
        _shareState.value = ShareUiState.NoFiles
    }

    suspend fun onStateChangeRequested() {
        // TODO handle rapid double taps that e.g. can tear down things before they even came up
        when (shareState.value) {
            is ShareUiState.FilesAdded -> startSharing()
            is ShareUiState.Starting -> stopSharing()
            is ShareUiState.Sharing -> stopSharing()
            is ShareUiState.Complete -> startSharing()
            ShareUiState.NoFiles -> error("Sheet button should not be visible with no files")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun startSharing() {
        if (startSharingJob?.isActive == true) {
            // TODO check if this always works as expected
            startSharingJob?.cancelAndJoin()
        }
        // Attention: We'll launch sharing in Global scope, so it survives ViewModel death
        startSharingJob = GlobalScope.launch(Dispatchers.IO) {
            val files = shareState.value.files
            // call ensureActive() before any heavy work to ensure we don't continue when cancelled
            ensureActive()
            _shareState.value = ShareUiState.Starting(files, shareState.value.totalSize)
            ensureActive()
            // TODO we might want to look into parallelizing what happens below (async {} ?)
            // When the current scope gets cancelled, the async routine gets cancelled as well
            val sendPage = getSendPage(files)
            ensureActive()
            // start tor and onion service // TODO catch exceptions, handle error
            val onionAddress = torManager.start(PORT)
            val url = "http://${onionAddress}"
            LOG.error("OnionShare URL: $url") // TODO remove before release
            val sharing = ShareUiState.Sharing(files, shareState.value.totalSize, url)
            // TODO properly manage tor and webserver state together
            ensureActive()
            // collecting from StateFlow will only return when coroutine gets cancelled
            webserverManager.start(sendPage).collect {
                onWebserverStateChanged(it, sharing)
            }
        }
    }

    @Throws(IOException::class)
    private suspend fun getSendPage(files: List<SendFile>): SendPage {
        val filesReady = fileManager.zipFiles(files)
        val fileSize = filesReady.zip.length()
        return SendPage(
            fileName = "download.zip",
            fileSize = fileSize.toString(),
            fileSizeHuman = Formatter.formatShortFileSize(app.applicationContext, fileSize),
            zipFile = filesReady.zip,
        ).apply {
            addFiles(filesReady.files)
        }
    }

    private suspend fun onWebserverStateChanged(
        state: WebserverManager.State,
        sharing: ShareUiState.Sharing,
    ) = withContext(Dispatchers.IO) {
        when (state) {
            WebserverManager.State.STARTED -> _shareState.value = sharing
            WebserverManager.State.SHOULD_STOP -> stopSharing(true)
            // Stopping again could cause a harmless double stop,
            // but ensures state update when webserver stops unexpectedly.
            // In practise, we cancel the coroutine of this collector when stopping the first time,
            // so calling stopSharing() twice should actually not happen.
            WebserverManager.State.STOPPED -> stopSharing()
        }
    }

    private suspend fun stopSharing(complete: Boolean = false) = withContext(Dispatchers.IO) {
        LOG.info("Stopping sharing...")
        if (startSharingJob?.isActive == true) {
            // TODO check if this always works as expected
            startSharingJob?.cancelAndJoin()
        }

        torManager.stop()
        webserverManager.stop()
        val files = shareState.value.files
        val newState = when {
            files.isEmpty() -> ShareUiState.NoFiles
            complete -> ShareUiState.Complete(files, files.sumOf { it.size })
            else -> ShareUiState.FilesAdded(files, files.sumOf { it.size })
        }
        _shareState.value = newState
    }

}
