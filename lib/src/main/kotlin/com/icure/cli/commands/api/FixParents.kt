package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.icure.cli.api.CliktConfig
import com.icure.cli.format.xml.beThesaurusHandler
import com.icure.cli.format.xml.beThesaurusProcHandler
import com.icure.cli.format.xml.defaultHandler
import com.icure.cli.format.xml.iso6391Handler
import com.icure.lib.deployCodes
import com.icure.lib.fixParents
import com.icure.sdk.IcureBaseSdk
import com.icure.sdk.IcureSdk
import com.icure.sdk.auth.UsernamePassword
import com.icure.sdk.model.Code
import com.icure.sdk.options.AuthenticationMethod
import com.icure.sdk.options.BasicApiOptions
import com.icure.sdk.utils.Serialization
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.util.LinkedList


class FixParents : CliktCommand("Set parent to undefined when it is blank") {
    private val config by requireObject<CliktConfig>()
    private val regexFilter by option("--regex", help = "Filter group ids by regex")
    private val local by option("--local", help = "Inject in user's database instead of in all subGroups").flag("-l")

    override fun run() {
        runBlocking {
            val api = IcureBaseSdk.initialise(
                config.server,
                AuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)),
                options = BasicApiOptions(httpClient = config.client, httpClientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true })
            )
            fixParents(api, local, regexFilter) { echo(it) }
        }
    }
}
