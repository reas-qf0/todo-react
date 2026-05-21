package com.reas

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.sessions.*

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }

    authentication {
        oauth("auth-oauth-google") {
            urlProvider = {
                "https://${request.host()}:${request.port()}/loginCallback"
            }
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

    dependencies {
        provide<Authorizer> { GoogleAuthorizer }
    }
}