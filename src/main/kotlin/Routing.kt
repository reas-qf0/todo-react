package com.reas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receiveText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Application.configureRouting() {
    val authorizer: Authorizer by dependencies
    suspend fun RoutingContext.authorize(
        onError: suspend () -> Unit = { call.respond(HttpStatusCode.Unauthorized, "Unauthorized") },
        onSuccess: suspend (String) -> Unit,
    ) {
        val session = call.sessions.get<UserSession>() ?: return onError()
        val user = authorizer.authorize(session)
        if (user == null) {
            return onError()
        } else {
            return onSuccess(user)
        }
    }

    val userService: ExposedService by dependencies

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

        route("/api") {
            get("/validate") {
                authorize {
                    call.respond(HttpStatusCode.OK)
                }
            }
            get("/tasks") {
                authorize { userId ->
                    call.respond(userService.userTasks(userId))
                }
            }
            post("/task") {
                authorize { userId ->
                    val body = call.receiveText()
                    val task = try {
                        Json.decodeFromString<TaskRequest>(body)
                    } catch (e: SerializationException) {
                        println(e)
                        return@authorize call.respond(HttpStatusCode.BadRequest, "couldn't parse body")
                    }
                    if (task.title == "")
                        return@authorize call.respond(HttpStatusCode.BadRequest, "title can't be empty")
                    userService.create(userId, task)
                    call.respond(HttpStatusCode.OK)
                }
            }
            get("/tasks/{id}") {
                authorize { userId ->
                    val taskId = call.parameters["id"] ?:
                        return@authorize call.respond(HttpStatusCode.BadRequest, "id is required")
                    val task = userService.getTask(taskId) ?:
                        return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                    if (task.userId != userId)
                        return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")
                    call.respond(task)
                }
            }
            patch("/tasks/{id}") {
                authorize { userId ->
                    val taskId = call.parameters["id"] ?:
                        return@authorize call.respond(HttpStatusCode.BadRequest, "id is required")
                    val task = userService.getTask(taskId) ?:
                        return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                    if (task.userId != userId)
                        return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")

                    val body = call.receiveText()
                    val updates = try {
                        Json.decodeFromString<TaskUpdateRequest>(body)
                    } catch (e: SerializationException) {
                        println(e)
                        return@authorize call.respond(HttpStatusCode.BadRequest, "couldn't parse body")
                    }
                    val newTask = task.copy(
                        title = updates.title ?: task.title,
                        description = updates.description ?: task.description,
                        completed = updates.completed ?: task.completed
                    )
                    if (newTask.title == "")
                        return@authorize call.respond(HttpStatusCode.BadRequest, "title can't be empty")
                    userService.update(newTask)
                    call.respond(newTask)
                }
            }
            delete("/tasks/{id}") {
                authorize { userId ->
                    val taskId = call.parameters["id"] ?:
                        return@authorize call.respond(HttpStatusCode.BadRequest, "id is required")
                    val task = userService.getTask(taskId) ?:
                        return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                    if (task.userId != userId)
                        return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")
                    userService.deleteTask(task.id)
                    call.respond(task)
                }
            }
        }

        singlePageApplication {
            react("webApp/dist")
        }
    }
}