package org.onionshare.android.ui

import android.content.ActivityNotFoundException
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onionshare.android.R
import org.onionshare.android.files.FileManager
import org.onionshare.android.server.WebserverManager
import org.onionshare.android.server.WebserverManager.State.STARTED
import org.onionshare.android.server.WebserverManager.State.STARTING
import org.onionshare.android.server.WebserverManager.State.STOPPED
import org.onionshare.android.server.WebserverManager.State.STOPPING
import org.onionshare.android.ui.theme.OnionshareTheme
import org.onionshare.android.ui.theme.PurpleOnionShare

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnionshareTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Greeting(
                        name = "OnionSharer",
                        webserverState = viewModel.webserverState,
                        onButtonClicked = this::onButtonClicked,
                        fileManagerState = viewModel.fileManagerState,
                        onFabClicked = this::onFabClicked,
                    )
                }
            }
        }
    }

    private fun onButtonClicked(currentState: WebserverManager.State) = when (currentState) {
        STOPPED -> viewModel.startServer()
        STARTED -> viewModel.stopServer()
        else -> error("Illegal click state: $currentState")
    }

    private val launcher = registerForActivityResult(GetMultipleContents()) { uris ->
        viewModel.onUrisReceived(uris)
    }

    private fun onFabClicked() {
        try {
            launcher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.add_files_not_supported, LENGTH_SHORT).show()
        }
    }
}

@Composable
fun Greeting(
    name: String,
    fileManagerState: StateFlow<FileManager.State>,
    onButtonClicked: (WebserverManager.State) -> Unit,
    webserverState: StateFlow<WebserverManager.State>,
    onFabClicked: () -> Unit,
) {
    val state = webserverState.collectAsState()
    Scaffold(
        floatingActionButton = {
            if (state.value == STOPPED) {
                val text = "Add files"
                ExtendedFloatingActionButton(
                    onClick = onFabClicked,
                    icon = { Icon(Icons.Filled.Add, contentDescription = text) },
                    text = { Text(text) },
                    backgroundColor = PurpleOnionShare,
                )
            }
        }
    ) {
        MainContent(name, fileManagerState, onButtonClicked, state)
    }
}

@Composable
fun MainContent(
    name: String,
    fileManagerState: StateFlow<FileManager.State>,
    onButtonClicked: (WebserverManager.State) -> Unit,
    webserverState: State<WebserverManager.State>,
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
            Text(
                text = "Hello $name!",
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
            FileList(fState)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OnionshareTheme {
        Greeting("Android with a long name for a preview",
            MutableStateFlow(FileManager.State.NoFiles),
            {},
            MutableStateFlow(STOPPED),
            {}
        )
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun NightModePreview() = DefaultPreview()
