package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.options.SdkOptions
import com.icure.cardinal.sdk.storage.impl.FileStorageFacade
import com.icure.cli.api.CliktConfig
import com.icure.lib.fixParents
import com.icure.lib.transferGroup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json


class TransferGroup : CliktCommand("Set parent to undefined when it is blank") {
    private val config by requireObject<CliktConfig>()
    private val sourceCredentials by option("-c", "--source-credentials", help = "The credentials to use on the source side").required()
    private val group by option("-g", "--group", help = "The group to move").required()
    private val destination by option("-d", "--destination", help = "The destination parent group").required()

    override fun run() {
        runBlocking {
            val destinationApi = CardinalBaseSdk.initialize(
                applicationId = null,
                baseUrl = config.server,
                authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)),
                options = BasicSdkOptions(lenientJson = true)
            )

            val sourceApi = CardinalBaseSdk.initialize(
                applicationId = null,
                baseUrl = config.server,
                authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(sourceCredentials.split(':').first(), sourceCredentials.split(':').drop(1).joinToString(":"))),
                options = BasicSdkOptions(lenientJson = true)
            )

            transferGroup(destinationApi, sourceApi, group, destination) { echo(it) }
        }
    }
}
