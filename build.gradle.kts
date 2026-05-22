plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dotenv)
}

group = "com.reas"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.client.apache)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.sessions)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.di)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.h2database.h2)
    implementation(libs.h2database.r2dbc)
    implementation(libs.postgresql.r2dbc)
    implementation(libs.logback.classic)
    implementation(libs.google.api.client)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
}

envLoader {
    envFiles(".env")
    taskNames("run")
    overrideSystemEnvironment.set(false)
}