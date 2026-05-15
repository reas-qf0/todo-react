package com.reas

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.sessions.*
import kotlinx.io.IOException
import java.security.GeneralSecurityException

suspend fun authorize(
    call: RoutingCall,
    onError: suspend () -> Unit = { call.respond(HttpStatusCode.Unauthorized) },
    onSuccess: suspend (String) -> Unit,
) {
    val session = call.sessions.get<UserSession>() ?: return onError()
    val token = session.accessToken

    val verifier = GoogleIdTokenVerifier.Builder(
        NetHttpTransport.Builder().build(),
        GsonFactory.getDefaultInstance()
    ).setIssuer("accounts.google.com").build()

    try {
        val idToken = verifier.verify(token) ?: return onError()
        onSuccess(idToken.payload.subject)
    } catch (_: GeneralSecurityException) {
        onError()
    } catch (_: IOException) {
        onError()
    }
}

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }

    authentication {
        oauth("auth-oauth-google") {
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GOOGLE_CLIENT_ID"),
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile")
                )
            }
            client = HttpClient(Apache)
        }
    }
}