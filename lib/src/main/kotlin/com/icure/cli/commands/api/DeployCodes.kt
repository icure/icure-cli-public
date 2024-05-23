package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.icure.cli.api.CliktConfig
import com.icure.sdk.IcureBaseSdk
import com.icure.sdk.api.BasicAuthenticationMethod
import com.icure.sdk.auth.UsernamePassword
import com.icure.sdk.model.Code
import com.icure.sdk.utils.Serialization
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException

class DeployCodes: CliktCommand() {
    private val config by requireObject<CliktConfig>()
    private val path by argument(
        help = "The path of the codes file to import. The codes file should be a JSON file with an array of objects with the following fields: id, code, type, version, regions, labels."
    ).optional()

    override fun run() {
        runBlocking {
            val codes = Serialization.json.decodeFromString<List<Code>>(path?.let { java.io.File(it).readText() } ?: System.`in`.bufferedReader().readText())

            val api = IcureBaseSdk.initialise(config.server, BasicAuthenticationMethod.UsingCredentials(UsernamePassword(config.username, config.password)))

            val groupApi = api.group
            val codeApi = api.code
            val groups = groupApi.listGroups()

            groups.forEach {
                try {
                    echo("Importing into group ${it.id}")
                    val existingCodes =
                        codeApi.getCodes(codes.map { c -> c.id }).associateBy { c -> c.id }
                    val codesToAdd = codes.filter { code -> !existingCodes.containsKey(code.id) }
                    val codesToUpdate = codes.filter { code -> existingCodes.containsKey(code.id) }
                        .map { code -> code.copy(rev = existingCodes[code.id]?.rev) }

                    codesToAdd.chunked(100).forEach { chunk ->
                        codeApi.createCodes(chunk)
                    }
                    codesToUpdate.chunked(100).forEach { chunk ->
                        codeApi.modifyCodes(chunk)
                    }
                    echo("Created ${codesToAdd.size} and updated ${codesToUpdate.size} codes into group ${it.id}")
                } catch (e: CancellationException) {
                    //skip
                } catch (e: Exception) {
                    echo("Error importing into group ${it.id}: ${e.message}")
                }
            }
        }
    }
}
