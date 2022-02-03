package org.onionshare.android

import android.app.Application
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.text.format.Formatter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onionshare.android.files.FileErrorException
import org.onionshare.android.files.FileManager
import org.onionshare.android.files.FilesZipping
import org.onionshare.android.files.totalSize
import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
import org.onionshare.android.server.WebserverManager
import org.onionshare.android.tor.TorManager
import org.onionshare.android.tor.TorState
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

    suspend fun addFiles(uris: List<Uri>, takePermission: Boolean) = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext // user backed out of select activity

        // taking persistable permissions only works with OPEN_DOCUMENT, not GET_CONTENT
        if (takePermission) {
            // take persistable Uri permission to prevent SecurityException
            // when activity got killed before we use the Uri
            val contentResolver = app.applicationContext.contentResolver
            uris.forEach { uri ->
                contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        // not supporting selecting entire folders with sub-folders
        val filesAdded = fileManager.addFiles(uris, shareState.value.files)
        _shareState.value = ShareUiState.FilesAdded(filesAdded.files, filesAdded.files.totalSize)
    }

    fun removeFile(file: SendFile) {
        // release persistable Uri permission again
        file.releaseUriPermission()

        val newList = shareState.value.files.filterNot { it == file }
        if (newList.isEmpty()) {
            _shareState.value = ShareUiState.NoFiles
        } else {
            _shareState.value = ShareUiState.FilesAdded(newList)
        }
    }

    fun removeAll() {
        // release persistable Uri permissions again
        shareState.value.files.forEach { file ->
            file.releaseUriPermission()
        }
        _shareState.value = ShareUiState.NoFiles
    }

    suspend fun onStateChangeRequested() = when (shareState.value) {
        is ShareUiState.FilesAdded -> startSharing()
        is ShareUiState.Starting -> stopSharing()
        is ShareUiState.Sharing -> stopSharing()
        is ShareUiState.Complete -> startSharing()
        is ShareUiState.Error -> startSharing()
        ShareUiState.NoFiles -> error("Sheet button should not be visible with no files")
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("BlockingMethodInNonBlockingContext")
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
            _shareState.value = ShareUiState.Starting(files, shareState.value.totalSize, 0, 0)
            try {
                // TODO we might want to look into parallelizing what happens below (async {} ?)
                // When the current scope gets cancelled, the async routine gets cancelled as well
                ensureActive()
                var sendPage: SendPage? = null
                fileManager.zipFiles(files).collect { state ->
                    ensureActive()
                    _shareState.value = ShareUiState.Starting(files, shareState.value.totalSize, state.progress, 0)
                    if (state.complete) sendPage = getSendPage(state)
                }
                val page = sendPage ?: error("SendPage was null")
                ensureActive()
                // start tor and onion service
                torManager.start()
                var onion: String? = null
                torManager.state.takeWhile { it !is TorState.Started }.collect { state ->
                    if (state is TorState.Starting) {
                        _shareState.value =
                            ShareUiState.Starting(files, shareState.value.totalSize, 100, state.progress)
                        onion = state.onion
                    }
                }
                _shareState.value = ShareUiState.Starting(files, shareState.value.totalSize, 100, 100)
                val onionAddress = onion ?: error("onion was null")
                val url = "http://$onionAddress"
                LOG.error("OnionShare URL: $url") // TODO remove before release
                val sharing = ShareUiState.Sharing(files, shareState.value.totalSize, url)
                // TODO properly manage tor and webserver state together
                ensureActive()
                // collecting from StateFlow will only return when coroutine gets cancelled
                webserverManager.start(page).collect {
                    onWebserverStateChanged(it, sharing)
                }
            } catch (e: IOException) {
                LOG.warn("Error while startSharing ", e)
                // launching stop on global scope to prevent deadlock when it waits for current job
                GlobalScope.launch { stopSharing(exception = e) }
                cancel("Error while startSharing", e)
            }
        }
    }

    @Throws(IOException::class)
    private fun getSendPage(filesZipping: FilesZipping): SendPage {
        val fileSize = filesZipping.zip.length()
        return SendPage(
            fileName = "download.zip",
            fileSize = fileSize.toString(),
            fileSizeHuman = Formatter.formatShortFileSize(app.applicationContext, fileSize),
            zipFile = filesZipping.zip,
        ).apply {
            addFiles(filesZipping.files)
        }
    }

    private suspend fun onWebserverStateChanged(
        state: WebserverManager.State,
        sharing: ShareUiState.Sharing,
    ) = withContext(Dispatchers.IO) {
        when (state) {
            WebserverManager.State.STARTED -> _shareState.value = sharing
            WebserverManager.State.SHOULD_STOP -> stopSharing(complete = true)
            // Stopping again could cause a harmless double stop,
            // but ensures state update when webserver stops unexpectedly.
            // In practise, we cancel the coroutine of this collector when stopping the first time,
            // so calling stopSharing() twice should actually not happen.
            WebserverManager.State.STOPPED -> stopSharing()
        }
    }

    private suspend fun stopSharing(
        complete: Boolean = false,
        exception: Exception? = null,
    ) = withContext(Dispatchers.IO) {
        LOG.info("Stopping sharing...")
        if (startSharingJob?.isActive == true) {
            // TODO check if this always works as expected
            startSharingJob?.cancelAndJoin()
        }
        startSharingJob = null

        torManager.stop()
        webserverManager.stop()
        val files = shareState.value.files
        val newState = when {
            files.isEmpty() -> ShareUiState.NoFiles
            complete -> ShareUiState.Complete(files, shareState.value.totalSize)
            exception is FileErrorException -> {
                // remove errorFile from list of files, so user can try again
                val newFiles = files.toMutableList().apply { remove(exception.file) }
                ShareUiState.Error(newFiles, newFiles.totalSize, exception.file)
            }
            exception != null -> ShareUiState.Error(files, shareState.value.totalSize)
            else -> ShareUiState.FilesAdded(files, shareState.value.totalSize)
        }
        _shareState.value = newState
        // special case handling for error state without file left
        if (newState is ShareUiState.Error && newState.files.isEmpty()) {
            delay(1000)
            _shareState.value = ShareUiState.NoFiles
        }
    }

    private fun SendFile.releaseUriPermission() {
        val contentResolver = app.applicationContext.contentResolver
        contentResolver.releasePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
    }

}
