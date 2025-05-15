package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.options.SdkOptions
import com.icure.cardinal.sdk.storage.impl.FileStorageFacade
import com.icure.cli.api.CliktConfig
import com.icure.lib.fixParents
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json


class FixParents : CliktCommand("Set parent to undefined when it is blank") {
    private val config by requireObject<CliktConfig>()
    private val regexFilter by option("--regex", help = "Filter group ids by regex")
    private val local by option("--local", help = "Inject in user's database instead of in all subGroups").flag("-l")

    override fun run() {
        runBlocking {
            val api = CardinalBaseSdk.initialize(
                applicationId = null,
                baseUrl = config.server,
                authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)),
                options = BasicSdkOptions(lenientJson = true)
            )

            fixParents(api, local, regexFilter) { echo(it) }
        }
    }
}
