package org.onionshare.android.ui

import org.onionshare.android.server.SendFile

sealed class ShareUiState(open val files: List<SendFile>, open val totalSize: Long) {

    object NoFiles : ShareUiState(emptyList(), 0L)

    data class FilesAdded(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

    data class Starting(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

    data class Sharing(
        override val files: List<SendFile>,
        override val totalSize: Long,
        val onionAddress: String,
    ) : ShareUiState(files, totalSize)

    data class Complete(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

}
