package org.onionshare.android.ui.share

import org.onionshare.android.files.totalSize
import org.onionshare.android.server.SendFile

sealed class ShareUiState(val files: List<SendFile>) {

    open val allowsModifyingFiles = true
    open val collapsableSheet = false
    val totalSize: Long = files.totalSize

    object NoFiles : ShareUiState(emptyList())

    class FilesAdded(
        files: List<SendFile>,
    ) : ShareUiState(files)

    class Starting(
        files: List<SendFile>,
        private val zipPercent: Int,
        private val torPercent: Int,
    ) : ShareUiState(files) {
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

    class Sharing(
        files: List<SendFile>,
        val onionAddress: String,
    ) : ShareUiState(files) {
        override val allowsModifyingFiles = false
        override val collapsableSheet = true
    }

    class Complete(
        files: List<SendFile>,
    ) : ShareUiState(files)

    class Error(
        files: List<SendFile>,
        val errorFile: SendFile? = null,
    ) : ShareUiState(files)

}
