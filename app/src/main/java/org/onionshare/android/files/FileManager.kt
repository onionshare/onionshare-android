package org.onionshare.android.files

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.text.format.Formatter.formatShortFileSize
import android.util.Base64.NO_PADDING
import android.util.Base64.URL_SAFE
import android.util.Base64.encodeToString
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.onionshare.android.R
import org.onionshare.android.server.SendFile
import org.onionshare.android.server.SendPage
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

class FileErrorException(val file: SendFile) : IOException()

@Singleton
class FileManager @Inject constructor(
    app: Application,
) {
    private val ctx = app.applicationContext
    private val _filesState = MutableStateFlow(FilesState(emptyList()))
    val filesState = _filesState.asStateFlow()
    private val _zipState = MutableStateFlow<ZipState?>(null)
    val zipState = _zipState.asStateFlow()

    suspend fun addFiles(uris: List<Uri>, takePermission: Boolean) = withContext(Dispatchers.IO) {
        // taking persistable permissions only works with OPEN_DOCUMENT, not GET_CONTENT
        if (takePermission) {
            // take persistable Uri permission to prevent SecurityException
            // when activity got killed before we use the Uri
            val contentResolver = ctx.contentResolver
            uris.iterator().forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // we've seen this happening on SDK_INT 22, but don't know why
                    LOG.error("Error taking persistable Uri permission ", e)
                }
            }
        }

        // not supporting selecting entire folders with sub-folders
        addFiles(uris)
    }

    private fun addFiles(uris: List<Uri>) {
        val existingFiles = filesState.value.files
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
        _filesState.value = FilesState(existingFiles + files)
    }

    fun removeFile(file: SendFile) {
        // release persistable Uri permission again
        file.releaseUriPermission()

        val newList = filesState.value.files.filterNot { it == file }
        _filesState.value = FilesState(newList)
    }

    fun removeAll() {
        // release persistable Uri permissions again
        filesState.value.files.iterator().forEach { file ->
            file.releaseUriPermission()
        }
        _filesState.value = FilesState(emptyList())
    }

    suspend fun zipFiles(): ZipResult = withContext(Dispatchers.IO) {
        try {
            val sendPage = zipFilesInternal()
            ZipResult.Zipped(sendPage)
        } catch (e: FileErrorException) {
            // remove errorFile from list of files, so user can try again
            val newFiles = filesState.value.files.toMutableList().apply { remove(e.file) }
            _filesState.value = FilesState(newFiles)
            ZipResult.Error(e.file)
        } catch (e: Exception) {
            ZipResult.Error()
        }
    }

    @Throws(IOException::class, FileErrorException::class)
    private suspend fun zipFilesInternal(): SendPage {
        val files = filesState.value.files
        val zipFileName = encodeToString(Random.nextBytes(32), NO_PADDING or URL_SAFE).trimEnd()
        val zipFile = ctx.getFileStreamPath(zipFileName)
        currentCoroutineContext().ensureActive()
        _zipState.value = ZipState(zipFile, 0)
        try {
            ctx.openFileOutput(zipFileName, MODE_PRIVATE).use { fileOutputStream ->
                ZipOutputStream(fileOutputStream).use { zipStream ->
                    files.forEachIndexed { i, file ->
                        // check first if we got cancelled before adding another file to the zip
                        currentCoroutineContext().ensureActive()
                        val progress = ((i + 1) / files.size.toFloat() * 100).roundToInt()
                        try {
                            ctx.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                                zipStream.putNextEntry(ZipEntry(file.basename))
                                inputStream.copyTo(zipStream)
                            }
                            currentCoroutineContext().ensureActive()
                            _zipState.value = ZipState(zipFile, progress)
                        } catch (e: FileNotFoundException) {
                            LOG.warn("Error while opening file: ", e)
                            throw FileErrorException(file)
                        } catch (e: SecurityException) {
                            LOG.warn("Error while opening file: SecurityException")
                            throw FileErrorException(file)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            LOG.info("Got cancelled, deleting zip file...")
            zipFile.delete()
            throw e
        }
        currentCoroutineContext().ensureActive()
        _zipState.value = ZipState(zipFile, 100)
        zipFile.deleteOnExit()

        // get SendPage for updating to final state
        return getSendPage(files, zipFile)
    }

    @Throws(IOException::class)
    private fun getSendPage(files: List<SendFile>, zipFile: File): SendPage {
        val fileSize = zipFile.length()
        return SendPage(
            fileName = "download.zip",
            fileSize = fileSize.toString(),
            fileSizeHuman = formatShortFileSize(ctx, fileSize),
            zipFile = zipFile,
        ).apply {
            addFiles(files)
        }
    }

    fun stop() {
        _zipState.value?.zip?.delete()
    }

    private fun Uri.getFallBackName(): String? {
        return lastPathSegment?.split("/")?.last()
    }

    private fun SendFile.releaseUriPermission() {
        val contentResolver = ctx.contentResolver
        try {
            contentResolver.releasePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            LOG.warn("Error releasing PersistableUriPermission", e)
        }
    }

}

val List<SendFile>.totalSize get() = sumOf { it.size }
