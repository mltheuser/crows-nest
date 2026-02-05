package com.crowsnest.web

import at.favre.lib.crypto.bcrypt.BCrypt
import com.crowsnest.database.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.util.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UserSession(val userId: String? = null, val pendingSearch: PendingSearch? = null)

@Serializable data class PendingSearch(val whatISearch: String, val aboutMe: String)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { configureApp() }.start(wait = true)
}

fun Application.configureApp() {
    val userRepo = DatabaseFactory.createUserRepository()
    val seekerRepo = DatabaseFactory.createSeekerRepository()

    val httpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 7 // 1 week
        }
    }

    install(Authentication) {
        oauth("auth-oauth-google") {
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                        name = "google",
                        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                        accessTokenUrl = "https://oauth2.googleapis.com/token",
                        requestMethod = HttpMethod.Post,
                        clientId = System.getenv("GOOGLE_CLIENT_ID") ?: "",
                        clientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: "",
                        defaultScopes =
                                listOf(
                                        "https://www.googleapis.com/auth/userinfo.email",
                                        "https://www.googleapis.com/auth/userinfo.profile"
                                )
                )
            }
            client = httpClient
        }
    }

    routing {
        staticResources("/static", "static")

        // Landing page
        get("/") { call.respondHtml(HttpStatusCode.OK) { landingPage() } }

        // Search form
        get("/search") { call.respondHtml(HttpStatusCode.OK) { searchPage() } }

        // Submit search → store in session → redirect to auth
        post("/search") {
            val params = call.receiveParameters()
            val whatISearch = params["whatISearch"] ?: ""
            val aboutMe = params["aboutMe"] ?: ""

            if (whatISearch.isBlank() || aboutMe.isBlank()) {
                call.respondHtml(HttpStatusCode.BadRequest) {
                    body { div("error") { +"Please fill in all fields." } }
                }
                return@post
            }

            val currentSession = call.sessions.get<UserSession>() ?: UserSession()

            // If already logged in, save directly
            if (currentSession.userId != null) {
                saveSeeker(seekerRepo, UUID.fromString(currentSession.userId), whatISearch, aboutMe)
                call.respondRedirect("/dashboard?saved=true")
                return@post
            }

            // Store pending search and redirect to auth
            call.sessions.set(
                    currentSession.copy(pendingSearch = PendingSearch(whatISearch, aboutMe))
            )
            call.respondRedirect("/auth")
        }

        val googleEnabled = System.getenv("GOOGLE_CLIENT_ID")?.isNotBlank() == true

        // Auth page
        get("/auth") {
            val error = call.request.queryParameters["error"]
            call.respondHtml(HttpStatusCode.OK) {
                authPage(error = error, googleEnabled = googleEnabled)
            }
        }

        // Email signup
        post("/auth/signup") {
            val params = call.receiveParameters()
            val email = params["email"] ?: ""
            val password = params["password"] ?: ""

            if (email.isBlank() || password.length < 6) {
                call.respondHtml(HttpStatusCode.BadRequest) {
                    authPage(
                            error = "Email required, password must be 6+ characters.",
                            googleEnabled = googleEnabled
                    )
                }
                return@post
            }

            // Check if exists
            val existing = userRepo.findByEmail(email)
            if (existing != null) {
                call.respondHtml(HttpStatusCode.Conflict) {
                    authPage(
                            error = "Email already registered. Try signing in.",
                            googleEnabled = googleEnabled
                    )
                }
                return@post
            }

            val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
            val userId = userRepo.save(User(email = email, passwordHash = hash))

            completeAuth(call, seekerRepo, userId)
        }

        // Email login
        post("/auth/login") {
            val params = call.receiveParameters()
            val email = params["email"] ?: ""
            val password = params["password"] ?: ""

            val user = userRepo.findByEmail(email)
            if (user == null || user.passwordHash == null) {
                call.respondHtml(HttpStatusCode.Unauthorized) {
                    authPage(error = "Invalid email or password.", googleEnabled = googleEnabled)
                }
                return@post
            }

            val verified = BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash)
            if (!verified.verified) {
                call.respondHtml(HttpStatusCode.Unauthorized) {
                    authPage(error = "Invalid email or password.", googleEnabled = googleEnabled)
                }
                return@post
            }

            completeAuth(call, seekerRepo, user.id!!)
        }

        // Google OAuth
        authenticate("auth-oauth-google") {
            get("/auth/google") {
                // Redirects automatically
            }

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                if (principal == null) {
                    call.respondRedirect("/auth?error=oauth_failed")
                    return@get
                }

                // Get user info from Google
                val userInfo = getGoogleUserInfo(httpClient, principal.accessToken)
                if (userInfo == null) {
                    call.respondRedirect("/auth?error=oauth_failed")
                    return@get
                }

                // Find or create user
                var user = userRepo.findByOAuth("google", userInfo.id)
                if (user == null) {
                    user = userRepo.findByEmail(userInfo.email)
                    if (user == null) {
                        val userId =
                                userRepo.save(
                                        User(
                                                email = userInfo.email,
                                                oauthProvider = "google",
                                                oauthId = userInfo.id
                                        )
                                )
                        user = User(id = userId, email = userInfo.email)
                    }
                }

                completeAuth(call, seekerRepo, user.id!!)
            }
        }

        // Dashboard (protected)
        get("/dashboard") {
            val session = call.sessions.get<UserSession>()
            if (session?.userId == null) {
                call.respondRedirect("/auth")
                return@get
            }

            val saved = call.request.queryParameters["saved"] == "true"
            call.respondHtml(HttpStatusCode.OK) { dashboardPage(saved) }
        }

        // Logout
        post("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/")
        }
    }
}

private suspend fun completeAuth(
        call: ApplicationCall,
        seekerRepo: SeekerRepository,
        userId: UUID
) {
    val session = call.sessions.get<UserSession>() ?: UserSession()
    val pending = session.pendingSearch

    // Save pending search if exists
    if (pending != null) {
        saveSeeker(seekerRepo, userId, pending.whatISearch, pending.aboutMe)
    }

    // Set session with user, clear pending
    call.sessions.set(UserSession(userId = userId.toString(), pendingSearch = null))

    // Redirect to dashboard
    call.respondRedirect(if (pending != null) "/dashboard?saved=true" else "/dashboard")
}

private suspend fun saveSeeker(
        repo: SeekerRepository,
        userId: UUID,
        whatISearch: String,
        aboutMe: String
) {
    repo.save(
            Seeker(
                    type = OfferType.JOB,
                    email = null,
                    profile = "What I search: $whatISearch\n\nAbout me: $aboutMe"
            )
    )
}

@Serializable
data class GoogleUserInfo(val id: String, val email: String, val name: String? = null)

private suspend fun getGoogleUserInfo(client: HttpClient, token: String): GoogleUserInfo? {
    return try {
        client
                .get("https://www.googleapis.com/oauth2/v2/userinfo") {
                    headers { append(HttpHeaders.Authorization, "Bearer $token") }
                }
                .body<GoogleUserInfo>()
    } catch (e: Exception) {
        null
    }
}
