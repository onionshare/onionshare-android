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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.onionshare.android.R
import org.onionshare.android.files.FilesState
import org.onionshare.android.server.SendFile
import org.onionshare.android.ui.ROUTE_ABOUT
import org.onionshare.android.ui.ROUTE_SETTINGS
import org.onionshare.android.ui.theme.Fab
import org.onionshare.android.ui.theme.OnionshareTheme
import org.onionshare.android.ui.theme.topBar

private val bottomSheetPeekHeight = 60.dp

private fun isEmptyState(shareState: ShareUiState, filesState: FilesState): Boolean {
    return shareState.allowsModifyingFiles && filesState.files.isEmpty()
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun ShareUi(
    navController: NavHostController,
    shareState: ShareUiState,
    filesState: FilesState,
    onFabClicked: () -> Unit,
    onFileRemove: (SendFile) -> Unit,
    onRemoveAll: () -> Unit,
    onSheetButtonClicked: () -> Unit,
) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val offset = getOffsetInDp(scaffoldState.bottomSheetState)
    val snackbarHostState: SnackbarHostState
    if (isEmptyState(shareState, filesState)) {
        val normalScaffoldState = rememberScaffoldState()
        snackbarHostState = normalScaffoldState.snackbarHostState
        Scaffold(
            topBar = { ActionBar(navController, R.string.app_name, shareState.allowsModifyingFiles) },
            scaffoldState = normalScaffoldState,
            floatingActionButton = {
                Fab(scaffoldState.bottomSheetState, onFabClicked)
            },
        ) { innerPadding ->
            MainContent(shareState, filesState, offset, onFileRemove, onRemoveAll, Modifier.padding(innerPadding))
        }
        LaunchedEffect("hideSheet") {
            // This ensures the FAB color can animate back when we transition to NoFiles state
            scaffoldState.bottomSheetState.collapse()
        }
    } else {
        snackbarHostState = scaffoldState.snackbarHostState
        LaunchedEffect("showSheet") {
            delay(750)
            scaffoldState.bottomSheetState.expand()
        }
        if (!shareState.collapsableSheet && scaffoldState.bottomSheetState.isCollapsed) {
            // ensure the bottom sheet is visible
            LaunchedEffect(shareState) {
                scaffoldState.bottomSheetState.expand()
            }
        }
        BottomSheetScaffold(
            topBar = { ActionBar(navController, R.string.app_name, shareState.allowsModifyingFiles) },
            floatingActionButton = if (shareState.allowsModifyingFiles) {
                { Fab(scaffoldState.bottomSheetState, onFabClicked) }
            } else null,
            sheetGesturesEnabled = shareState.collapsableSheet,
            sheetPeekHeight = bottomSheetPeekHeight,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            scaffoldState = scaffoldState,
            sheetElevation = 16.dp,
            sheetContent = { BottomSheet(shareState, onSheetButtonClicked) }
        ) { innerPadding ->
            MainContent(shareState, filesState, offset, onFileRemove, onRemoveAll, Modifier.padding(innerPadding))
        }
    }
    if (shareState is ShareUiState.ErrorAddingFile) {
        val errorFile = shareState.errorFile
        val text = if (errorFile != null) {
            stringResource(R.string.share_error_file_snackbar_text, errorFile.basename)
        } else {
            stringResource(R.string.share_error_snackbar_text)
        }
        val action = if (filesState.files.isEmpty()) null else stringResource(R.string.share_error_snackbar_action)
        LaunchedEffect("showSnackbar") {
            val snackbarResult = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = action,
                duration = SnackbarDuration.Long,
            )
            if (snackbarResult == SnackbarResult.ActionPerformed) onSheetButtonClicked()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun getOffsetInDp(bottomSheetState: BottomSheetState): Dp {
    if (!bottomSheetState.isExpanded) return 0.dp
    val offset = try {
        bottomSheetState.requireOffset()
    } catch (e: IllegalStateException) {
        0f
    }
    if (offset == 0f) return 0.dp
    val configuration = LocalConfiguration.current
    val screenHeight = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    return with(LocalDensity.current) {
        (screenHeight - offset).toDp()
    }
}

@Composable
fun ActionBar(
    navController: NavHostController,
    @StringRes res: Int,
    showOverflowMenu: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        backgroundColor = MaterialTheme.colors.topBar,
        title = { Text(stringResource(res)) },
        actions = {
            if (showOverflowMenu) {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.menu)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {

                    DropdownMenuItem(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                        Text(stringResource(R.string.settings_title))

                    }
                    DropdownMenuItem(onClick = { navController.navigate(ROUTE_ABOUT) }) {
                        Text(stringResource(R.string.about_title))
                    }
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun Fab(scaffoldState: BottomSheetState, onFabClicked: () -> Unit) {
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

@Composable
fun MainContent(
    shareState: ShareUiState,
    filesState: FilesState,
    offset: Dp,
    onFileRemove: (SendFile) -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier,
) {
    if (isEmptyState(shareState, filesState)) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Box(
                contentAlignment = TopCenter,
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
        FileList(Modifier.padding(bottom = offset), shareState, filesState, onFileRemove, onRemoveAll)
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
            shareState = ShareUiState.AddingFiles,
            filesState = FilesState(files),
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
            shareState = ShareUiState.AddingFiles,
            filesState = FilesState(emptyList()),
            onFabClicked = {},
            onFileRemove = {},
            onRemoveAll = {},
        ) {}
    }
}
