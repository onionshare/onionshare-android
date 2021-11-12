package org.onionshare.android.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Arrangement.Top
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val scaffoldState = rememberBottomSheetScaffoldState()
    val offset = getOffsetInDp(scaffoldState.bottomSheetState.offset)
    if (state.value == ShareUiState.NoFiles) {
        Scaffold(
            topBar = { ActionBar(R.string.app_name) },
            floatingActionButton = { Fab(state.value, offset, onFabClicked) },
        ) {
            MainContent(stateFlow, onFileRemove, onRemoveAll)
        }
        LaunchedEffect("hideSheet") {
            // This ensures the FAB color can animate back when we transition to NoFiles state
            scaffoldState.bottomSheetState.collapse()
        }
    } else {
        LaunchedEffect("showSheet") {
            delay(750)
            scaffoldState.bottomSheetState.expand()
        }
        BottomSheetScaffold(
            topBar = { ActionBar(R.string.app_name) },
            floatingActionButton = { Fab(state.value, offset, onFabClicked) },
            sheetPeekHeight = bottomSheetPeekHeight,
            sheetShape = RoundedCornerShape(16.dp),
            scaffoldState = scaffoldState,
            sheetContent = { BottomSheet(state.value, onSheetButtonClicked) }
        ) {
            MainContent(stateFlow, onFileRemove, onRemoveAll)
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
        title = { Text(stringResource(res)) },
    )
}

@Composable
fun Fab(state: ShareUiState, offset: Dp, onFabClicked: () -> Unit) {
    if (state.allowsModifyingFiles) {
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
    onFileRemove: (SendFile) -> Unit,
    onRemoveAll: () -> Unit,
) {
    val state = stateFlow.collectAsState()
    Column(
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = if (state.value is ShareUiState.NoFiles) Center else Top,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        if (state.value is ShareUiState.NoFiles) {
            Image(painterResource(R.drawable.ic_share_empty_state), contentDescription = null)
            Text(
                text = stringResource(R.string.share_empty_state),
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val modifier = Modifier.padding(bottom = bottomSheetPeekHeight)
            FileList(modifier, state, onFileRemove, onRemoveAll)
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

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
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
