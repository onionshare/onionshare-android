package org.onionshare.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.onionshare.android.R
import org.onionshare.android.ui.theme.OnionBlue

@Composable
fun CopyButton(toCopy: String, clipBoardLabel: String) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val colorControlNormal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Button(
        onClick = {
            val clip = ClipData.newPlainText(clipBoardLabel, toCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, R.string.clipboard_onion_service_copied, Toast.LENGTH_SHORT)
                .show()
        },
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.OnionBlue,
        ),
        border = BorderStroke(1.dp, colorControlNormal),
        shape = RoundedCornerShape(32.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
        ),
        modifier = Modifier
            .size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.requiredSize(24.dp),
        )
    }
}
