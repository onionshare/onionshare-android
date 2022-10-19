package org.onionshare.android.ui.settings

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.onionshare.android.R
import org.onionshare.android.ui.AboutActionBar
import org.onionshare.android.ui.MainViewModel
import org.onionshare.android.ui.ROUTE_SETTINGS_MY_BRIDGES
import org.onionshare.android.ui.ShareButton
import org.onionshare.android.ui.theme.OnionBlue
import org.onionshare.android.ui.theme.OnionshareTheme
import kotlin.random.Random

private const val BRIDGE_DB = "https://bridges.torproject.org"

@Composable
fun SettingsTorUi(
    navController: NavHostController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settingsManager = viewModel.settingsManager
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            AboutActionBar(navController, R.string.settings_tor_title)
        }
    ) { innerPadding ->
        SettingsTorUiContent(
            country = settingsManager.currentCountry,
            automaticBridges = settingsManager.automaticBridges.value,
            numberOfCustomBridges = settingsManager.customBridges.value.size,
            onMyBridgesClicked = { navController.navigate(ROUTE_SETTINGS_MY_BRIDGES) },
            onAutomaticBridgesChanged = { settingsManager.setAutomaticBridges(it) },
            onBridgesAdded = {
                val numBridgesAdded = settingsManager.addCustomBridges(it)
                coroutineScope.launch {
                    scaffoldState.snackbarHostState.showSnackbar(
                        message = getBridgeNumberString(context, numBridgesAdded),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun SettingsTorUiContent(
    country: String,
    automaticBridges: Boolean,
    numberOfCustomBridges: Int,
    onMyBridgesClicked: () -> Unit,
    onAutomaticBridgesChanged: (Boolean) -> Unit,
    onBridgesAdded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollableState = rememberScrollState()
    Column(modifier = modifier.verticalScroll(scrollableState)
    ) {
        Text(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            text = stringResource(R.string.settings_tor_intro),
            style = MaterialTheme.typography.body2,
        )
        RadioPreference(
            title = stringResource(R.string.settings_tor_automatic),
            summary = country,
            selected = automaticBridges,
        ) {
            onAutomaticBridgesChanged(true)
        }
        RadioPreference(
            title = stringResource(R.string.settings_tor_bridges_title),
            summary = getBridgeNumberString(LocalContext.current, numberOfCustomBridges),
            selected = !automaticBridges,
        ) {
            onAutomaticBridgesChanged(false)
        }
        if (!automaticBridges) CustomBridgesUi(
            numberOfCustomBridges = numberOfCustomBridges,
            onMyBridgesClicked = onMyBridgesClicked,
            onBridgesAdded = onBridgesAdded,
        )
    }
}

@Composable
fun CustomBridgesUi(
    numberOfCustomBridges: Int,
    onMyBridgesClicked: () -> Unit,
    onBridgesAdded: (String) -> Unit,
) {
    val context = LocalContext.current
    Preference(
        title = stringResource(R.string.settings_tor_my_bridges_title),
        summary = getBridgeNumberString(context, numberOfCustomBridges),
    ) { onMyBridgesClicked() }
    Text(
        modifier = Modifier
            .padding(horizontal = 16.dp),
        text = stringResource(R.string.settings_tor_bridges_intro),
        style = MaterialTheme.typography.body2,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .clickable {
                    val linkIntent = Intent().apply {
                        action = ACTION_VIEW
                        data = Uri.parse(BRIDGE_DB)
                    }
                    context.startActivity(Intent.createChooser(linkIntent, null))
                }
                .weight(1f)
                .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
            text = BRIDGE_DB,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.OnionBlue,
        )
        ShareButton(BRIDGE_DB)
    }
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    OutlinedTextField(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 128.dp),
        value = textState,
        onValueChange = { textState = it },
        singleLine = false,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val clipboardManager = LocalClipboardManager.current
        IconButton(
            modifier = Modifier.padding(horizontal = 8.dp),
            onClick = {
                clipboardManager.getText()?.let {
                    textState = TextFieldValue(it, selection = TextRange(it.length))
                }
            },
        ) {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = stringResource(R.string.paste),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.65f),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = { textState = TextFieldValue("") },
            enabled = textState.text.isNotBlank(),
        ) {
            Text(stringResource(R.string.cancel))
        }
        TextButton(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = {
                onBridgesAdded(textState.text)
                textState = TextFieldValue("")
            },
            enabled = textState.text.isNotBlank(),
        ) {
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
fun RadioPreference(
    title: String,
    summary: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                enabled = !selected,
                selected = selected,
                onClick = onSelected,
            )
            .padding(vertical = 8.dp)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected,
        )
        Column(Modifier.padding(start = 8.dp)) {
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

fun getBridgeNumberString(context: Context, num: Int): String {
    return if (num == 0) {
        context.getString(R.string.settings_tor_bridges_none)
    } else {
        context.resources.getQuantityString(R.plurals.settings_tor_bridges_number, num, num)
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsTorPreview() = OnionshareTheme {
    Surface(color = MaterialTheme.colors.background) {
        SettingsTorUiContent(
            country = "United States",
            automaticBridges = false,
            numberOfCustomBridges = Random.nextInt(0, 2),
            onMyBridgesClicked = {},
            onAutomaticBridgesChanged = {},
            onBridgesAdded = {},
        )
    }
}
