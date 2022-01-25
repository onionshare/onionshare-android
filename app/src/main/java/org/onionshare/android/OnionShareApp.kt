package org.onionshare.android

import android.app.Application
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
