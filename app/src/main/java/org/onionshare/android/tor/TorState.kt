package org.onionshare.android.tor

sealed class TorState {

    object Stopped : TorState()

    data class Starting(
        val progress: Int,
        val startTime: Long,
        val lastProgressTime: Long,
        val onion: String? = null,
    ) : TorState()

    data class Started(
        val onion: String,
    ) : TorState()

    object Stopping : TorState()

}
