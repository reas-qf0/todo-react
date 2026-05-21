package com.reas

import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

object TestAuthorizer : Authorizer {
    override fun authorize(session: UserSession): String {
        return session.accessToken
    }
}

fun Application.configureSecurityTest() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }

    authentication {
        bearer("auth-oauth-google") {
            realm = "test"
            authenticate { tokenCredential ->
                OAuthAccessTokenResponse.OAuth2(
                    "", "", (Clock.System.now() + 18.days).toEpochMilliseconds(), "",
                    Parameters.build {
                        set("id_token", tokenCredential.token)
                    }
                )
            }
        }
    }
    dependencies {
        provide<Authorizer> { TestAuthorizer }
    }
}