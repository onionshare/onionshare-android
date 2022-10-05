package org.onionshare.android.tor

import android.app.Application
import android.os.Build
import android.os.Build.SUPPORTED_ABIS
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private val LOG = getLogger(ExecutableManager::class.java)
private val LIBRARY_ARCHITECTURES = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
private const val OBFS4_LIB_NAME = "libobfs4proxy.so"
private const val SNOWFLAKE_LIB_NAME = "libsnowflake.so"

@Singleton
class ExecutableManager @Inject constructor(
    private val app: Application,
) {
    val obfs4Executable get() = File(app.applicationInfo.nativeLibraryDir, OBFS4_LIB_NAME)
    val snowflakeExecutableFile get() = File(app.applicationInfo.nativeLibraryDir, SNOWFLAKE_LIB_NAME)

    @Throws(IOException::class)
    fun installObfs4Executable() {
        installExecutable(obfs4Executable, obfs4Executable, OBFS4_LIB_NAME)
    }

    @Throws(IOException::class)
    fun installSnowflakeExecutable() {
        installExecutable(snowflakeExecutableFile, snowflakeExecutableFile, SNOWFLAKE_LIB_NAME)
    }

    @Throws(IOException::class)
    private fun installExecutable(extracted: File, lib: File, libName: String) {
        if (lib.exists()) {
            // If an older version left behind a binary, delete it
            if (extracted.exists()) {
                if (extracted.delete()) LOG.info("Deleted old binary")
                else LOG.info("Failed to delete old binary")
            }
        } else if (Build.VERSION.SDK_INT < 29) {
            // The binary wasn't extracted at install time. Try to extract it
            extractLibraryFromApk(libName, extracted)
        } else {
            // No point extracting the binary, we won't be allowed to execute it
            throw FileNotFoundException(lib.absolutePath)
        }
    }

    @Throws(IOException::class)
    private fun extractLibraryFromApk(libName: String, dest: File) {
        var sourceDir = File(app.applicationInfo.sourceDir)
        if (sourceDir.isFile) {
            // Look for other APK files in the same directory, if we're allowed
            val parent = sourceDir.parentFile
            if (parent != null) sourceDir = parent
        }
        val libPaths: List<String> = getSupportedLibraryPaths(libName)
        for (apk in findApkFiles(sourceDir)) {
            ZipInputStream(FileInputStream(apk)).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (libPaths.contains(e.name)) {
                        LOG.info("Extracting ${e.name} from ${apk.absolutePath}")
                        zin.use { inputStream ->
                            extract(inputStream, dest)
                        }
                        return
                    }
                    e = zin.nextEntry
                }
            }
        }
        throw FileNotFoundException(libName)
    }

    /**
     * Returns the paths at which libraries with the given name would be found
     * inside an APK file, for all architectures supported by the device, in
     * order of preference.
     */
    private fun getSupportedLibraryPaths(libName: String): List<String> {
        val architectures: MutableList<String> = ArrayList()
        for (abi in SUPPORTED_ABIS) {
            if (LIBRARY_ARCHITECTURES.contains(abi)) {
                architectures.add("lib/$abi/$libName")
            }
        }
        return architectures
    }

    /**
     * Returns all files with the extension .apk or .APK under the given root.
     */
    private fun findApkFiles(root: File): List<File> {
        val files = ArrayList<File>()
        findApkFiles(root, files)
        return files
    }

    private fun findApkFiles(f: File, files: MutableList<File>) {
        if (f.isFile && f.name.lowercase(Locale.getDefault()).endsWith(".apk")) {
            files.add(f)
        } else if (f.isDirectory) {
            val children = f.listFiles()
            if (children != null) {
                for (child in children) findApkFiles(child, files)
            }
        }
    }

    @Throws(IOException::class)
    private fun extract(inputStream: InputStream, dest: File) {
        dest.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }

}
