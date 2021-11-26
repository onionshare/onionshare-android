package org.onionshare.android.files

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.text.format.Formatter.formatShortFileSize
import android.util.Base64.NO_PADDING
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.onionshare.android.R
import org.onionshare.android.files.FileManager.State.FilesAdded
import org.onionshare.android.files.FileManager.State.FilesReadyForDownload
import org.onionshare.android.server.SendFile
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private val LOG = getLogger(FileManager::class.java)

@Singleton
class FileManager @Inject constructor(
    app: Application,
) {

    sealed class State {
        open class FilesAdded(val files: List<SendFile>) : State()
        class FilesReadyForDownload(files: List<SendFile>, val zip: File) : FilesAdded(files)
    }

    private val ctx = app.applicationContext

    fun addFiles(uris: List<Uri>, existingFiles: List<SendFile>): FilesAdded {
        val files = uris.mapNotNull { uri ->
            // continue if we already have that file
            if (existingFiles.any { it.uri == uri }) return@mapNotNull null

            val documentFile = DocumentFile.fromSingleUri(ctx, uri) ?: error("Only API < 19")
            val name = documentFile.name ?: uri.getFallBackName() ?: error("Uri has no path $uri")
            val size = documentFile.length()
            val sizeHuman = if (size == 0L) ctx.getString(R.string.unknown) else {
                formatShortFileSize(ctx, size)
            }
            SendFile(name, sizeHuman, size, uri, documentFile.type)
        }
        return FilesAdded(existingFiles + files)
    }

    @Throws(IOException::class)
    suspend fun zipFiles(files: List<SendFile>): FilesReadyForDownload {
        val zipFileName = encodeToString(Random.nextBytes(32), NO_PADDING or URL_SAFE).trimEnd()
        val zipFile = ctx.getFileStreamPath(zipFileName)
        try {
            @Suppress("BlockingMethodInNonBlockingContext")
            ctx.openFileOutput(zipFileName, MODE_PRIVATE).use { fileOutputStream ->
                ZipOutputStream(fileOutputStream).use { zipStream ->
                    files.forEach { file ->
                        // check first if we got cancelled before adding another file to the zip
                        currentCoroutineContext().ensureActive()
                        LOG.debug("Zipping next file: ${file.basename}")
                        ctx.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                            zipStream.putNextEntry(ZipEntry(file.basename))
                            inputStream.copyTo(zipStream)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            zipFile.delete()
        }
        // TODO we should take better care to clean up old zip files properly
        zipFile.deleteOnExit()
        return FilesReadyForDownload(files, zipFile)
    }

    private fun Uri.getFallBackName(): String? {
        return lastPathSegment?.split("/")?.last()
    }

}
