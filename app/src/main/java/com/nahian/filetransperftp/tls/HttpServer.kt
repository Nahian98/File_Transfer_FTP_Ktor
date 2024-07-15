package com.nahian.filetransperftp.tls

import android.content.Context
import com.nahian.filetransperftp.interfaces.SslCredentials
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import org.slf4j.LoggerFactory

class HttpServer(private val sslCredentials: SslCredentials, private val context: Context) {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->

        })

    private val logger = LoggerFactory.getLogger(HttpServer::class.java.simpleName)
    private val server = createServer()

    fun start() {
        server.start()
    }

    private fun createServer(): NettyApplicationEngine {
        return scope.embeddedServer(Netty) {
            // Logs all the requests performed
            install(CallLogging)

            routing {
                get("/") {
                    call.respond(mapOf("message" to "Local Network Integration server running. Scan QR or fill in IP address to connect."))
                }
            }
        }
    }

    private fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
            CoroutineScope.embeddedServer(
        factory: ApplicationEngineFactory<TEngine, TConfiguration>,
        module: Application.() -> Unit
    ): TEngine {
        val environment = applicationEngineEnvironment {
            this.parentCoroutineContext = coroutineContext + parentCoroutineContext
            this.log = logger
            this.module(module)

            connector {
                this.port = HTTP_PORT
            }

            sslConnector(
                sslCredentials.getKeyStore(),
                sslCredentials.getKeyAlias(),
                { sslCredentials.getKeyPassword().toCharArray() },
                { sslCredentials.getAliasPassword().toCharArray() }
            ) {
                this.port = HTTPS_PORT
                this.keyStorePath = sslCredentials.getKeyStoreFile()
            }
        }

        return embeddedServer(factory, environment)
    }

    fun stop() {
        server.stop(0, 0)
        scope.coroutineContext.cancelChildren()
    }

    companion object {
        const val HTTP_PORT = 8080
        const val HTTPS_PORT = 8443
    }
}