package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.model.HealthcareParty
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.utils.Serialization
import com.icure.cli.api.CliktConfig
import com.icure.lib.createHealthcareParty
import com.icure.lib.getHealthcareParty
import com.icure.lib.listHealthcareParties
import com.icure.lib.modifyHealthcareParty
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

private suspend fun CliktConfig.sdk() = CardinalBaseSdk.initialize(
    projectId = null,
    baseUrl = server,
    authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(username, password)),
    options = BasicSdkOptions(lenientJson = true),
)

class Hcp : CliktCommand(name = "hcp", help = "Manage healthcare parties in a group") {
    private val config by requireObject<CliktConfig>()
    private val group by option("-g", "--group", help = "The id of the group to operate on").required()

    override fun run() {
        config.group = group
    }

    init {
        this.subcommands(HcpList(), HcpGet(), HcpCreate(), HcpModify())
    }
}

class HcpList : CliktCommand(name = "list", help = "List healthcare parties in the group (summary lines)") {
    private val config by requireObject<CliktConfig>()
    private val filter by option("--filter", help = "Filter healthcare parties by name")
    private val limit by option("--limit", help = "Maximum number of healthcare parties to return").int().default(100)

    override fun run() {
        runBlocking {
            listHealthcareParties(config.sdk(), config.group, filter, limit) { echo(it) }
        }
    }
}

class HcpGet : CliktCommand(name = "get", help = "Get a healthcare party by id (full JSON)") {
    private val config by requireObject<CliktConfig>()
    private val hcpId by argument(help = "The id of the healthcare party to fetch")

    override fun run() {
        runBlocking {
            getHealthcareParty(config.sdk(), config.group, hcpId) { echo(it) }
        }
    }
}

class HcpCreate : CliktCommand(
    name = "create",
    help = "Create a healthcare party, either from convenience flags or from a full HealthcareParty JSON (file path or stdin)",
) {
    private val config by requireObject<CliktConfig>()
    private val firstName by option("--first-name", help = "First name")
    private val lastName by option("--last-name", help = "Last name")
    private val name by option("--name", help = "Name (e.g. for organisations)")
    private val speciality by option("--speciality", help = "Speciality")
    private val ssin by option("--ssin", help = "Social security identification number")
    private val civility by option("--civility", help = "Civility")
    private val path by argument(help = "Path to a HealthcareParty JSON file (read from stdin when omitted and no flags are given)").optional()

    override fun run() {
        runBlocking {
            val hasFlags = listOf(firstName, lastName, name, speciality, ssin, civility).any { it != null }

            val hcp = if (hasFlags) {
                HealthcareParty(
                    id = UUID.randomUUID().toString(),
                    firstName = firstName,
                    lastName = lastName,
                    name = name,
                    speciality = speciality,
                    ssin = ssin,
                    civility = civility,
                )
            } else {
                val json = path?.let { File(it).readText() } ?: System.`in`.bufferedReader().readText()
                Serialization.json.decodeFromString<HealthcareParty>(json)
            }

            createHealthcareParty(config.sdk(), config.group, hcp) { echo(it) }
        }
    }
}

class HcpModify : CliktCommand(
    name = "modify",
    help = "Modify a healthcare party. With an id, fetches the healthcare party and applies the given flags; without an id, reads a full HealthcareParty JSON from stdin (full replace)",
) {
    private val config by requireObject<CliktConfig>()
    private val firstName by option("--first-name", help = "First name")
    private val lastName by option("--last-name", help = "Last name")
    private val name by option("--name", help = "Name (e.g. for organisations)")
    private val speciality by option("--speciality", help = "Speciality")
    private val ssin by option("--ssin", help = "Social security identification number")
    private val civility by option("--civility", help = "Civility")
    private val hcpId by argument(help = "The id of the healthcare party to modify (flag mode); omit to pipe a full HealthcareParty JSON").optional()

    override fun run() {
        runBlocking {
            val hasFlags = listOf(firstName, lastName, name, speciality, ssin, civility).any { it != null }
            val api = config.sdk()

            val hcp = if (hcpId != null) {
                val current = api.healthcareParty.inGroup.getHealthcareParty(config.group, hcpId!!)?.entity
                if (current == null) {
                    echo("Healthcare party $hcpId not found in group ${config.group}")
                    return@runBlocking
                }
                current.copy(
                    firstName = firstName ?: current.firstName,
                    lastName = lastName ?: current.lastName,
                    name = name ?: current.name,
                    speciality = speciality ?: current.speciality,
                    ssin = ssin ?: current.ssin,
                    civility = civility ?: current.civility,
                )
            } else {
                if (hasFlags) {
                    echo("Provide a healthcare party id to modify with flags, or omit flags to pipe a full HealthcareParty JSON.")
                    return@runBlocking
                }
                val json = System.`in`.bufferedReader().readText()
                Serialization.json.decodeFromString<HealthcareParty>(json)
            }

            modifyHealthcareParty(api, config.group, hcp) { echo(it) }
        }
    }
}
