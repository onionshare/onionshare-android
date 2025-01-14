package org.onionshare.android.ui

import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * We can use this for a legacy TextView that allows for styled text until compose can do it itself.
 */
@Composable
fun StyledLegacyText(@StringRes id: Int, modifier: Modifier = Modifier) {
    val text = LocalContext.current.resources.getText(id)
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context -> TextView(context) },
        update = { textView ->
            textView.textSize = 16f
            textView.setTextColor(color)
            textView.text = text
        }
    )
}
