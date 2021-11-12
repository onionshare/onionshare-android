package org.onionshare.android.ui

import org.onionshare.android.server.SendFile

sealed class ShareUiState(open val files: List<SendFile>, open val totalSize: Long) {

    open val allowsAddingFiles = true

    object NoFiles : ShareUiState(emptyList(), 0L)

    data class FilesAdded(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

    data class Starting(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize) {
        override val allowsAddingFiles = false
    }

    data class Sharing(
        override val files: List<SendFile>,
        override val totalSize: Long,
        val onionAddress: String,
    ) : ShareUiState(files, totalSize) {
        override val allowsAddingFiles = false
    }

    data class Complete(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

}
