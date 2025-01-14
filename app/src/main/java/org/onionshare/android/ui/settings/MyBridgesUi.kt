package org.onionshare.android.ui.settings

import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import org.onionshare.android.R
import org.onionshare.android.ui.AboutActionBar
import org.onionshare.android.ui.MainViewModel
import org.onionshare.android.ui.ShareButton
import org.onionshare.android.ui.theme.OnionshareTheme

@Composable
fun MyBridgesUi(
    navController: NavHostController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val settingsManager = viewModel.settingsManager
    Scaffold(topBar = {
        AboutActionBar(navController, R.string.settings_tor_my_bridges_title)
    }) { innerPadding ->
        MyBridgesUiContent(
            bridges = settingsManager.customBridges.value,
            onBridgeRemoved = { settingsManager.removeCustomBridge(it) },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun MyBridgesUiContent(
    bridges: List<String>,
    onBridgeRemoved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bridges.isEmpty()) Box(modifier = Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier.align(Center),
            text = stringResource(R.string.settings_tor_bridges_none),
            style = MaterialTheme.typography.headlineSmall,
        )
    } else {
        val scrollState = rememberLazyListState()
        LazyColumn(
            modifier = modifier,
            state = scrollState,
        ) {
            items(bridges) {
                BridgeLineUi(it) {
                    onBridgeRemoved(it)
                }
            }
        }
    }
}

@Composable
fun BridgeLineUi(bridge: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = bridge,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = SpaceBetween,
            ) {
                ShareButton(bridge)
                IconButton(
                    onClick = onDelete,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MyBridgesPreview() = OnionshareTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        MyBridgesUiContent(
            bridges = listOf(
                "obfs4 178.238.237.63:1443 2818B19A83611C927F3D1A054432A730763D571D " +
                    "cert=BKOIE8C7Bz4dSxIH0W91GfdXq1V3uDnPBb/9rwjWfrPGXxlh16nJ+zpENvb84JlP6pG2IA iat-mode=0",
                "obfs4 92.37.149.198:443 B66921924E47BB9D4FD582DE0671F6AE9975598E " +
                    "cert=UwbBnGYeVRoxhCA5+wxd2tRgS7BimvT3ZjKDec4HE5gS0Wc5w4QIbEO4gx6do/FAcJZ0Jg iat-mode=0",
                "obfs4 201.61.207.126:2439 6F059771EFD2EE8649C2CCEF41407AEBD82B1E4C " +
                    "cert=caVSQBW9+duFx52XC51Sgoc6GvPlCfdSdYWvMYdlR5TQDYzCOEtGy+qE/s7QHsyrX2tFCQ iat-mode=0",
            ),
            onBridgeRemoved = {},
        )
    }
}
