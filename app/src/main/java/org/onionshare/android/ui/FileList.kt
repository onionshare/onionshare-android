package org.onionshare.android.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onionshare.android.R
import org.onionshare.android.files.FileManager
import org.onionshare.android.server.SendFile
import org.onionshare.android.ui.theme.OnionshareTheme

@Composable
fun FileList(fileManagerState: State<FileManager.State>, onFileRemove: (SendFile) -> Unit) {
    val files = when (val state = fileManagerState.value) {
        is FileManager.State.FilesAdded -> state.files
        else -> error("Wrong state: $state")
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(files) { file ->
            FileRow(file, onFileRemove)
        }
    }
}

@Composable
fun FileRow(file: SendFile, onFileRemove: (SendFile) -> Unit) {
    Row(modifier = Modifier.padding(8.dp)) {
        Icon(
            imageVector = getIconFromMimeType(file.mimeType),
            contentDescription = "test",
            tint = MaterialTheme.colors.onSurface,
            modifier = Modifier
                .size(48.dp)
                .alpha(0.54f)
                .align(Alignment.CenterVertically)
        )
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

private fun getIconFromMimeType(mimeType: String?): ImageVector = when {
    mimeType == null -> Icons.Filled.InsertDriveFile
    mimeType.startsWith("image") -> Icons.Filled.Image
    mimeType.startsWith("video") -> Icons.Filled.Slideshow
    mimeType.startsWith("audio") -> Icons.Filled.MusicNote
    else -> Icons.Filled.InsertDriveFile
}

@Preview(showBackground = true/*, uiMode = UI_MODE_NIGHT_YES*/)
@Composable
fun FileRowPreview() {
    OnionshareTheme {
        FileRow(
            SendFile("foo", "1 KiB", Uri.parse("/foo"), null)
        ) { }
    }
}

@Preview(showBackground = true)
@Composable
fun FileListPreview() {
    OnionshareTheme {
        val files = listOf(
            SendFile("foo", "1 KiB", Uri.parse("/foo"), "image/jpeg"),
            SendFile("bar", "42 MiB", Uri.parse("/bar"), "video/mp4"),
            SendFile("foo bar", "23 MiB", Uri.parse("/foo/bar"), null),
        )
        val mutableState = remember {
            mutableStateOf(FileManager.State.FilesAdded(files))
        }
        FileList(mutableState) {}
    }
}
