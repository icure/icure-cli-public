package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.model.Code
import com.icure.cardinal.sdk.model.Insurance
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.utils.Serialization
import com.icure.cli.api.CliktConfig
import com.icure.cli.format.xml.beThesaurusHandler
import com.icure.cli.format.xml.beThesaurusProcHandler
import com.icure.cli.format.xml.defaultHandler
import com.icure.cli.format.xml.iso6391Handler
import com.icure.lib.deployCodes
import com.icure.lib.deployInsurances
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.apache.xerces.jaxp.SAXParserFactoryImpl
import java.io.File
import java.util.LinkedList


class DeployCodes : CliktCommand("Deploy codes on all sub-groups available to the user") {
    private val config by requireObject<CliktConfig>()
    private val regexFilter by option("--regex", help = "Filter group ids by regex")
    private val local by option("--local", help = "Inject in user's database instead of in all subGroups").flag("-l")

    private val path by argument(
        help = "The path of the codes file to import. The codes file should be a JSON file with an array of objects with the following fields: id, code, type, version, regions, labels. XML files of diverse formats are supported if the --xml flag is present"
    ).optional()

    override fun run() {
        runBlocking {
            val api = CardinalBaseSdk.initialize(
                applicationId = null,
                baseUrl = config.server,
                authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)),
                options = BasicSdkOptions(lenientJson = true),
            )

            val insurances = Serialization.json.decodeFromString<List<Insurance>>(path?.let { File(it).readText() }
                ?: System.`in`.bufferedReader().readText())

            deployInsurances(api, insurances, local, regexFilter) { echo(it) }
        }
    }
}

enum class XmlFormat {
    default, iso, thesaurus, thesaurusProc
}
