package org.onionshare.android.tor

import org.briarproject.moat.MoatApi
import java.io.File

fun interface MoatApiFactory {
    fun createMoatApi(lyrebirdExecutable: File, lyrebirdDir: File): MoatApi
}

object DefaultMoatApiFactory : MoatApiFactory {
    private const val MOAT_URL = "https://1723079976.rsc.cdn77.org/"
    private const val MOAT_FRONT = "www.phpmyadmin.net"

    override fun createMoatApi(lyrebirdExecutable: File, lyrebirdDir: File): MoatApi {
        return MoatApi(lyrebirdExecutable, lyrebirdDir, MOAT_URL, MOAT_FRONT)
    }
}
