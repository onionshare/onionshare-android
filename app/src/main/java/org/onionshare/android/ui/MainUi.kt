package org.onionshare.android.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onionshare.android.R
import org.onionshare.android.files.FileManager
import org.onionshare.android.server.SendFile
import org.onionshare.android.server.WebserverManager
import org.onionshare.android.server.WebserverManager.State.STARTED
import org.onionshare.android.server.WebserverManager.State.STARTING
import org.onionshare.android.server.WebserverManager.State.STOPPED
import org.onionshare.android.server.WebserverManager.State.STOPPING
import org.onionshare.android.ui.theme.OnionshareTheme

@Composable
fun MainUi(
    fileManagerState: StateFlow<FileManager.State>,
    onButtonClicked: (WebserverManager.State) -> Unit,
    webserverState: StateFlow<WebserverManager.State>,
    onFabClicked: () -> Unit,
    onFileRemove: (SendFile) -> Unit,
) {
    val state = webserverState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
        floatingActionButton = {
            if (state.value == STOPPED) {
                val text = "Add files"
                FloatingActionButton(
                    onClick = onFabClicked,
                    backgroundColor = MaterialTheme.colors.primary,
                ) { Icon(Icons.Filled.Add, contentDescription = text) }
            }
        }
    ) {
        MainContent(fileManagerState, onButtonClicked, state, onFileRemove)
    }
}

@Composable
fun MainContent(
    fileManagerState: StateFlow<FileManager.State>,
    onButtonClicked: (WebserverManager.State) -> Unit,
    webserverState: State<WebserverManager.State>,
    onFileRemove: (SendFile) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val fState = fileManagerState.collectAsState()
        if (fState.value == FileManager.State.NoFiles) {
            Image(painterResource(R.drawable.ic_share_empty_state), contentDescription = null)
            Text(
                text = stringResource(R.string.share_empty_state),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val wState = webserverState.value
            Button(
                modifier = Modifier.padding(16.dp),
                enabled = wState == STOPPED || wState == STARTED,
                onClick = { onButtonClicked(wState) },
            ) {
                if (wState == STOPPED || wState == STARTING) Text("Start Webserver")
                else if (wState == STARTED || wState == STOPPING) Text("Stop Webserver")
            }
            FileList(fState, onFileRemove)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OnionshareTheme {
        MainUi(
            fileManagerState = MutableStateFlow(FileManager.State.NoFiles),
            onButtonClicked = {},
            webserverState = MutableStateFlow(STOPPED),
            onFabClicked = {},
            onFileRemove = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun NightModePreview() = DefaultPreview()
