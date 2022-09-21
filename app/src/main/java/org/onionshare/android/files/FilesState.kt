package org.onionshare.android.files

import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
import java.io.File

sealed class FilesState {
    abstract val files: List<SendFile>

    data class Added(override val files: List<SendFile>) : FilesState()

    data class Zipping(
        override val files: List<SendFile>,
        val zip: File,
        val progress: Int,
    ) : FilesState()

    data class Zipped(
        override val files: List<SendFile>,
        val sendPage: SendPage,
    ) : FilesState()

    data class Error(
        override val files: List<SendFile>,
        val errorFile: SendFile? = null,
    ) : FilesState()
}
