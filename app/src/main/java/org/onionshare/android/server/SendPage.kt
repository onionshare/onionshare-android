package org.onionshare.android.server

data class SendPage(
    private val fileName: String,
    private val fileSize: String,
    private val fileSizeHuman: String,
    private val title: String = "OnionShare",
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
    val basename: String,
    val size_human: String,
)
