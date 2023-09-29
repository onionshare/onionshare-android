package org.onionshare.android.ui.share

import org.onionshare.android.server.SendFile

sealed class ShareUiState {

    open val allowsModifyingFiles = true
    open val collapsableSheet = false

    data object AddingFiles : ShareUiState()

    data class Starting(
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
        val onionAddress: String,
    ) : ShareUiState() {
        override val allowsModifyingFiles = false
        override val collapsableSheet = true
        // Don't allow onion address to be logged
        override fun toString() = "Sharing()"
    }

    data object Complete : ShareUiState()

    data object Stopping : ShareUiState() {
        override val allowsModifyingFiles = false
    }

    sealed class Error : ShareUiState()

    data class ErrorAddingFile(
        val errorFile: SendFile? = null,
    ) : Error()

    data class ErrorStarting(
        val torFailedToConnect: Boolean = false,
        val errorMsg: String? = null,
    ) : Error()

}
