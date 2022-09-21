package org.onionshare.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.onionshare.android.R
import org.onionshare.android.ui.AboutActionBar
import org.onionshare.android.ui.MainViewModel
import org.onionshare.android.ui.ROUTE_SETTINGS_TOR
import org.onionshare.android.ui.theme.OnionshareTheme

@Composable
fun SettingsUi(
    navController: NavHostController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val automatic = viewModel.settingsManager.automaticBridges.value
    Scaffold(topBar = {
        AboutActionBar(navController, R.string.settings_title)
    }) { innerPadding ->
        val scrollableState = rememberScrollState()
        Column(modifier = Modifier
            .padding(innerPadding)
            .verticalScroll(scrollableState)
        ) {
            Preference(
                title = stringResource(R.string.settings_tor_title),
                summary = if (automatic) {
                    stringResource(R.string.settings_tor_automatic)
                } else {
                    stringResource(R.string.settings_tor_bridges_title)
                },
            ) {
                navController.navigate(ROUTE_SETTINGS_TOR)
            }
        }
    }
}

@Composable
fun Preference(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.alpha(0.65f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() = OnionshareTheme {
    Surface(color = MaterialTheme.colors.background) {
        SettingsUi(rememberNavController())
    }
}
