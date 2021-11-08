package org.onionshare.android.ui

import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onionshare.android.server.WebserverManager.State
import org.onionshare.android.server.WebserverManager.State.STARTED
import org.onionshare.android.server.WebserverManager.State.STARTING
import org.onionshare.android.server.WebserverManager.State.STOPPED
import org.onionshare.android.server.WebserverManager.State.STOPPING
import org.onionshare.android.ui.theme.OnionshareTheme
import org.onionshare.android.ui.theme.PurpleOnionShare
import org.slf4j.LoggerFactory.getLogger

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
                        onFabClicked = this::onFabClicked,
                    )
                }
            }
        }
    }

    private fun onButtonClicked(currentState: State) = when (currentState) {
        STOPPED -> viewModel.startServer()
        STARTED -> viewModel.stopServer()
        else -> error("Illegal click state: $currentState")
    }

    private val launcher = registerForActivityResult(GetMultipleContents()) { uris ->
        // not supporting selecting entire folders with sub-folders
        uris.forEach { uri ->
            getLogger("TEST").error("$uri")
        }
        Toast.makeText(this, "Not implemented", LENGTH_SHORT).show()
    }

    private fun onFabClicked() {
        launcher.launch("*/*")
    }
}

@Composable
fun Greeting(
    name: String,
    webserverState: StateFlow<State>,
    onButtonClicked: (State) -> Unit,
    onFabClicked: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            val text = "Add files"
            ExtendedFloatingActionButton(
                onClick = onFabClicked,
                icon = { Icon(Icons.Filled.Add, contentDescription = text) },
                text = { Text(text) },
                backgroundColor = PurpleOnionShare,
            )
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Text(
                text = "Hello $name!",
                modifier = Modifier.padding(all = Dp(16.0f))
            )
            val state = webserverState.collectAsState().value
            Log.e("TEST", "state: $state")
            val isEnabled = state == STOPPED || state == STARTED
            Button(onClick = { onButtonClicked(state) }, enabled = isEnabled) {
                if (state == STOPPED || state == STARTING) Text("Start Webserver")
                else if (state == STARTED || state == STOPPING) Text("Stop Webserver")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OnionshareTheme {
        Greeting("Android with a long name for a preview", MutableStateFlow(STOPPED), {}, {})
    }
}
