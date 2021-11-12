package org.onionshare.android.ui

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.onionshare.android.files.FileManager
import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
import org.onionshare.android.server.WebserverManager
import org.slf4j.LoggerFactory
import javax.inject.Inject

private val LOG = LoggerFactory.getLogger(MainViewModel::class.java)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    handle: SavedStateHandle,
    private val webserverManager: WebserverManager,
    private val fileManager: FileManager,
) : AndroidViewModel(app) {

    private val _shareState = MutableStateFlow<ShareUiState>(ShareUiState.NoFiles)
    val shareState: StateFlow<ShareUiState> = _shareState

    override fun onCleared() {
        super.onCleared()
        // TODO later, we shouldn't stop the server when closing the activity
        stopServer()
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
        viewModelScope.launch {
            _shareState.value =
                ShareUiState.Starting(_shareState.value.files, _shareState.value.totalSize)
            delay(1700)
            _shareState.value = ShareUiState.Sharing(_shareState.value.files,
                _shareState.value.totalSize,
                "http://openpravyvc6spbd4flzn4g2iqu4sxzsizbtb5aqec25t76dnoo5w7yd.onion/")
            delay(5000)
            _shareState.value =
                ShareUiState.Complete(_shareState.value.files, _shareState.value.totalSize)
        }
    }

    fun startServer() {
        // FIXME this is a mixing of concerns to get the right state in the UI
        webserverManager.onFilesBeingZipped()

        viewModelScope.launch(Dispatchers.IO) {
            val filesReady = fileManager.zipFiles(shareState.value.files)
            LOG.error("$filesReady")
            val fileSize = filesReady.zip.length()
            val sendPage = SendPage(
                fileName = "download.zip",
                fileSize = fileSize.toString(),
                fileSizeHuman = Formatter.formatShortFileSize(app.applicationContext, fileSize),
                zipFile = filesReady.zip,
            ).apply {
                addFiles(filesReady.files)
            }
            webserverManager.start(sendPage)
        }
    }

    fun stopServer() {
        viewModelScope.launch(Dispatchers.IO) {
            webserverManager.stop()
        }
    }

}
