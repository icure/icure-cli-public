package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.options.SdkOptions
import com.icure.cardinal.sdk.storage.impl.FileStorageFacade
import com.icure.cli.api.CliktConfig
import com.icure.lib.patch
import com.icure.lib.valorisationPatch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json


class ValorisationPatch : CliktCommand("Patch the valorisation of a tarification") {
    private val config by requireObject<CliktConfig>()
    private val regexFilter by option("--regex", help = "Filter group ids by regex")
    private val local by option("--local", help = "Inject in user's database instead of in all subGroups").flag("-l")
    private val ids by option("--ids", help = "The ids of the entities to patch separated by comma").split(",")
        .default(emptyList())
    private val ref by option("--ref", help = "The ref of the valorisation to patch").required()
    private val predicate by option("--predicate", help = "The new predicate").required()

    override fun run() {
        runBlocking {
            val api = CardinalBaseSdk.initialize(
                applicationId = null,
                baseUrl = config.server,
                authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)),
                options = BasicSdkOptions(httpClient = config.client, httpClientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true })
            )

            valorisationPatch(api, ids, ref, predicate, local, regexFilter) { echo(it) }
        }
    }
}


