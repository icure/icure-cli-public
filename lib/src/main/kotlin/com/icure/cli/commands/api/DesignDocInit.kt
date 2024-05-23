package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.default
import com.icure.cli.api.CliktConfig
import com.icure.sdk.IcureBaseSdk
import com.icure.sdk.api.BasicAuthenticationMethod
import com.icure.sdk.auth.UsernamePassword
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
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
            val api = IcureBaseSdk.initialise(config.server, BasicAuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)))

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
