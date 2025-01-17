package com.icure.cli.commands.couchdb

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.icure.cli.api.CliktConfig
import com.icure.cli.api.getClient

class CouchDb : CliktCommand() {
	private val credentials by option("-u", "--credentials", help = "Credentials").default("icure:icure")
	private val server by argument(help = "Couchdb server URL").default("http://localhost:5984")

	private val config by findOrSetObject { CliktConfig() }

	override fun run() {
		config.client = getClient(credentials)
		config.server = server
	}

	init {
		this.subcommands(
			CheckLegacyUsers()
		)
	}
}

