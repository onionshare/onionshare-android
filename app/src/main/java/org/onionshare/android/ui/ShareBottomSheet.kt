package org.onionshare.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.font.FontStyle.Companion.Italic
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onionshare.android.R
import org.onionshare.android.ui.theme.IndicatorReady
import org.onionshare.android.ui.theme.IndicatorSharing
import org.onionshare.android.ui.theme.IndicatorStarting
import org.onionshare.android.ui.theme.OnionshareTheme

private data class BottomSheetUi(
    val indicatorIcon: ImageVector = Icons.Filled.Circle,
    val indicatorColor: Color,
    @StringRes val stateText: Int,
    @StringRes val buttonText: Int,
)

private fun getBottomSheetUi(state: ShareUiState) = when (state) {
    is ShareUiState.FilesAdded -> BottomSheetUi(
        indicatorColor = IndicatorReady,
        stateText = R.string.share_state_ready,
        buttonText = R.string.share_button_start,
    )
    is ShareUiState.Starting -> BottomSheetUi(
        indicatorColor = IndicatorStarting,
        stateText = R.string.share_state_starting,
        buttonText = R.string.share_button_starting,
    )
    is ShareUiState.Sharing -> BottomSheetUi(
        indicatorColor = IndicatorSharing,
        stateText = R.string.share_state_sharing,
        buttonText = R.string.share_button_stop,
    )
    is ShareUiState.Complete -> BottomSheetUi(
        indicatorIcon = Icons.Filled.CheckCircle,
        indicatorColor = IndicatorSharing,
        stateText = R.string.share_state_transfer_complete,
        buttonText = R.string.share_button_complete,
    )
    is ShareUiState.Error -> BottomSheetUi(
        indicatorColor = IndicatorReady,
        stateText = R.string.share_state_ready,
        buttonText = R.string.share_button_start,
    )
    is ShareUiState.NoFiles -> error("No bottom sheet in empty state.")
}

@Composable
fun BottomSheet(state: ShareUiState, onSheetButtonClicked: () -> Unit) {
    if (state is ShareUiState.NoFiles) return
    val sheetUi = getBottomSheetUi(state)
    Column {
        Image(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.Gray),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(24.dp, 14.dp)
                .padding(top = 4.dp)
                .align(CenterHorizontally),
        )
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
        ) {
            Icon(
                imageVector = sheetUi.indicatorIcon,
                tint = sheetUi.indicatorColor,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(sheetUi.stateText),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Divider(thickness = 2.dp)
        val colorControlNormal = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
        if (state is ShareUiState.Sharing) {
            Text(
                text = stringResource(R.string.share_onion_intro),
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
            )
            Row(modifier = Modifier.padding(16.dp)) {
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    Text(state.onionAddress, fontFamily = Monospace)
                }
                val clipBoardLabel = stringResource(R.string.clipboard_onion_service_label)
                val ctx = LocalContext.current
                val clipboard = ctx.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                Button(
                    onClick = {
                        val clip = ClipData.newPlainText(clipBoardLabel, state.onionAddress)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(ctx, R.string.clipboard_onion_service_copied, LENGTH_SHORT)
                            .show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.secondary,
                    ),
                    border = BorderStroke(1.dp, colorControlNormal),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .align(CenterVertically)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.requiredSize(24.dp),
                    )
                }
            }
            Divider(thickness = 2.dp)
        }
        Button(
            onClick = onSheetButtonClicked,
            colors = if (state is ShareUiState.Sharing) {
                ButtonDefaults.buttonColors(contentColor = Color.Red,
                    backgroundColor = MaterialTheme.colors.background)
            } else ButtonDefaults.buttonColors(),
            border = if (state is ShareUiState.Sharing) {
                BorderStroke(1.dp, colorControlNormal)
            } else null,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(sheetUi.buttonText),
                fontStyle = if (state is ShareUiState.Starting) Italic else null,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ShareBottomSheetReadyPreview() {
    OnionshareTheme {
        Surface(color = MaterialTheme.colors.background) {
            BottomSheet(
                state = ShareUiState.FilesAdded(emptyList(), 0L),
                onSheetButtonClicked = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShareBottomSheetStartingPreview() {
    OnionshareTheme {
        Surface(color = MaterialTheme.colors.background) {
            BottomSheet(
                state = ShareUiState.Starting(emptyList(), 0L),
                onSheetButtonClicked = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShareBottomSheetSharingPreview() {
    OnionshareTheme {
        Surface(color = MaterialTheme.colors.background) {
            BottomSheet(
                state = ShareUiState.Sharing(
                    emptyList(),
                    0L,
                    "http://openpravyvc6spbd4flzn4g2iqu4sxzsizbtb5aqec25t76dnoo5w7yd.onion/",
                ),
                onSheetButtonClicked = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ShareBottomSheetSharingPreviewNight() {
    ShareBottomSheetSharingPreview()
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ShareBottomSheetCompletePreview() {
    OnionshareTheme {
        Surface(color = MaterialTheme.colors.background) {
            BottomSheet(
                state = ShareUiState.Complete(emptyList(), 0L),
                onSheetButtonClicked = {},
            )
        }
    }
}
