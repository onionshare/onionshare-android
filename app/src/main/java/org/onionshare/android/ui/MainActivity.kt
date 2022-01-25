package org.onionshare.android.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import dagger.hilt.android.AndroidEntryPoint
import org.onionshare.android.R
import org.onionshare.android.ui.ShareUiState.FilesAdded
import org.onionshare.android.ui.theme.OnionshareTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnionshareTheme {
                Surface(color = MaterialTheme.colors.background) {
                    MainUi(
                        stateFlow = viewModel.shareState,
                        onFabClicked = this::onFabClicked,
                        onFileRemove = viewModel::removeFile,
                        onRemoveAll = viewModel::removeAll,
                        onSheetButtonClicked = this::onSheetButtonClicked,
                    )
                }
            }
        }
    }

    private val contentLauncher = registerForActivityResult(OpenDocuments()) { uris ->
        // TODO we might need to set this to true, or removing code that takes permissions
        viewModel.onUrisReceived(uris, false)
    }

    /**
     * Some phones seem to have a messed up Storage Access Framework and do not support OPEN_DOCUMENT.
     * This uses GET_CONTENT as a fall-back.
     */
    private val contentFallbackLauncher = registerForActivityResult(GetMultipleContents()) { uris ->
        viewModel.onUrisReceived(uris, false)
    }

    private val batteryLauncher = registerForActivityResult(StartActivityForResult()) {
        // we just ignore the result and don't check for battery optimization again
        // assuming the user will understand if they didn't allow background
        // TODO we might want to do user testing here to see if the assumption holds
        viewModel.onSheetButtonClicked()
    }

    private fun onFabClicked() {
        try {
            contentLauncher.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            try {
                contentFallbackLauncher.launch("*/*")
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.add_files_not_supported, LENGTH_SHORT).show()
            }
        }
    }

    private fun onSheetButtonClicked() {
        if (viewModel.shareState.value is FilesAdded &&
            !viewModel.isIgnoringBatteryOptimizations()
        ) {
            @SuppressLint("BatteryLife", "InlinedApi")
            val i = Intent().apply {
                action = ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            batteryLauncher.launch(i)
        } else {
            viewModel.onSheetButtonClicked()
        }
    }
}

private class OpenDocuments : OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        return intent
    }
}
