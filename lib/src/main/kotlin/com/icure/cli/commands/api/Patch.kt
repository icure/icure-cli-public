package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.api.raw.RawApiConfig
import com.icure.cardinal.sdk.api.raw.impl.RawTarificationApiImpl
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.auth.services.AuthProvider
import com.icure.cardinal.sdk.auth.services.AuthService
import com.icure.cardinal.sdk.model.embed.AuthenticationClass
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.options.RequestRetryConfiguration
import com.icure.cardinal.sdk.options.SdkOptions
import com.icure.cardinal.sdk.storage.impl.FileStorageFacade
import com.icure.cardinal.sdk.utils.RequestStatusException
import com.icure.cardinal.sdk.utils.Serialization
import com.icure.cli.api.CliktConfig
import com.icure.lib.patch
import com.icure.utils.InternalIcureApi
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json


@OptIn(InternalIcureApi::class)
class Patch : CliktCommand("Patch entities") {
    private val config by requireObject<CliktConfig>()
    private val regexFilter by option("--regex", help = "Filter group ids by regex")
    private val local by option("--local", help = "Inject in user's database instead of in all subGroups").flag("-l")
    private val entity by option(
        "--entity",
        help = "The entity to patch ${Entity.entries.joinToString(", ")}"
    ).convert { Entity.valueOf(it) }.default(Entity.code)
    private val ids by option("--ids", help = "The ids of the entities to patch separated by comma").split(",")
        .default(emptyList())
    private val patchs by argument().multiple(required = true)
    override fun run() {
        runBlocking {
            val api = CardinalBaseSdk.initialize(
                projectId = null,
                baseUrl = config.server,
                authenticationMethod = AuthenticationMethod.UsingCredentials(
                    UsernamePassword(
                        config.username,
                        config.password
                    )
                ),
                options = BasicSdkOptions(lenientJson = true)
            )

            val rawTarificationApi = RawTarificationApiImpl(
                config.server, object : AuthProvider {
                    override fun getAuthService(): AuthService = object : AuthService {
                        override suspend fun setAuthenticationInRequest(
                            builder: HttpRequestBuilder,
                            authenticationClass: AuthenticationClass?,
                        ) {
                            builder.bearerAuth(api.auth.getBearerToken())
                        }

                        override suspend fun invalidateCurrentAuthentication(
                            error: RequestStatusException,
                            requiredAuthClass: AuthenticationClass?,
                        ) {
                            TODO("Not yet implemented")
                        }

                    }

                    override suspend fun switchGroup(newGroupId: String): AuthProvider {
                        TODO("Not yet implemented")
                    }

                    override suspend fun changeScope(dataOwnerId: String): AuthProvider {
                        TODO("Not yet implemented")
                    }
                }, RawApiConfig(
                    httpClient = config.client ?: HttpClient(),
                    additionalHeaders = emptyMap(),
                    json = Serialization.json,
                    requestTimeout = null,
                    retryConfiguration = RequestRetryConfiguration()
                )
            )
            patch(api, rawTarificationApi, entity, ids, patchs, local, regexFilter) { echo(it) }
        }
    }

    enum class Entity {
        code, tarification
    }

}
