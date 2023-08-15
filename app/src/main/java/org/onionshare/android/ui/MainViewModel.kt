package org.onionshare.android.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.briarproject.android.dontkillmelib.DozeUtils.needsDozeWhitelisting
import org.onionshare.android.R
import org.onionshare.android.ShareManager
import org.onionshare.android.files.FileManager
import org.onionshare.android.files.FilesState
import org.onionshare.android.server.SendFile
import org.onionshare.android.ui.settings.SettingsManager
import org.onionshare.android.ui.share.ShareUiState
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    val settingsManager: SettingsManager,
    private val shareManager: ShareManager,
    private val fileManager: FileManager,
) : AndroidViewModel(app) {

    val shareState: StateFlow<ShareUiState> = shareManager.shareState
    val filesState: StateFlow<FilesState> = shareManager.filesState

    val needsDozeWhitelisting get() = needsDozeWhitelisting(getApplication())

    fun onUrisReceived(uris: List<Uri>, takePermission: Boolean) {
        require(uris.isNotEmpty()) { "Call this only for non-empty list of Uris" }

        if (shareState.value.allowsModifyingFiles) viewModelScope.launch {
            fileManager.addFiles(uris, takePermission)
        } else {
            Toast.makeText(getApplication(), R.string.share_error_not_allowed, LENGTH_LONG).show()
        }
    }

    fun removeFile(file: SendFile) = fileManager.removeFile(file)
    fun removeAll() = fileManager.removeAll()
    fun onSheetButtonClicked() = viewModelScope.launch {
        shareManager.onStateChangeRequested()
    }

}
