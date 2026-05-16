package com.reas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.server.http.content.*
import java.io.File

fun Application.configureRouting() {
    routing {
        authenticate("auth-oauth-google", optional = true) {
            get("login") {
                val redirectUrl = call.request.queryParameters["redirect"] ?: "/dashboard"
                call.response.cookies.append("redirect", redirectUrl, path="/loginCallback")
                call.respondRedirect("/loginCallback")
            }
        }

        authenticate("auth-oauth-google", optional = false) {
            get("/loginCallback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                val idToken = principal?.extraParameters?.get("id_token").toString()
                call.sessions.set(UserSession(idToken))

                val redirectUrl = call.request.cookies["redirect"] ?: "/dashboard"
                call.respondRedirect(redirectUrl)
            }
        }

        get("/api/validate") {
            authorize(call) {
                call.respond(HttpStatusCode.OK)
            }
        }

        staticResources("/", "static") {
            fallback { _, call ->
                call.respondFile(File("src/main/resources/static/index.html"))
            }
        }
    }
}