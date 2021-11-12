package org.onionshare.android.ui

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.onionshare.android.files.FileManager
import org.onionshare.android.server.PORT
import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
import org.onionshare.android.server.WebserverManager
import org.slf4j.LoggerFactory
import javax.inject.Inject

private val LOG = LoggerFactory.getLogger(MainViewModel::class.java)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val webserverManager: WebserverManager,
    private val fileManager: FileManager,
) : AndroidViewModel(app) {

    private val _shareState = MutableStateFlow<ShareUiState>(ShareUiState.NoFiles)
    val shareState: StateFlow<ShareUiState> = _shareState

    @Volatile
    var startSharingJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        // TODO later, we shouldn't stop the server when closing the activity
        stopSharing()
    }

    fun onUrisReceived(uris: List<Uri>) {
        if (uris.isEmpty()) return // user backed out of select activity

        // not supporting selecting entire folders with sub-folders
        viewModelScope.launch(Dispatchers.IO) {
            val filesAdded = fileManager.addFiles(uris, shareState.value.files)
            val totalSize = filesAdded.files.sumOf { it.size }
            _shareState.value = ShareUiState.FilesAdded(filesAdded.files, totalSize)
        }
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

    fun onSheetButtonClicked() {
        when (shareState.value) {
            is ShareUiState.FilesAdded -> startSharing()
            is ShareUiState.Starting -> stopSharing()
            is ShareUiState.Sharing -> stopSharing()
            is ShareUiState.Complete -> startSharing()
            ShareUiState.NoFiles -> error("Sheet button should not be visible with no files")
        }
    }

    private fun startSharing() {
        startSharingJob = viewModelScope.launch(Dispatchers.IO) {
            val files = shareState.value.files
            _shareState.value = ShareUiState.Starting(files, shareState.value.totalSize)
            ensureActive()
            val filesReady = fileManager.zipFiles(files) { ensureActive() }
            val fileSize = filesReady.zip.length()
            val sendPage = SendPage(
                fileName = "download.zip",
                fileSize = fileSize.toString(),
                fileSizeHuman = Formatter.formatShortFileSize(app.applicationContext, fileSize),
                zipFile = filesReady.zip,
            ).apply {
                addFiles(filesReady.files)
            }
            ensureActive()
            val webserverState = webserverManager.start(sendPage)
            if (webserverState == WebserverManager.State.STOPPED) {
                stopSharing()
            }
            val url = "http://127.0.0.1:$PORT"
//        val url = "http://openpravyvc6spbd4flzn4g2iqu4sxzsizbtb5aqec25t76dnoo5w7yd.onion/"
            _shareState.value = ShareUiState.Sharing(files, shareState.value.totalSize, url)
        }
    }

    private fun stopSharing() {
        viewModelScope.launch(Dispatchers.IO) {
            if (startSharingJob?.isActive == true) {
                // TODO check if this always works as expected
                startSharingJob?.cancelAndJoin()
            }

            webserverManager.stop()
            val files = shareState.value.files
            val newState = if (files.isEmpty()) {
                ShareUiState.NoFiles
            } else {
                ShareUiState.FilesAdded(files, files.sumOf { it.size })
            }
            _shareState.value = newState
        }
    }

}
