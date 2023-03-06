package org.onionshare.android.tor

sealed class TorState {

    data class Stopped(
        val failedToConnect: Boolean,
    ) : TorState()

    data class Starting(
        val progress: Int,
        val startTime: Long,
        val lastProgressTime: Long,
        val onion: String? = null,
    ) : TorState()

    data class Started(
        val onion: String,
    ) : TorState()

    data class Stopping(
        val failedToConnect: Boolean,
    ) : TorState()

}
