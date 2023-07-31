package org.onionshare.android

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.briarproject.moat.MoatApi
import org.briarproject.onionwrapper.CircumventionProvider
import org.briarproject.onionwrapper.LocationUtils
import org.briarproject.onionwrapper.TorWrapper
import org.junit.Test
import org.onionshare.android.tor.MoatApiFactory
import org.onionshare.android.tor.TorManager
import org.onionshare.android.tor.TorState
import org.onionshare.android.ui.settings.SettingsManager
import java.io.File
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TorManagerTest {

    private val app: Application = mockk()
    private val tor: TorWrapper = mockk()
    private val settingsManager: SettingsManager = mockk()
    private val circumventionProvider: CircumventionProvider = mockk()
    private val locationUtils: LocationUtils = mockk()
    private val moatApiFactory: MoatApiFactory = mockk()
    private val clock: Clock = mockk()
    private val dispatcher = StandardTestDispatcher()

    private val torManager: TorManager

    private val obfs4ExecutableFile = File("/usr/bin/echo")
    private val stateDir = File("/tmp")
    private val moatApi: MoatApi = mockk()

    init {
        every { tor.setObserver(any()) } just Runs

        torManager = TorManager(
            app = app,
            tor = tor,
            settingsManager = settingsManager,
            circumventionProvider = circumventionProvider,
            locationUtils = locationUtils,
            moatApiFactory = moatApiFactory,
            clock = clock,
            dispatcher = dispatcher
        )
    }

    @Test
    fun testSimpleStart() = runTest {
        every { clock.currentTimeMillis() } returns 1L andThen 2L
        every { app.startService(any()) } returns null
        every { tor.start() } just Runs
        every { settingsManager.automaticBridges } returns mutableStateOf(true)
        every { tor.enableNetwork(true) } just Runs

        torManager.state.test {
            assertIs<TorState.Stopped>(awaitItem())
            torManager.start()
            assertIs<TorState.Starting>(awaitItem()) // 0%
            assertIs<TorState.Starting>(awaitItem()) // 5%
            torManager.onState(TorWrapper.TorState.STARTING)

            torManager.onBootstrapPercentage(50)
            val starting50 = awaitItem()
            assertIs<TorState.Starting>(starting50) // 50%
            assertEquals(50, starting50.progress)

            torManager.onHsDescriptorUpload("foobar")
            val published = awaitItem()
            assertIs<TorState.Published>(published)
            assertEquals("foobar", published.onion)

            // subsequent descriptor uploads are ignored
            torManager.onHsDescriptorUpload("foobar")
            torManager.onHsDescriptorUpload("foobar")
        }
    }

    @Test
    fun testCircumventionFailure() = runTest {
        val startTime = 1L
        val startedTime = 2L
        val firstWaitTime = startedTime + 1
        val firstTimeJump = firstWaitTime + MINUTES.toMillis(2) + 1
        val secondWaitTime = firstTimeJump + 1
        val secondTimeJump = secondWaitTime + MINUTES.toMillis(2) + 1

        every { clock.currentTimeMillis() } returns startTime andThen startedTime
        every { app.startService(any()) } returns null
        every { tor.start() } just Runs
        every { settingsManager.automaticBridges } returns mutableStateOf(true)
        every { tor.enableNetwork(true) } just Runs

        // moat doesn't return bridges
        every { tor.obfs4ExecutableFile } returns obfs4ExecutableFile
        every { app.getDir("state", 0) } returns stateDir
        every { moatApiFactory.createMoatApi(obfs4ExecutableFile, stateDir) } returns moatApi
        every { moatApi.get() } returns emptyList()
        every { locationUtils.currentCountry } returns "br"
        every { moatApi.getWithCountry("br") } returns emptyList()

        // use built-in bridges (empty here as well)
        every { circumventionProvider.getSuitableBridgeTypes("br") } returns emptyList()
        every { tor.enableBridges(emptyList()) } just Runs

        torManager.state.test {
            assertIs<TorState.Stopped>(awaitItem())
            torManager.start()

            val beforeStart = awaitItem()
            assertIs<TorState.Starting>(beforeStart) // 0%
            assertEquals(0, beforeStart.progress)
            assertEquals(startTime, beforeStart.lastProgressTime)

            val afterStart = awaitItem()
            assertIs<TorState.Starting>(afterStart) // 5%
            assertEquals(5, afterStart.progress)
            assertEquals(startedTime, afterStart.lastProgressTime)

            // now a lot of time has passed and we run the startCheckJob
            every { clock.currentTimeMillis() } returnsMany listOf(
                firstWaitTime, firstTimeJump,
                secondWaitTime, secondTimeJump,
            )
            dispatcher.scheduler.runCurrent()

            val beforeMoat = awaitItem()
            assertIs<TorState.Starting>(beforeMoat)
            assertEquals(5, beforeMoat.progress) // progress didn't change
            assertEquals(firstWaitTime, beforeMoat.lastProgressTime)

            val afterBuiltIn = awaitItem()
            assertIs<TorState.Starting>(afterBuiltIn)
            assertEquals(5, afterBuiltIn.progress) // progress didn't change
            assertEquals(secondWaitTime, afterBuiltIn.lastProgressTime)

            assertIs<TorState.FailedToConnect>(awaitItem())
        }
    }

}
