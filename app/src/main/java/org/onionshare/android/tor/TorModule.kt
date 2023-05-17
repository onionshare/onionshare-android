package org.onionshare.android.tor

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManager
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManagerFactory
import org.briarproject.onionwrapper.AndroidLocationUtilsFactory
import org.briarproject.onionwrapper.AndroidTorWrapper
import org.briarproject.onionwrapper.CircumventionProvider
import org.briarproject.onionwrapper.CircumventionProviderFactory
import org.briarproject.onionwrapper.LocationUtils
import org.briarproject.onionwrapper.TorWrapper
import java.io.File
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class TorModule {

    companion object {
        private const val SOCKS_PORT = 53054
        private const val CONTROL_PORT = 53055

        private fun getTorDir(application: Application): File {
            return application.getDir("tor", Context.MODE_PRIVATE)
        }

        private val architecture: String
            get() {
                for (abi in Build.SUPPORTED_ABIS) {
                    return when {
                        abi.startsWith("x86_64") -> "x86_64_pie"
                        abi.startsWith("x86") -> "x86_pie"
                        abi.startsWith("arm64") -> "arm64_pie"
                        abi.startsWith("armeabi") -> "arm_pie"
                        else -> continue
                    }
                }
                error("Tor is not supported on this architecture")
            }
    }

    @Provides
    @Singleton
    internal fun provideTorWrapper(
        application: Application,
        wakeLockManager: AndroidWakeLockManager,
    ): TorWrapper {
        val ioExecutor = ThreadPoolExecutor(
            0,
            Int.MAX_VALUE,
            60,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            ThreadPoolExecutor.DiscardPolicy()
        )
        return AndroidTorWrapper(
            application,
            wakeLockManager,
            ioExecutor,
            ContextCompat.getMainExecutor(application),
            architecture,
            getTorDir(application),
            SOCKS_PORT,
            CONTROL_PORT
        )
    }

    @Provides
    @Singleton
    internal fun provideCircumventionProvider(): CircumventionProvider {
        return CircumventionProviderFactory.createCircumventionProvider()
    }

    @Provides
    @Singleton
    internal fun provideLocationUtils(app: Application): LocationUtils {
        return AndroidLocationUtilsFactory.createAndroidLocationUtils(app)
    }

    @Provides
    @Singleton
    internal fun provideWakeLockManager(app: Application): AndroidWakeLockManager {
        return AndroidWakeLockManagerFactory.createAndroidWakeLockManager(app)
    }
}
