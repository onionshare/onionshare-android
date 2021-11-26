package org.onionshare.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.onionshare.android.ShareManager
import org.onionshare.android.server.SendFile
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val shareManager: ShareManager,
) : AndroidViewModel(app) {

    val shareState: StateFlow<ShareUiState> = shareManager.shareState

    fun onUrisReceived(uris: List<Uri>) {
        if (uris.isEmpty()) return // user backed out of select activity

        viewModelScope.launch {
            shareManager.addFiles(uris)
        }
    }

    fun removeFile(file: SendFile) = shareManager.removeFile(file)
    fun removeAll() = shareManager.removeAll()
    fun onSheetButtonClicked() = viewModelScope.launch {
        shareManager.onStateChangeRequested()
    }

}
