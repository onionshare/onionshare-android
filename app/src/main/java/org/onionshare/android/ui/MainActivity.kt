package org.onionshare.android.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) {
        // we don't care if the user wants to see notifications or not
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainUi(viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        // ask for notification permission right away since we already ask for doze exception when pressing start
        if (SDK_INT >= 33 && checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(POST_NOTIFICATIONS)
        }
    }
}
