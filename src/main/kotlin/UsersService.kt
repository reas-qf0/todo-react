package com.reas

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Task(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val added: String,
    val completed: Boolean
)

@Serializable
data class TaskRequest(
    val title: String,
    val description: String
)

@Serializable
data class TaskUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val completed: Boolean? = null
)

@OptIn(ExperimentalUuidApi::class)
class ExposedService(val database: R2dbcDatabase) {
    object Tasks : UuidTable() {
        val userId = text("user_id")
        val title = text("title")
        val description = text("description")
        val addedTime = long("added_time")
        val isCompleted = bool("is_completed")
    }

    suspend fun createSchema() {
        suspendTransaction(database) {
            SchemaUtils.create(Tasks)
        }
    }

    suspend fun create(userId_: String, task: TaskRequest): String = suspendTransaction(database) {
        val newRecord = Tasks.insert {
            it[userId] = userId_
            it[title] = task.title
            it[description] = task.description
            it[addedTime] = Clock.System.now().toEpochMilliseconds()
            it[isCompleted] = false
        }
        newRecord[Tasks.id].value.toString()
    }

    suspend fun update(task: Task) = suspendTransaction(database) {
        Tasks.update({ Tasks.id eq Uuid.parse(task.id) }) {
            it[title] = task.title
            it[description] = task.description
            it[isCompleted] = task.completed
        }
    }

    suspend fun userTasks(userId: String): List<Task> = suspendTransaction(database) {
        Tasks.selectAll().where(Tasks.userId eq userId)
            .orderBy(Tasks.isCompleted to SortOrder.ASC, Tasks.addedTime to SortOrder.DESC)
            .map { row -> Task(
                id = row[Tasks.id].toString(),
                userId = row[Tasks.userId],
                title = row[Tasks.title],
                description = row[Tasks.description],
                added = Instant.fromEpochMilliseconds(row[Tasks.addedTime]).format(formatter),
                completed = row[Tasks.isCompleted]
            ) }
            .toList()
    }

    suspend fun getTask(id: String): Task? = suspendTransaction(database) {
        val uuid = try {
            Uuid.parse(id)
        } catch (_: IllegalArgumentException) {
            return@suspendTransaction null
        }
        Tasks.selectAll().where(Tasks.id eq uuid).firstOrNull()?.toTask()
    }

    suspend fun deleteTask(id: String) = suspendTransaction(database) {
        Tasks.deleteWhere { Tasks.id eq Uuid.parse(id) }
    }

    private fun ResultRow.toTask() = Task(
        id = this[Tasks.id].toString(),
        userId = this[Tasks.userId],
        title = this[Tasks.title],
        description = this[Tasks.description],
        added = Instant.fromEpochMilliseconds(this[Tasks.addedTime]).format(formatter),
        completed = this[Tasks.isCompleted]
    )

    companion object {
        val formatter = DateTimeComponents.Format {
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars(" ")
            day()
            chars(", ")
            year()
        }
    }
}
