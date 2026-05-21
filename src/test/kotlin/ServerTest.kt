package com.reas

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {
    private fun testEnvironment(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        configure()
        configure("test-overrides.yaml")

        client = createClient {
            install(HttpCookies)
            install(ContentNegotiation) {
                json()
            }
        }

        block()
    }

    private suspend fun ApplicationTestBuilder.authorize(userId: String) {
        assertEquals(HttpStatusCode.OK, client.get("/loginCallback") {
            header("Authorization", "Bearer $userId")
        }.status)
    }

    @Test
    fun `test root endpoint`() = testEnvironment {
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun `test HTTPS endpoint`() = testEnvironment {
        val response = client.get("/") {
            url {
                protocol = URLProtocol.HTTPS
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `test validate unauthorized`() = testEnvironment {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/validate").status)
    }

    @Test
    fun `test validate authorized`() = testEnvironment {
        authorize("user123")
        assertEquals(HttpStatusCode.OK, client.get("/api/validate").status)
    }

    @Test
    fun `test get tasks`() = testEnvironment {
        authorize("user123")
        assertEquals(HttpStatusCode.OK, client.get("/api/tasks").status)
    }

    @Test
    fun `test add task`() = testEnvironment {
        authorize("user123")
        val task = TaskRequest(
            title = "test task",
            description = "test task",
        )
        val request = client.post("/api/task") {
            contentType(ContentType.Application.Json)
            setBody(task)
        }
        assertEquals(HttpStatusCode.OK, request.status)

        val request2 = client.get("/api/tasks")
        assertEquals(HttpStatusCode.OK, request2.status)
        val response = request2.body<List<Task>>()
        assertEquals(1, response.size)
        assertEquals(task.title, response.first().title)
        assertEquals(task.description, response.first().description)
    }

    @Test
    fun `test user isolation`() = testEnvironment {
        authorize("user123")
        val task = TaskRequest(
            title = "test task",
            description = "test task",
        )
        val request = client.post("/api/task") {
            contentType(ContentType.Application.Json)
            setBody(task)
        }
        assertEquals(HttpStatusCode.OK, request.status)

        authorize("user456")
        val request2 = client.get("/api/tasks")
        assertEquals(HttpStatusCode.OK, request2.status)
        val response = request2.body<List<Task>>()
        assertEquals(0, response.size)
    }
}
