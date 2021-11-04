package org.onionshare.android.ui

import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.onionshare.android.files.FileManager
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

    private val _fileManagerState = MutableStateFlow<FileManager.State>(FileManager.State.NoFiles)
    val fileManagerState: StateFlow<FileManager.State> = _fileManagerState
    val webserverState = webserverManager.state

    override fun onCleared() {
        super.onCleared()
        // TODO later, we shouldn't stop the server when closing the activity
        stopServer()
    }

    fun onUrisReceived(uris: List<Uri>) {
        if (uris.isEmpty()) return // user backed out of select activity

        // not supporting selecting entire folders with sub-folders
        viewModelScope.launch(Dispatchers.IO) {
            val filesAdded = fileManager.addFiles(uris)
            _fileManagerState.value = filesAdded
        }
    }

    fun startServer() {
        // FIXME this is a mixing of concerns to get the right state in the UI
        webserverManager.onFilesBeingZipped()

        viewModelScope.launch(Dispatchers.IO) {
            val filesAdded = fileManagerState.value as FileManager.State.FilesAdded
            val filesReady = fileManager.zipFiles(filesAdded)
            _fileManagerState.value = filesReady

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
