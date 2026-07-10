@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.int
import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.auth.UsernamePassword
import com.icure.cardinal.sdk.model.User
import com.icure.cardinal.sdk.model.enums.UsersStatus
import com.icure.cardinal.sdk.options.AuthenticationMethod
import com.icure.cardinal.sdk.options.BasicSdkOptions
import com.icure.cardinal.sdk.utils.Serialization
import com.icure.cli.api.CliktConfig
import com.icure.lib.createUser
import com.icure.lib.getUser
import com.icure.lib.listUsers
import com.icure.lib.modifyUser
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

private suspend fun CliktConfig.sdk() = CardinalBaseSdk.initialize(
    projectId = null,
    baseUrl = server,
    authenticationMethod = AuthenticationMethod.UsingCredentials(UsernamePassword(username, password)),
    options = BasicSdkOptions(ignoreUnknownFields = true),
)

class User : CliktCommand(help = "Manage users in a group") {
    private val config by requireObject<CliktConfig>()
    private val group by option("-g", "--group", help = "The id of the group to operate on").required()

    override fun run() {
        config.group = group
    }

    init {
        this.subcommands(UserList(), UserGet(), UserCreate(), UserModify())
    }
}

class UserList : CliktCommand(name = "list", help = "List users in the group (summary lines)") {
    private val config by requireObject<CliktConfig>()
    private val filter by option("--filter", help = "Filter users by name, email or phone")
    private val limit by option("--limit", help = "Maximum number of users to return").int().default(100)

    override fun run() {
        runBlocking {
            listUsers(config.sdk(), config.group, filter, limit) { echo(it) }
        }
    }
}

class UserGet : CliktCommand(name = "get", help = "Get a user by id (full JSON)") {
    private val config by requireObject<CliktConfig>()
    private val userId by argument(help = "The id of the user to fetch")

    override fun run() {
        runBlocking {
            getUser(config.sdk(), config.group, userId) { echo(it) }
        }
    }
}

class UserCreate : CliktCommand(
    name = "create",
    help = "Create a user, either from convenience flags or from a full User JSON (file path or stdin)",
) {
    private val config by requireObject<CliktConfig>()
    private val login by option("--login", help = "Login (we encourage using an email address)")
    private val email by option("--email", help = "Email address")
    private val name by option("--name", help = "Last name of the user")
    private val mobilePhone by option("--mobile-phone", help = "Mobile phone")
    private val status by option("--status", help = "User status: Active, Disabled or Registering").convert { UsersStatus.valueOf(it) }
    private val roles by option("--roles", help = "Comma-separated list of roles").split(",").default(emptyList())
    private val hcpId by option("--hcp-id", help = "Linked healthcare party id")
    private val patientId by option("--patient-id", help = "Linked patient id")
    private val password by option("--password", help = "Initial password (set via a follow-up call after creation)")
    private val path by argument(help = "Path to a User JSON file (read from stdin when omitted and no flags are given)").optional()

    override fun run() {
        runBlocking {
            val hasFlags = listOf(login, email, name, mobilePhone, hcpId, patientId).any { it != null } ||
                status != null || roles.isNotEmpty()

            val user = if (hasFlags) {
                User(
                    id = UUID.randomUUID().toString(),
                    login = login,
                    email = email,
                    name = name,
                    mobilePhone = mobilePhone,
                    status = status ?: UsersStatus.Active,
                    roles = roles.toSet(),
                    healthcarePartyId = hcpId,
                    patientId = patientId,
                )
            } else {
                val json = path?.let { File(it).readText() } ?: System.`in`.bufferedReader().readText()
                Serialization.json.decodeFromString<User>(json)
            }

            createUser(config.sdk(), config.group, user, password) { echo(it) }
        }
    }
}

class UserModify : CliktCommand(
    name = "modify",
    help = "Modify a user. With a user id, fetches the user and applies the given flags; without an id, reads a full User JSON from stdin (full replace)",
) {
    private val config by requireObject<CliktConfig>()
    private val login by option("--login", help = "Login (we encourage using an email address)")
    private val email by option("--email", help = "Email address")
    private val name by option("--name", help = "Last name of the user")
    private val mobilePhone by option("--mobile-phone", help = "Mobile phone")
    private val status by option("--status", help = "User status: Active, Disabled or Registering").convert { UsersStatus.valueOf(it) }
    private val roles by option("--roles", help = "Comma-separated list of roles (replaces existing roles)").split(",").default(emptyList())
    private val hcpId by option("--hcp-id", help = "Linked healthcare party id")
    private val patientId by option("--patient-id", help = "Linked patient id")
    private val userId by argument(help = "The id of the user to modify (flag mode); omit to pipe a full User JSON").optional()

    override fun run() {
        runBlocking {
            val hasFlags = listOf(login, email, name, mobilePhone, hcpId, patientId).any { it != null } ||
                status != null || roles.isNotEmpty()
            val api = config.sdk()

            val user = if (userId != null) {
                val current = api.user.inGroup.getUser(config.group, userId!!)?.entity
                if (current == null) {
                    echo("User $userId not found in group ${config.group}")
                    return@runBlocking
                }
                current.copy(
                    login = login ?: current.login,
                    email = email ?: current.email,
                    name = name ?: current.name,
                    mobilePhone = mobilePhone ?: current.mobilePhone,
                    status = status ?: current.status,
                    roles = if (roles.isNotEmpty()) roles.toSet() else current.roles,
                    healthcarePartyId = hcpId ?: current.healthcarePartyId,
                    patientId = patientId ?: current.patientId,
                )
            } else {
                if (hasFlags) {
                    echo("Provide a user id to modify with flags, or omit flags to pipe a full User JSON.")
                    return@runBlocking
                }
                val json = System.`in`.bufferedReader().readText()
                Serialization.json.decodeFromString<User>(json)
            }

            modifyUser(api, config.group, user) { echo(it) }
        }
    }
}
