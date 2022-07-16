package org.onionshare.android.ui.share

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.onionshare.android.R
import org.onionshare.android.server.SendFile
import org.onionshare.android.ui.ROUTE_ABOUT
import org.onionshare.android.ui.theme.Fab
import org.onionshare.android.ui.theme.OnionshareTheme
import org.onionshare.android.ui.theme.topBar

private val bottomSheetPeekHeight = 60.dp

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun ShareUi(
    navController: NavHostController,
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
            topBar = { ActionBar(navController, R.string.app_name) },
            floatingActionButton = {
                Fab(state.value, scaffoldState.bottomSheetState, onFabClicked)
            },
        ) {
            MainContent(stateFlow, offset, onFileRemove, onRemoveAll)
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
        val uiState = state.value
        if (uiState is ShareUiState.ErrorAddingFile) {
            val errorFile = uiState.errorFile
            val text = if (errorFile != null) {
                stringResource(R.string.share_error_file_snackbar_text, errorFile.basename)
            } else {
                stringResource(R.string.share_error_snackbar_text)
            }
            val action = if (uiState.files.isEmpty()) null else stringResource(R.string.share_error_snackbar_action)
            LaunchedEffect("showSnackbar") {
                val snackbarResult = scaffoldState.snackbarHostState.showSnackbar(
                    message = text,
                    actionLabel = action,
                    duration = SnackbarDuration.Long,
                )
                if (snackbarResult == SnackbarResult.ActionPerformed) onSheetButtonClicked()
            }
        }
        if (!uiState.collapsableSheet && scaffoldState.bottomSheetState.isCollapsed) {
            // ensure the bottom sheet is visible
            LaunchedEffect(uiState) {
                scaffoldState.bottomSheetState.expand()
            }
        }
        BottomSheetScaffold(
            topBar = { ActionBar(navController, R.string.app_name) },
            floatingActionButton = {
                Fab(uiState, scaffoldState.bottomSheetState, onFabClicked)
            },
            sheetGesturesEnabled = uiState.collapsableSheet,
            sheetPeekHeight = bottomSheetPeekHeight,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            scaffoldState = scaffoldState,
            sheetElevation = 16.dp,
            sheetContent = { BottomSheet(uiState, onSheetButtonClicked) }
        ) {
            MainContent(stateFlow, offset, onFileRemove, onRemoveAll)
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
fun ActionBar(
    navController: NavHostController,
    @StringRes res: Int,
) = TopAppBar(
    backgroundColor = MaterialTheme.colors.topBar,
    title = { Text(stringResource(res)) },
    actions = {
        IconButton(onClick = { navController.navigate(ROUTE_ABOUT) }) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.about_title),
            )
        }
    },
)

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun Fab(state: ShareUiState, scaffoldState: BottomSheetState, onFabClicked: () -> Unit) {
    if (state.allowsModifyingFiles) {
        val color = if (scaffoldState.isCollapsed) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.Fab
        }
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
    if (state.value is ShareUiState.NoFiles) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Box(contentAlignment = TopCenter,
                modifier = Modifier
                    .padding(16.dp)
                    .background(MaterialTheme.colors.error)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = Bold)) {
                            append(stringResource(R.string.warning_alpha_intro))
                        }
                        append(" ")
                        append(stringResource(R.string.warning_alpha))
                    },
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Column(
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Image(painterResource(R.drawable.ic_share_empty_state), contentDescription = null)
                Text(
                    text = stringResource(R.string.share_empty_state),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    } else {
        val modifier = Modifier.padding(bottom = offset)
        FileList(modifier, state, onFileRemove, onRemoveAll)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val files = listOf(
        SendFile("foo", "23 KiB", 1337L, Uri.parse(""), null)
    )
    OnionshareTheme {
        ShareUi(
            navController = rememberNavController(),
            stateFlow = MutableStateFlow(ShareUiState.FilesAdded(files)),
            onFabClicked = {},
            onFileRemove = {},
            onRemoveAll = {},
        ) {}
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun NightModePreview() {
    OnionshareTheme {
        ShareUi(
            navController = rememberNavController(),
            stateFlow = MutableStateFlow(ShareUiState.NoFiles),
            onFabClicked = {},
            onFileRemove = {},
            onRemoveAll = {},
        ) {}
    }
}
