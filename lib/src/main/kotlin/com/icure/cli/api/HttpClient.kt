package com.icure.cli.api

import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.serialization.json.Json

fun getClient(credentials: String) = HttpClient(CIO) {
    defaultRequest {
        headers["Authorization"] =
            "Basic ${credentials.toByteArray(Charsets.UTF_8).encodeBase64()}"
    }

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; ; coerceInputValues = true })
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 60000
    }
}
