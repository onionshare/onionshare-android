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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.onionshare.android.R
import org.onionshare.android.server.SendFile
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

private val LOG = getLogger(FileManager::class.java)

data class FilesAdded(val files: List<SendFile>)
data class FilesZipping(val files: List<SendFile>, val zip: File, val progress: Int, val complete: Boolean = false)

class FileErrorException(val file: SendFile) : IOException()

@Singleton
class FileManager @Inject constructor(
    app: Application,
) {
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
    suspend fun zipFiles(files: List<SendFile>): Flow<FilesZipping> = flow {
        val zipFileName = encodeToString(Random.nextBytes(32), NO_PADDING or URL_SAFE).trimEnd()
        val zipFile = ctx.getFileStreamPath(zipFileName)
        emit(FilesZipping(files, zipFile, 0))
        try {
            @Suppress("BlockingMethodInNonBlockingContext")
            ctx.openFileOutput(zipFileName, MODE_PRIVATE).use { fileOutputStream ->
                ZipOutputStream(fileOutputStream).use { zipStream ->
                    files.forEachIndexed { i, file ->
                        // check first if we got cancelled before adding another file to the zip
                        currentCoroutineContext().ensureActive()
                        val progress = ((i + 1) / files.size.toFloat() * 100).roundToInt()
                        // TODO remove before release
                        LOG.debug("Zipping next file $progress/100: ${file.basename}")
                        try {
                            ctx.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                                zipStream.putNextEntry(ZipEntry(file.basename))
                                inputStream.copyTo(zipStream)
                            }
                            emit(FilesZipping(files, zipFile, progress))
                        } catch (e: FileNotFoundException) {
                            LOG.warn("Error while opening file: ", e)
                            throw FileErrorException(file)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            zipFile.delete()
        }
        // TODO we should take better care to clean up old zip files properly
        zipFile.deleteOnExit()
        emit(FilesZipping(files, zipFile, 100, true))
    }

    private fun Uri.getFallBackName(): String? {
        return lastPathSegment?.split("/")?.last()
    }

}

val List<SendFile>.totalSize get() = sumOf { it.size }
