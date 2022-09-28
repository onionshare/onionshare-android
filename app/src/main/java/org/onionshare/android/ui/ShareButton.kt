package org.onionshare.android.ui

import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.onionshare.android.R
import org.onionshare.android.ui.theme.OnionBlue

@Composable
fun ShareButton(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    IconButton(
        modifier = modifier,
        onClick = {
            val sendIntent = Intent().apply {
                action = ACTION_SEND
                putExtra(EXTRA_TEXT, text)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = stringResource(R.string.share),
            tint = MaterialTheme.colors.OnionBlue,
        )
    }
}
