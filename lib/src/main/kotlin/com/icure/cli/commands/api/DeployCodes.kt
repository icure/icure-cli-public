package com.icure.cli.commands.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.icure.cli.api.CliktConfig
import io.icure.kraken.client.apis.CodeApi
import io.icure.kraken.client.apis.GroupApi
import io.icure.kraken.client.infrastructure.ApiClient.Companion.objectMapper
import io.icure.kraken.client.models.LoginCredentials
import io.icure.kraken.client.security.ExternalJWTProvider
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.taktik.icure.services.external.rest.v2.dto.CodeDto
import org.taktik.icure.services.external.rest.v2.dto.ListOfIdsDto

class DeployCodes: CliktCommand() {
    private val config by requireObject<CliktConfig>()
    private val path by argument(
        help = "The path of the codes file to import. The codes file should be a JSON file with an array of objects with the following fields: id, code, type, version, regions, labels."
    ).optional()

    @OptIn(ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class)
    override fun run() {
        runBlocking {
            val token = config.client.post("${config.server}/rest/v2/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(objectMapper.writeValueAsString(LoginCredentials(username = config.username, password = config.password)))
            }.let { objectMapper.readValue<JwtResponse>(it.bodyAsText()) }.token

            val codes = objectMapper.readValue<List<CodeDto>>(path?.let { java.io.File(it).readText() } ?: System.`in`.bufferedReader().readText())

            val groupApi = GroupApi(basePath = config.server, authProvider = ExternalJWTProvider(token))
            val codeApi = CodeApi(basePath = config.server, authProvider = ExternalJWTProvider(token))
            val groups = groupApi.listGroups()
            groups.forEach {
                try {
                    echo("Importing into group ${it.id}")
                    val existingCodes =
                        codeApi.getCodes(ListOfIdsDto(ids = codes.map { c -> c.id })).associateBy { c -> c.id }
                    val codesToAdd = codes.filter { code -> !existingCodes.containsKey(code.id) }
                    val codesToUpdate = codes.filter { code -> existingCodes.containsKey(code.id) }
                        .map { code -> code.copy(rev = existingCodes[code.id]?.rev) }

                    codesToAdd.chunked(100).forEach { chunk ->
                        codeApi.createCodes(chunk)
                    }
                    codesToUpdate.chunked(100).forEach { chunk ->
                        codeApi.modifyCodes(chunk)
                    }
                    echo("Created ${codesToAdd.size} and updated ${codesToUpdate.size} codes into group ${it.id}")
                } catch (e: Exception) {
                    echo("Error importing into group ${it.id}: ${e.message}")
                }
            }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class JwtResponse(
    val token: String
)
