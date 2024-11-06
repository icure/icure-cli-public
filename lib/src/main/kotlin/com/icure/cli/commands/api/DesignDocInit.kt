package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.default
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.options.SdkOptions
import com.icure.cardinal.sdk.storage.impl.FileStorageFacade
import com.icure.cli.api.CliktConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import kotlinx.serialization.json.Json
import java.util.concurrent.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class DesignDocInit: CliktCommand() {
    private val config by requireObject<CliktConfig>()
    private val entity: String by argument(help = "The entity to create for which the design document will be created (Code, Patient, …)")
    private val warmup: Boolean by argument(help = "Force immediate warmup of the design document (true/false)").convert { it.toBoolean() }.default(false)
    private val cooldown: Duration by argument(help = "Delay between two initialisations in seconds").convert { it.toInt().seconds }.default(20.seconds)

    override fun run() {
        runBlocking {
            val api = CardinalBaseSdk.initialize(
                applicationId = null,
                baseUrl = config.server,
                authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)),
                options = BasicSdkOptions(httpClient = config.client, httpClientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true })
            )


            val groupApi = api.group
            val groups = groupApi.listGroups()

            groups.forEach {
                try {
                    groupApi.initDesignDocs(it.id, entity, warmup, false)
                    echo("Init done for $entity in ${it.id}")
                    cooldown.let { delay -> if (delay > Duration.ZERO) delay(delay.toJavaDuration()) }
                } catch (e: CancellationException) {
                    //skip
                } catch (e: Exception) {
                    echo("Error ${e.javaClass.canonicalName} importing into group ${it.id}: ${e.message}")
                }
            }
        }
    }
}
