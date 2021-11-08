package org.onionshare.android.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.onionshare.android.OnionShareApp
import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
import org.onionshare.android.server.WebserverManager
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    app: OnionShareApp,
    handle: SavedStateHandle,
    private val webserverManager: WebserverManager,
) : AndroidViewModel(app) {

    val webserverState = webserverManager.state

    fun startServer() {
        val sendPage = SendPage(
            fileName = "foo.zip",
            fileSize = "202590",
            fileSizeHuman = "197.8 KiB"
        ).apply {
            // the below will get constructed programmatically later, this is just an example
            addFiles(
                listOf(
                    SendFile("foo", "1 KiB"),
                    SendFile("bar", "42 MiB"),
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            webserverManager.start(sendPage)
        }
    }

    fun stopServer() {
        viewModelScope.launch(Dispatchers.IO) {
            webserverManager.stop()
        }
    }

}
