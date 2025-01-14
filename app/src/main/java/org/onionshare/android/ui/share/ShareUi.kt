package org.onionshare.android.ui.share

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
@OptIn(ExperimentalMaterial3Api::class)
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
    val snackbarHostState = remember { SnackbarHostState() }
    if (isEmptyState(shareState, filesState)) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = { ActionBar(navController, R.string.app_name, shareState.allowsModifyingFiles) },
            floatingActionButton = {
                Fab(false, onFabClicked)
            },
        ) { innerPadding ->
            MainContent(shareState, filesState, offset, onFileRemove, onRemoveAll, Modifier.padding(innerPadding))
        }
    } else {
        LaunchedEffect("showSheet") {
            delay(750)
            scaffoldState.bottomSheetState.expand()
        }
        if (!shareState.collapsableSheet && !scaffoldState.bottomSheetState.isVisible) {
            // ensure the bottom sheet is visible
            LaunchedEffect(shareState) {
                scaffoldState.bottomSheetState.expand()
            }
        }
        BottomSheetScaffold(
            topBar = { ActionBar(navController, R.string.app_name, shareState.allowsModifyingFiles) },
            sheetSwipeEnabled = shareState.collapsableSheet,
            sheetPeekHeight = bottomSheetPeekHeight,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            scaffoldState = scaffoldState,
            sheetShadowElevation = 16.dp,
            sheetDragHandle = if (shareState.collapsableSheet) ({ BottomSheetDefaults.DragHandle() }) else null,
            sheetContent = { BottomSheet(shareState, onSheetButtonClicked) }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
            ) {
                MainContent(shareState, filesState, offset, onFileRemove, onRemoveAll, Modifier.padding(innerPadding))
                if (shareState.allowsModifyingFiles) {
                    Fab(
                        hasFilesAdded = true,
                        onFabClicked = onFabClicked,
                        modifier = Modifier
                            .align(BottomEnd)
                            .offset(y = -offset - bottomSheetPeekHeight)
                            .padding(16.dp),
                    )
                }
            }
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
@OptIn(ExperimentalMaterial3Api::class)
private fun getOffsetInDp(bottomSheetState: SheetState): Dp {
    if (!bottomSheetState.isVisible) return 0.dp
    val offset = try {
        bottomSheetState.requireOffset()
    } catch (e: IllegalStateException) {
        0f
    }
    if (offset == 0f) return 0.dp
    val configuration = LocalConfiguration.current
    val screenHeight = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    return with(LocalDensity.current) {
        val o = (screenHeight - offset).toDp()
        max(0.dp, o - bottomSheetPeekHeight)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBar(
    navController: NavHostController,
    @StringRes res: Int,
    showOverflowMenu: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.topBar,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
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

                    DropdownMenuItem(onClick = { navController.navigate(ROUTE_SETTINGS) }, text = {
                        Text(stringResource(R.string.settings_title))
                    })
                    DropdownMenuItem(onClick = { navController.navigate(ROUTE_ABOUT) }, text = {
                        Text(stringResource(R.string.about_title))
                    })
                }
            }
        },
    )
}

@Composable
fun Fab(hasFilesAdded: Boolean, onFabClicked: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (hasFilesAdded) {
        MaterialTheme.colorScheme.Fab
    } else {
        MaterialTheme.colorScheme.primary
    }
    FloatingActionButton(
        onClick = onFabClicked,
        containerColor = color,
        modifier = modifier,
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
        Column(
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Center,
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Image(painterResource(R.drawable.ic_share_empty_state), contentDescription = null)
            Text(
                text = stringResource(R.string.share_empty_state),
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        FileList(modifier.padding(bottom = offset), shareState, filesState, onFileRemove, onRemoveAll)
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
