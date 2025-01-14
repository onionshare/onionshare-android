package org.onionshare.android.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.Intent.EXTRA_STREAM
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
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
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                handleSend(intent.getParcelableExtra<Parcelable>(EXTRA_STREAM) as? Uri)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendMultiple(intent.getParcelableArrayListExtra(EXTRA_STREAM))
            }

            else -> super.onNewIntent(intent)
        }
    }

    private fun handleSend(uri: Uri?) {
        if (uri == null) return
        viewModel.onUrisReceived(listOf(uri), false)
    }

    private fun handleSendMultiple(parcelables: ArrayList<Parcelable>?) {
        if (parcelables == null) return
        val uris = parcelables.mapNotNull { it as? Uri }
        if (uris.isEmpty()) return
        viewModel.onUrisReceived(uris, false)
    }

    override fun onStart() {
        super.onStart()
        // ask for notification permission right away since we already ask for doze exception when pressing start
        if (SDK_INT >= 33 && checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(POST_NOTIFICATIONS)
        }
    }
}
