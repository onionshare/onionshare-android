package org.onionshare.android.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.onionshare.android.files.FileManager
import org.onionshare.android.server.SendFile
import org.onionshare.android.ui.theme.OnionshareTheme

@Composable
fun FileList(fileManagerState: State<FileManager.State>) {
    val files = when (val state = fileManagerState.value) {
        is FileManager.State.FilesAdded -> state.files
        else -> error("Wrong state: $state")
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(files) { file ->
            Card {
                Row {
                    Text(file.basename, modifier = Modifier
                        .padding(all = 16.dp)
                        .weight(1f))
                    Text(file.size_human, modifier = Modifier.padding(all = 16.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun FileListPreview() {
    OnionshareTheme {
        val files = listOf(
            SendFile("foo", "1 KiB", Uri.parse("/foo")),
            SendFile("bar", "42 MiB", Uri.parse("/bar")),
        )
        val mutableState = remember {
            mutableStateOf(FileManager.State.FilesAdded(files))
        }
        FileList(mutableState)
    }
}
