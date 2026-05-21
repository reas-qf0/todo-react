package com.reas

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import kotlinx.io.IOException
import java.security.GeneralSecurityException

interface Authorizer {
    fun authorize(session: UserSession): String?
}

object GoogleAuthorizer : Authorizer {
    override fun authorize(session: UserSession): String? {
        val token = session.accessToken

        val verifier = GoogleIdTokenVerifier.Builder(
            NetHttpTransport.Builder().build(),
            GsonFactory.getDefaultInstance()
        ).setIssuer("accounts.google.com").build()

        val idToken = try {
            verifier.verify(token) ?: return null
        } catch (_: GeneralSecurityException) {
            return null
        } catch (_: IOException) {
            return null
        }
        return idToken.payload.subject
    }
}