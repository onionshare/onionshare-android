package org.onionshare.android.ui

import org.onionshare.android.server.SendFile

sealed class ShareUiState(open val files: List<SendFile>, open val totalSize: Long) {

    open val allowsModifyingFiles = true

    object NoFiles : ShareUiState(emptyList(), 0L)

    data class FilesAdded(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

    data class Starting(
        override val files: List<SendFile>,
        override val totalSize: Long,
        val zipPercent: Int,
        val torPercent: Int,
    ) : ShareUiState(files, totalSize) {
        override val allowsModifyingFiles = false
        val totalProgress: Float
            get() {
                val sum = zipPercent.toFloat() / 2 + torPercent.toFloat() / 2
                return sum / 100
            }

        init {
            require(zipPercent in 0..100)
            require(torPercent in 0..100)
        }
    }

    data class Sharing(
        override val files: List<SendFile>,
        override val totalSize: Long,
        val onionAddress: String,
    ) : ShareUiState(files, totalSize) {
        override val allowsModifyingFiles = false
    }

    data class Complete(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

    data class Error(
        override val files: List<SendFile>,
        override val totalSize: Long,
    ) : ShareUiState(files, totalSize)

}
