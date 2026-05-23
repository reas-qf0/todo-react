package com.reas

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
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
    val client = HttpClient()

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

            get("/tasks/{id}/attachments") {
                authorize { userId ->
                    val taskId = call.parameters["id"] ?:
                        return@authorize call.respond(HttpStatusCode.BadRequest, "id is required")
                    val task = userService.getTask(taskId) ?:
                        return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                    if (task.userId != userId)
                        return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")

                    val attachments = userService.getAttachments(taskId)!!
                    call.respond(attachments)
                }
            }
            post("/tasks/{id}/attachments") {
                authorize { userId ->
                    val taskId = call.parameters["id"] ?:
                        return@authorize call.respond(HttpStatusCode.BadRequest, "id is required")
                    val task = userService.getTask(taskId) ?:
                        return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                    if (task.userId != userId)
                        return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")

                    call.receiveMultipart().forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val uuid = Uuid.random()
                            val name = uuid.toString()
                            val tmpFile = File("/tmp/$name")
                            part.provider().copyAndClose(tmpFile.writeChannel())

                            val response = client.put("https://storage.yandexcloud.net/uploads-bucket/$name") {
                                header("Authorization", "Bearer " + System.getenv("YC_IAM_TOKEN"))
                                contentType(part.contentType!!)
                                setBody(tmpFile.readBytes())
                            }
                            if (!response.status.isSuccess()) {
                                println(response.status)
                                println(response.bodyAsText())
                                call.respond(HttpStatusCode.InternalServerError, "Yandex Cloud error")
                            } else {
                                userService.addAttachment(
                                    id = uuid,
                                    filename = part.originalFileName!!,
                                    contentType = part.contentType!!.toString(),
                                    taskId = task.id
                                )
                                tmpFile.delete()
                            }
                        }
                        part.dispose()
                    }
                    call.respond(userService.getAttachments(task.id)!!)
                }
            }
        }
        get("/attachments/{attachId}") {
            authorize { userId ->
                val attachId = call.parameters["attachId"] ?:
                    return@authorize call.respond(HttpStatusCode.BadRequest, "attachment id is required")
                val attachment = userService.getAttachment(attachId) ?:
                    return@authorize call.respond(HttpStatusCode.NotFound, "attachment not found")
                val task = userService.getTask(attachment.taskId) ?:
                    return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                if (task.userId != userId)
                    return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")

                val response = client.get("https://storage.yandexcloud.net/uploads-bucket/${attachId}") {
                    header("Authorization", "Bearer " + System.getenv("YC_IAM_TOKEN"))
                }
                if (!response.status.isSuccess()) {
                    println(response.status)
                    println(response.bodyAsText())
                    call.respond(HttpStatusCode.InternalServerError, "Yandex Cloud error")
                }
                call.respond(response.bodyAsBytes())
            }
        }
        delete("/attachments/{attachId}") {
            authorize { userId ->
                val attachId = call.parameters["attachId"] ?:
                    return@authorize call.respond(HttpStatusCode.BadRequest, "attachment id is required")
                val attachment = userService.getAttachment(attachId) ?:
                    return@authorize call.respond(HttpStatusCode.NotFound, "attachment not found")
                val task = userService.getTask(attachment.taskId) ?:
                    return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                if (task.userId != userId)
                    return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")

                val response = client.delete("https://storage.yandexcloud.net/uploads-bucket/${attachId}") {
                    header("Authorization", "Bearer " + System.getenv("YC_IAM_TOKEN"))
                }
                if (!response.status.isSuccess()) {
                    println(response.status)
                    println(response.bodyAsText())
                    call.respond(HttpStatusCode.InternalServerError, "Yandex Cloud error")
                }
                userService.deleteAttachment(attachId)
                call.respond(userService.getAttachments(task.id)!!)
            }
        }

        singlePageApplication {
            react("webApp/dist")
        }
    }
}