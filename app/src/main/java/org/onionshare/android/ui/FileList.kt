package org.onionshare.android.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import android.text.format.Formatter.formatShortFileSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onionshare.android.R
import org.onionshare.android.server.SendFile
import org.onionshare.android.ui.theme.OnionAccent
import org.onionshare.android.ui.theme.OnionshareTheme

@Composable
fun FileList(
    modifier: Modifier = Modifier,
    state: State<ShareUiState>,
    onFileRemove: (SendFile) -> Unit,
    onRemoveAll: () -> Unit,
) {
    val files = state.value.files
    val ctx = LocalContext.current
    val totalSize = formatShortFileSize(ctx, state.value.totalSize)
    val res = ctx.resources
    val text =
        res.getQuantityString(R.plurals.share_file_list_summary, files.size, files.size, totalSize)
    val scrollState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = scrollState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, modifier = Modifier
                    .weight(1f)
                    .padding(16.dp))
                if (state.value.allowsModifyingFiles) {
                    TextButton(onClick = onRemoveAll, Modifier.padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.clear_all),
                            color = MaterialTheme.colors.OnionAccent,
                        )
                    }
                }
            }
        }
        items(files) { file ->
            FileRow(file, state.value.allowsModifyingFiles, onFileRemove)
        }
    }
}

@Composable
fun FileRow(file: SendFile, editAllowed: Boolean, onFileRemove: (SendFile) -> Unit) {
    Row(modifier = Modifier.padding(8.dp)) {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Icon(
                imageVector = getIconFromMimeType(file.mimeType),
                contentDescription = "test",
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterVertically)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = file.basename,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(all = 2.dp),
            )
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = file.size_human,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(all = 2.dp)
                )
            }
        }
        if (editAllowed) {
            var expanded by remember { mutableStateOf(false) }
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "test",
                    modifier = Modifier.alpha(0.54f)
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        onClick = {
                            onFileRemove(file)
                            expanded = false
                        }
                    ) {
                        Text(stringResource(R.string.remove))
                    }
                }
            }
        }
    }
}

private fun getIconFromMimeType(mimeType: String?): ImageVector = when {
    mimeType == null -> Icons.Filled.InsertDriveFile
    mimeType.startsWith("image") -> Icons.Filled.Image
    mimeType.startsWith("video") -> Icons.Filled.Slideshow
    mimeType.startsWith("audio") -> Icons.Filled.MusicNote
    else -> Icons.Filled.InsertDriveFile
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun FileRowPreview(editAllowed: Boolean = true) {
    OnionshareTheme {
        Surface(color = MaterialTheme.colors.background) {
            FileRow(
                SendFile("foo", "1 KiB", 1, Uri.parse("/foo"), null),
                editAllowed,
            ) { }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileRowNoEditPreview() {
    FileRowPreview(false)
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun FileListPreviewDark() {
    OnionshareTheme {
        Surface {
            val files = listOf(
                SendFile("foo bar file", "1337 KiB", 1, Uri.parse("/foo"), null),
            )
            val mutableState = remember {
                mutableStateOf(ShareUiState.FilesAdded(files))
            }
            FileList(Modifier, mutableState, {}) {}
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileListPreview() {
    OnionshareTheme {
        val files = listOf(
            SendFile("foo", "1 KiB", 1, Uri.parse("/foo"), "image/jpeg"),
            SendFile("bar", "42 MiB", 2, Uri.parse("/bar"), "video/mp4"),
            SendFile("foo bar", "23 MiB", 3, Uri.parse("/foo/bar"), null),
        )
        val mutableState = remember {
            mutableStateOf(ShareUiState.FilesAdded(files))
        }
        FileList(Modifier, mutableState, {}) {}
    }
}
