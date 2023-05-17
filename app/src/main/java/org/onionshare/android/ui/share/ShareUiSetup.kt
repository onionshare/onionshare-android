package org.onionshare.android.ui.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import org.briarproject.android.dontkillmelib.DozeUtils.getDozeWhitelistingIntent
import org.onionshare.android.R
import org.onionshare.android.ui.MainViewModel

@Composable
internal fun ShareUiSetup(navController: NavHostController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val contentLauncher = rememberLauncherForActivityResult(OpenDocuments()) { uris ->
        onUrisReceived(context, viewModel, uris, true)
    }
    // Some phones seem to have a messed up Storage Access Framework and do not support OPEN_DOCUMENT.
    // This uses GET_CONTENT as a fall-back.
    val contentFallbackLauncher = rememberLauncherForActivityResult(GetMultipleContents()) { uris ->
        onUrisReceived(context, viewModel, uris, false)
    }
    val batteryLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        // we just ignore the result and don't check for battery optimization again
        // assuming the user will understand if they didn't allow background
        // TODO we might want to do user testing here to see if the assumption holds
        viewModel.onSheetButtonClicked()
    }
    val shareState = viewModel.shareState.collectAsState()
    val filesState = viewModel.filesState.collectAsState()
    ShareUi(
        navController = navController,
        shareState = shareState.value,
        filesState = filesState.value,
        onFabClicked = { onFabClicked(context, contentLauncher, contentFallbackLauncher) },
        onFileRemove = viewModel::removeFile,
        onRemoveAll = viewModel::removeAll
    ) { onSheetButtonClicked(context, viewModel, batteryLauncher) }
}

private fun onUrisReceived(
    context: Context,
    viewModel: MainViewModel,
    uris: List<Uri>,
    takePermission: Boolean,
) {
    if (uris.isEmpty()) {
        // user backed out of select activity
        Toast.makeText(context, R.string.warning_no_files_added, Toast.LENGTH_LONG).show()
    } else {
        viewModel.onUrisReceived(uris, takePermission)
    }
}

private fun onFabClicked(
    context: Context,
    contentLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>,
    contentFallbackLauncher: ManagedActivityResultLauncher<String, List<Uri>>,
) {
    try {
        contentLauncher.launch(arrayOf("*/*"))
    } catch (e: ActivityNotFoundException) {
        try {
            contentFallbackLauncher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.add_files_not_supported, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun onSheetButtonClicked(
    context: Context,
    viewModel: MainViewModel,
    batteryLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
) {
    if (viewModel.shareState.value is ShareUiState.AddingFiles && viewModel.needsDozeWhitelisting) {
        try {
            batteryLauncher.launch(getDozeWhitelistingIntent(context))
        } catch (e: ActivityNotFoundException) {
            // this is really unusual (happened once on a Samsung Galaxy A5 with SDK 23), just pray and proceed
            viewModel.onSheetButtonClicked()
        }
    } else {
        viewModel.onSheetButtonClicked()
    }
}

private class OpenDocuments : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}
