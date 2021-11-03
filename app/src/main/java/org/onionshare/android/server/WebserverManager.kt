package org.onionshare.android.server

import com.mitchellbosecke.pebble.loader.ClasspathLoader
import io.ktor.application.Application
import io.ktor.application.ApplicationStarted
import io.ktor.application.ApplicationStopped
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.pebble.Pebble
import io.ktor.pebble.PebbleContent
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

internal const val PORT: Int = 17638

class WebserverManager @Inject constructor() {

    enum class State { STARTING, STARTED, STOPPING, STOPPED }

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state

    private var server: ApplicationEngine? = null

    fun start(sendPage: SendPage) {
        _state.value = State.STARTING
        server = embeddedServer(Netty, PORT, watchPaths = emptyList()) {
            install(CallLogging)
            install(Pebble) {
                loader(ClasspathLoader().apply { prefix = "assets/templates" })
            }
            addListener()
            routing {
                defaultRoutes()
                sendRoutes(sendPage)
            }
        }.also { it.start() }
    }

    fun stop() {
        _state.value = State.STOPPING
        server?.stop(1_000, 2_000)
    }

    private fun Application.addListener() {
        environment.monitor.subscribe(ApplicationStarted) {
            _state.value = State.STARTED
        }
        environment.monitor.subscribe(ApplicationStopped) {
            _state.value = State.STOPPED
        }
    }

    private fun Route.defaultRoutes() {
        static("css") {
            resources("assets/static/css")
        }
        static("img") {
            resources("assets/static/img")
        }
        static("js") {
            resources("assets/static/js")
        }
    }

    private fun Route.sendRoutes(sendPage: SendPage) {
        get("/") {
            call.respond(PebbleContent("send.html", sendPage.model))
        }
        get("/download") {
            call.respond("Not yet implemented")
        }
    }
}
