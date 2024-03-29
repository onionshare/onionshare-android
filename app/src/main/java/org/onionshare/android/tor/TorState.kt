package org.onionshare.android.tor

sealed class TorState {

    object Stopped : TorState()

    data class Starting(
        val progress: Int,
        val lastProgressTime: Long,
    ) : TorState()

    object Started : TorState()

    data class Published(
        val onion: String,
    ) : TorState()

    object FailedToConnect : TorState()

    object Stopping : TorState()

}
