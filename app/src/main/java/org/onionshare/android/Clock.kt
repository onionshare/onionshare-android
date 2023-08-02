package org.onionshare.android

fun interface Clock {
    fun currentTimeMillis(): Long
}

object DefaultClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
