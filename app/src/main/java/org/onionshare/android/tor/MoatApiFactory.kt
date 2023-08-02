package org.onionshare.android.tor

import org.briarproject.moat.MoatApi
import java.io.File

fun interface MoatApiFactory {
    fun createMoatApi(obfs4Executable: File, obfs4Dir: File): MoatApi
}

object DefaultMoatApiFactory : MoatApiFactory {
    private const val MOAT_URL = "https://onion.azureedge.net/"
    private const val MOAT_FRONT = "ajax.aspnetcdn.com"

    override fun createMoatApi(obfs4Executable: File, obfs4Dir: File): MoatApi {
        return MoatApi(obfs4Executable, obfs4Dir, MOAT_URL, MOAT_FRONT)
    }
}
