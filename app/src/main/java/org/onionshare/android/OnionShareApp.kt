package org.onionshare.android

import android.app.Application
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.UriPermission
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OnionShareApp @Inject constructor() : Application() {

    override fun onCreate() {
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
        releaseUriPermissions()
    }

    /**
     * There's a limit to how many persistable [UriPermission]s we can hold.
     * At each app start, we release the ones that we may still hold from last time.
     */
    private fun releaseUriPermissions() {
        val contentResolver = applicationContext.contentResolver
        contentResolver.persistedUriPermissions.forEach { uriPermission ->
            contentResolver.releasePersistableUriPermission(uriPermission.uri, FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun enableStrictMode() {
        val threadPolicy: ThreadPolicy.Builder = ThreadPolicy.Builder().apply {
            detectAll()
            penaltyLog()
            penaltyFlashScreen()
        }
        val vmPolicy = VmPolicy.Builder().apply {
            detectAll()
            penaltyLog()
        }
        StrictMode.setThreadPolicy(threadPolicy.build())
        StrictMode.setVmPolicy(vmPolicy.build())
    }

}
