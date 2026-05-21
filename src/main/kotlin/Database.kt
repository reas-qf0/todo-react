package com.reas

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

suspend fun Application.configureDatabase() {
    val database = R2dbcDatabase.connect(
        url = "r2dbc:h2:file:///./h2",
        user = "root",
        password = "",
    )
    val userService = ExposedService(database).also {
        it.createSchema()
    }

    dependencies {
        provide<ExposedService> { userService }
    }
}
