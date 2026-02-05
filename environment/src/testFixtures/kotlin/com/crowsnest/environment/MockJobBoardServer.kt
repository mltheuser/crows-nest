package com.crowsnest.environment

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.ServerSocket

class MockJobBoardServer : Closeable {

    val port: Int = findFreePort()
    val baseUrl = "http://localhost:$port"

    // Simple in-memory database to simulate server-side rendering lookup
    private val jobDb =
            mapOf(
                    "1" to mapOf("title" to "Junior Kotlin Developer", "company" to "JetBrains"),
                    "2" to mapOf("title" to "Senior AI Engineer", "company" to "OpenAI")
            )

    private val server =
            embeddedServer(Netty, port = port) {
                        routing {
                            // Static resources (CSS, JS)
                            staticResources("/static", "www/static")

                            // HTML Page Endpoints
                            configurePageRoutes()

                            // JSON API Endpoints (for async JS loading)
                            configureApiRoutes()
                        }
                    }
                    .start(wait = false)

    private fun Route.configurePageRoutes() {
        // Landing Page
        get("/") {
            call.respondText(readResource("www/index.html"), ContentType.Text.Html)
        }

        // Job Detail Page (content loaded via JS from API)
        get("/job/{id}") {
            call.respondText(readResource("www/detail.html"), ContentType.Text.Html)
        }

        // Listing Page with URL-based Pagination (/jobs?page=1, /jobs?page=2)
        get("/jobs") {
            call.respondText(readResource("www/listing-url-pagination.html"), ContentType.Text.Html)
        }

        // Listing Page with Button-based Pagination (/jobs-button?page=1)
        get("/jobs-button") {
            call.respondText(readResource("www/listing-button-pagination.html"), ContentType.Text.Html)
        }

        // Empty Page - no jobs (for testing graceful error handling)
        get("/empty") {
            call.respondText(readResource("www/empty.html"), ContentType.Text.Html)
        }
    }

    private fun Route.configureApiRoutes() {
        route("/api") {
            // Get jobs listing
            get("/jobs") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val jobs = when (page) {
                    1 -> listOf(mapOf("id" to "1") + jobDb["1"]!!)
                    else -> listOf(mapOf("id" to "2") + jobDb["2"]!!)
                }
                call.respondText(Json.encodeToString(jobs), ContentType.Application.Json)
            }

            // Get job details
            get("/job/{id}") {
                val id = call.parameters["id"] ?: "0"
                val job = (jobDb[id] ?: mapOf("title" to "Unknown", "company" to "Unknown")) +
                        mapOf("id" to id)
                call.respondText(Json.encodeToString(job), ContentType.Application.Json)
            }
        }
    }

    override fun close() {
        // gracePeriod: Time to wait for in-flight requests to complete (accepts no new requests)
        // timeout: Hard limit after which the server is forcibly terminated
        server.stop(gracePeriodMillis = 100, timeoutMillis = 100)
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use {
            return it.localPort
        }
    }

    private fun readResource(path: String): String {
        // Loads files from src/test/resources
        return javaClass.classLoader.getResource(path)?.readText()
                ?: throw IllegalArgumentException("Resource not found: $path")
    }
}
