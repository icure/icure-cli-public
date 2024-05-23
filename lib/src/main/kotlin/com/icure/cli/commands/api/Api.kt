package com.icure.cli.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.icure.cli.api.CliktConfig
import com.icure.cli.api.getClient
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class Api : CliktCommand() {
    private val credentials by option("-u", "--credentials", help = "Credentials").required()
    private val server by option("-s", "--server", help = "Couchdb server URL").default("https://api.icure.cloud")

    private val config by findOrSetObject { CliktConfig() }

    override fun run() {
        config.client = getClient(credentials)
        config.server = server
        config.username = credentials.substringBefore(":")
        config.password = credentials.substringAfter(":")
    }

    init {
        this.subcommands(DeployCodes())
        this.subcommands(DesignDocInit())
    }
}

