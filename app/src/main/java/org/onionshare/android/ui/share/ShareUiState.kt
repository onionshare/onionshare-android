package org.onionshare.android.ui.share

import org.onionshare.android.files.totalSize
import org.onionshare.android.server.SendFile

sealed class ShareUiState {

    abstract val files: List<SendFile>
    open val allowsModifyingFiles = true
    open val collapsableSheet = false
    val totalSize: Long get() = files.totalSize

    object NoFiles : ShareUiState() {
        override val files = emptyList<SendFile>()
    }

    data class FilesAdded(
        override val files: List<SendFile>,
    ) : ShareUiState()

    data class Starting(
        override val files: List<SendFile>,
        private val zipPercent: Int,
        private val torPercent: Int,
    ) : ShareUiState() {
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
        val onionAddress: String,
    ) : ShareUiState() {
        override val allowsModifyingFiles = false
        override val collapsableSheet = true
    }

    data class Complete(
        override val files: List<SendFile>,
    ) : ShareUiState()

    data class Stopping(
        override val files: List<SendFile>,
    ) : ShareUiState()

    data class ErrorAddingFile(
        override val files: List<SendFile>,
        val errorFile: SendFile? = null,
    ) : ShareUiState()

    data class Error(
        override val files: List<SendFile>,
    ) : ShareUiState()

}
