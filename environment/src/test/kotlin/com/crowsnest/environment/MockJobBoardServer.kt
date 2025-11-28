package com.crowsnest.environment

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.Closeable
import java.net.ServerSocket

class MockJobBoardServer : Closeable {

    val port: Int = findFreePort()
    val baseUrl = "http://localhost:$port"

    // Simple in-memory database to simulate server-side rendering lookup
    private val jobDb = mapOf(
        "1" to mapOf("title" to "Junior Kotlin Developer", "company" to "JetBrains"),
        "2" to mapOf("title" to "Senior AI Engineer", "company" to "OpenAI")
    )

    // FIX: Removed explicit type ': NettyApplicationEngine' and initialized inline
    // This lets Kotlin infer the complex generic type automatically.
    private val server = embeddedServer(Netty, port = port) {
        routing {
            // 1. Serve Static Files (CSS, JS)
            // This maps the URL path /static to the classpath resource folder www/static
            staticResources("/static", "www/static")

            // 2. Landing Page
            get("/") {
                val html = readResource("www/index.html")
                call.respondText(html, ContentType.Text.Html)
            }

            // 3. Job Detail Page (Server-Side Rendering Simulation)
            get("/job/{id}") {
                val id = call.parameters["id"] ?: "0"
                val jobData = jobDb[id] ?: mapOf("title" to "Unknown", "company" to "Unknown")

                var html = readResource("www/detail.html")

                // Simple template replacement
                html = html.replace("{{id}}", id)
                html = html.replace("{{title}}", jobData["title"]!!)
                html = html.replace("{{company}}", jobData["company"]!!)

                call.respondText(html, ContentType.Text.Html)
            }
        }
    }.start(wait = false)

    override fun close() {
        server.stop(100, 100)
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    private fun readResource(path: String): String {
        // Loads files from src/test/resources
        return javaClass.classLoader.getResource(path)?.readText()
            ?: throw IllegalArgumentException("Resource not found: $path")
    }
}