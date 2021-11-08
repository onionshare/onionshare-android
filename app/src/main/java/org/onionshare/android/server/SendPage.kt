package org.onionshare.android.server

import android.net.Uri
import java.io.File

data class SendPage(
    val fileName: String,
    private val fileSize: String,
    private val fileSizeHuman: String,
    private val title: String = "OnionShare",
    val zipFile: File,
) {
    private val files: ArrayList<SendFile> = ArrayList()
    val model: Map<String, Any>
        get() {
            return mapOf(
                "filename" to fileName,
                "filesize" to fileSize,
                "title" to title,
                "filesize_human" to fileSizeHuman,
                "is_zipped" to "1",
                "files" to files,
//                "download_individual_files" to "",
//                "dirs" to dirs,
            )
        }

    fun addFiles(newFiles: Collection<SendFile>) {
        files.addAll(newFiles)
    }
}

data class SendFile(
    /**
     * Used by template.
     */
    val basename: String,
    /**
     * Used by template.
     */
    val size_human: String,
    /**
     * Used internally to retrieve the file content.
     */
    val uri: Uri,
)
