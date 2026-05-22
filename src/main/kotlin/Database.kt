package com.reas

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

suspend fun Application.configureDatabase() {
    val database = R2dbcDatabase.connect(
        driver = "postgresql",
        url = "r2dbc:postgresql://" + System.getenv("POSTGRES_URL"),
        user = System.getenv("POSTGRES_USER"),
        password = System.getenv("POSTGRES_PASSWORD")
    )
    val userService = ExposedService(database).also {
        it.createSchema()
    }

    dependencies {
        provide<ExposedService> { userService }
    }
}
