package org.onionshare.android.files

import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
import java.io.File

data class FilesState(val files: List<SendFile>) {
    val totalSize: Long get() = files.totalSize
}

data class ZipState(
    val zip: File,
    val progress: Int,
)

sealed class ZipResult {

    data class Zipped(
        val sendPage: SendPage,
    ) : ZipResult()

    data class Error(
        val errorFile: SendFile? = null,
    ) : ZipResult()

}
