package org.onionshare.android.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
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

    private val contentLauncher = registerForActivityResult(GetMultipleContents()) { uris ->
        viewModel.onUrisReceived(uris)
    }

    private val batteryLauncher = registerForActivityResult(StartActivityForResult()) {
        // we just ignore the result and don't check for battery optimization again
        // assuming the user will understand if they didn't allow background
        viewModel.onSheetButtonClicked()
    }

    private fun onFabClicked() {
        try {
            contentLauncher.launch("*/*")
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.add_files_not_supported, LENGTH_SHORT).show()
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
