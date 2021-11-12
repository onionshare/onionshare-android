package org.onionshare.android.server

import android.util.Base64
import android.util.Base64.NO_PADDING
import android.util.Base64.URL_SAFE
import com.mitchellbosecke.pebble.loader.ClasspathLoader
import io.ktor.application.Application
import io.ktor.application.ApplicationStarted
import io.ktor.application.ApplicationStopped
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.StatusPages
import io.ktor.http.ContentDisposition.Companion.Attachment
import io.ktor.http.ContentDisposition.Parameters.FileName
import io.ktor.http.HttpHeaders.ContentDisposition
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.pebble.Pebble
import io.ktor.pebble.PebbleContent
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.isActive
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal const val PORT: Int = 17638

class WebserverManager @Inject constructor() {

    enum class State { STARTED, STOPPED }

    private val secureRandom = SecureRandom()
    private var server: ApplicationEngine? = null

    suspend fun start(sendPage: SendPage) = suspendCoroutine<State> { continuation ->
        val staticPath = getStaticPath()
        val staticPathMap = mapOf("static_url_path" to staticPath)
        server = embeddedServer(Netty, PORT, watchPaths = emptyList()) {
            install(CallLogging)
            install(Pebble) {
                loader(ClasspathLoader().apply { prefix = "assets/templates" })
            }
            installStatusPages(staticPathMap)
            // this method will not return until the continuation in that listener gets resumed
            addListener(continuation)
            routing {
                defaultRoutes(staticPath)
                sendRoutes(sendPage, staticPathMap)
            }
        }.also { it.start() }
    }

    fun stop() {
        server?.stop(500, 1_000)
    }

    private fun getStaticPath(): String {
        val staticSuffixBytes = ByteArray(16).apply { secureRandom.nextBytes(this) }
        val staticSuffix =
            Base64.encodeToString(staticSuffixBytes, NO_PADDING or URL_SAFE).trimEnd()
        return "/static_$staticSuffix"
    }

    private fun Application.addListener(continuation: Continuation<State>) {
        environment.monitor.subscribe(ApplicationStarted) {
            if (continuation.context.isActive) continuation.resume(State.STARTED)
        }
        environment.monitor.subscribe(ApplicationStopped) {
            if (continuation.context.isActive) continuation.resume(State.STOPPED)
        }
    }

    private fun Application.installStatusPages(staticPathMap: Map<String, String>) {
        install(StatusPages) {
            status(HttpStatusCode.NotFound) {
                call.respond(PebbleContent("404.html", staticPathMap))
            }
            status(HttpStatusCode.MethodNotAllowed) {
                call.respond(PebbleContent("405.html", staticPathMap))
            }
            status(HttpStatusCode.InternalServerError) {
                call.respond(PebbleContent("500.html", staticPathMap))
            }
        }
    }

    private fun Route.defaultRoutes(staticPath: String) {
        static("$staticPath/css") {
            resources("assets/static/css")
        }
        static("$staticPath/img") {
            resources("assets/static/img")
        }
        static("$staticPath/js") {
            resources("assets/static/js")
        }
    }

    private fun Route.sendRoutes(sendPage: SendPage, staticPathMap: Map<String, String>) {
        get("/") {
            val model = sendPage.model + staticPathMap
            call.respond(PebbleContent("send.html", model))
        }
        get("/download") {
            call.response.header(
                ContentDisposition,
                Attachment.withParameter(FileName, sendPage.fileName).toString()
            )
            call.respondFile(sendPage.zipFile)
        }
    }
}
