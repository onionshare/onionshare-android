package org.onionshare.android

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.UriPermission
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Process
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import dagger.hilt.android.HiltAndroidApp
import org.onionshare.android.ui.MainActivity
import org.slf4j.bridge.SLF4JBridgeHandler
import javax.inject.Inject

@HiltAndroidApp
class OnionShareApp @Inject constructor() : Application(), ActivityLifecycleCallbacks {

    var isActivityStarted: Boolean = false
        private set

    override fun onCreate() {
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        if (!isTorProcess()) releaseUriPermissions()
        // Route java.util.logging through Logback
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
    }

    private fun isTorProcess(): Boolean {
        val processName = if (SDK_INT >= 28) {
            getProcessName()
        } else {
            val pid = Process.myPid()
            val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: return false
        }
        return processName.endsWith(":tor")
    }

    /**
     * There's a limit to how many persistable [UriPermission]s we can hold.
     * At each app start, we release the ones that we may still hold from last time.
     */
    private fun releaseUriPermissions() {
        val contentResolver = applicationContext.contentResolver
        contentResolver.persistedUriPermissions.iterator().forEach { uriPermission ->
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

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is MainActivity) isActivityStarted = true
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is MainActivity) isActivityStarted = false
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

}
