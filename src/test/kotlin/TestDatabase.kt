package com.reas

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

suspend fun Application.configureDatabaseTest() {
    val database = R2dbcDatabase.connect(
        url = "r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;",
    )
    val userService = ExposedService(database).also {
        it.createSchema(dropTables = true)
    }

    dependencies {
        provide<ExposedService> { userService }
    }
}