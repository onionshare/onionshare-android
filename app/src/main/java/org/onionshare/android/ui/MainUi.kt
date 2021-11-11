package org.onionshare.android.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.onionshare.android.R
import org.onionshare.android.server.SendFile
import org.onionshare.android.ui.theme.OnionshareTheme

private val bottomSheetPeekHeight = 60.dp

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun MainUi(
    stateFlow: StateFlow<ShareUiState>,
    onFabClicked: () -> Unit,
    onFileRemove: (SendFile) -> Unit,
    onRemoveAll: () -> Unit,
    onSheetButtonClicked: () -> Unit,
) {
    val state = stateFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val offset = getOffsetInDp(scaffoldState.bottomSheetState.offset)
    if (state.value is ShareUiState.NoFiles) {
        Scaffold(
            topBar = { ActionBar(R.string.app_name) },
            floatingActionButton = { Fab(state.value, offset, onFabClicked) },
        ) {
            MainContent(stateFlow, offset, onFileRemove, onRemoveAll)
        }
    } else {
        LaunchedEffect("showSheet") {
            delay(1000)
            scaffoldState.bottomSheetState.expand()
        }
        BottomSheetScaffold(
            topBar = { ActionBar(R.string.app_name) },
            floatingActionButton = { Fab(state.value, offset, onFabClicked) },
            sheetPeekHeight = bottomSheetPeekHeight,
            scaffoldState = scaffoldState,
            sheetContent = { BottomSheet(state.value, onSheetButtonClicked) }
        ) {
            MainContent(stateFlow, offset, onFileRemove) {
                coroutineScope.launch {
                    scaffoldState.bottomSheetState.collapse()
                }
                onRemoveAll()
            }
        }
    }
}

@Composable
private fun getOffsetInDp(offset: State<Float>): Dp {
    val value by remember { offset }
    if (value == 0f) return 0.dp
    val configuration = LocalConfiguration.current
    val screenHeight = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    return with(LocalDensity.current) {
        (screenHeight - value).toDp()
    }
}

@Composable
fun ActionBar(@StringRes res: Int) {
    TopAppBar(
        backgroundColor = MaterialTheme.colors.primary,
        title = { Text(stringResource(res)) }
    )
}

@Composable
fun Fab(state: ShareUiState, offset: Dp, onFabClicked: () -> Unit) {
    if (state is ShareUiState.NoFiles || state is ShareUiState.FilesAdded) {
        val color by animateColorAsState(
            targetValue = if (offset <= bottomSheetPeekHeight) {
                MaterialTheme.colors.primary
            } else {
                MaterialTheme.colors.surface
            },
            animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        )
        FloatingActionButton(
            onClick = onFabClicked,
            backgroundColor = color,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.share_files_add),
            )
        }
    }
}

@Composable
fun MainContent(
    stateFlow: StateFlow<ShareUiState>,
    offset: Dp,
    onFileRemove: (SendFile) -> Unit,
    onRemoveAll: () -> Unit,
) {
    val state = stateFlow.collectAsState()
    Column(
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = if (state.value is ShareUiState.NoFiles) {
            Arrangement.Center
        } else {
            Arrangement.Top
        },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        if (state.value is ShareUiState.NoFiles) {
            Image(painterResource(R.drawable.ic_share_empty_state), contentDescription = null)
            Text(
                text = stringResource(R.string.share_empty_state),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val modifier = Modifier.padding(bottom = offset)
            FileList(modifier, state, onFileRemove, onRemoveAll)
        }
    }
}

@Composable
fun BottomSheet(state: ShareUiState, onSheetButtonClicked: () -> Unit) {
    val indicatorColor: Color
    val stateText: Int
    val buttonText: Int
    when (state) {
        is ShareUiState.FilesAdded -> {
            indicatorColor = Color.Gray
            stateText = R.string.ready
            buttonText = R.string.share_button_start
        }
        is ShareUiState.Starting -> {
            indicatorColor = Color.Yellow
            stateText = R.string.starting
            buttonText = R.string.share_button_starting
        }
        is ShareUiState.Sharing -> {
            indicatorColor = Color.Green
            stateText = R.string.sharing
            buttonText = R.string.share_button_stop
        }
        else -> {
            indicatorColor = Color.Gray
            stateText = R.string.ready
            buttonText = R.string.share_button_start
        }
    }
    Column {
        Image(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.Gray),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(24.dp, 12.dp)
                .padding(top = 4.dp)
                .align(CenterHorizontally),
        )
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Circle,
                tint = indicatorColor,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = stringResource(stateText),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Divider()
        if (state is ShareUiState.Sharing) {
            Text(state.onionAddress,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(16.dp))
            Divider()
        }
        Button(
            onClick = onSheetButtonClicked,
            colors = if (state is ShareUiState.Sharing) {
                buttonColors(contentColor = Color.Red,
                    backgroundColor = MaterialTheme.colors.surface)
            } else buttonColors(),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Text(
                text = stringResource(buttonText),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val files = listOf(
        SendFile("foo", "23 KiB", 1337L, Uri.parse(""), null)
    )
    OnionshareTheme {
        MainUi(
            stateFlow = MutableStateFlow(ShareUiState.FilesAdded(files, 1337L)),
            onFabClicked = {},
            onFileRemove = {},
            onRemoveAll = {},
            onSheetButtonClicked = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun NightModePreview() {
    OnionshareTheme {
        MainUi(
            stateFlow = MutableStateFlow(ShareUiState.NoFiles),
            onFabClicked = {},
            onFileRemove = {},
            onRemoveAll = {},
            onSheetButtonClicked = {},
        )
    }
}
