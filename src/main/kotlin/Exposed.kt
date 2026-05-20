package com.reas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase


suspend fun Application.configureExposed() {
    val database = R2dbcDatabase.connect(
        url = "r2dbc:h2:file:///./h2",
        user = "root",
        password = "",
    )
    val userService = ExposedService(database).also {
        it.createSchema()
    }

    routing {
        get("/api/tasks") {
            authorize(call) { userId ->
                call.respond(userService.userTasks(userId))
            }
        }
        post("/api/task") {
            authorize(call) { userId ->
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
        get("/api/tasks/{id}") {
            authorize(call) { userId ->
                val taskId = call.parameters["id"] ?:
                    return@authorize call.respond(HttpStatusCode.BadRequest, "id is required")
                val task = userService.getTask(taskId) ?:
                    return@authorize call.respond(HttpStatusCode.NotFound, "task not found")
                if (task.userId != userId)
                    return@authorize call.respond(HttpStatusCode.Forbidden, "You don't have access to this task")
                call.respond(task)
            }
        }
        patch("/api/tasks/{id}") {
            authorize(call) { userId ->
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
        delete("/api/tasks/{id}") {
            authorize(call) { userId ->
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
}
