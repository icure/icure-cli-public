package com.icure.cli.commands.couchdb

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.icure.cli.api.CliktConfig
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CheckLegacyUsers : CliktCommand("Set parent to undefined when it is blank") {
	private val config by requireObject<CliktConfig>()
	private val group by option("--group", help = "The group id").required()

	override fun run() {
		runBlocking {
			val db = "icure-$group-base"
			if (!config.client.get("${config.server}/$db").status.isSuccess() ) {
				throw IllegalStateException("Cannot find group $group on ${config.server}")
			}
			val missingEntities = mutableSetOf<String>()
			val hcpsById = mutableMapOf<String, HcpStub>()

			suspend fun getHcp(hcpId: String): HcpStub? =
				hcpsById[hcpId] ?:
					if (missingEntities.contains(hcpId)) null
					else config.client.get {
						url {
							takeFrom(config.server)
							appendPathSegments(db, hcpId)
						}
					}.let {
						when {
							it.status.isSuccess() -> it.body<HcpStub>().also { hcp -> hcpsById[hcp.id] = hcp }
							it.status.value == 404 -> {
								missingEntities.add(hcpId)
								null
							}
							else -> throw IllegalStateException("Cannot get hcp $hcpId: ${it.status.value}")
						}
					}


			val usersReport =
				config.client.get("${config.server}/$db/_design/User/_view/all?include_docs=true").body<Row<UserStub>>().rows.map {
					it.doc
				}.filter {
					it.deletionDate == null
						&& it.status?.lowercase() == "active"
				}.associateWith { user ->
					val autoDelegations = user.autoDelegations["all"] ?: emptySet()
					val missingAutoDelegations = autoDelegations.filter {
						getHcp(it) == null
					}.toSet()

					(user.healthcarePartyId?.let { hcpId ->
						val hcp = getHcp(hcpId)
						if (hcp == null) {
							UserReport(
								missingAutoDelegations = missingAutoDelegations,
								hasMissingHcp = hcpId
							)
						} else if (autoDelegations.size > 1 && hcp.parentId != null && autoDelegations.contains(hcp.parentId)) {
							UserReport(
								missingAutoDelegations = missingAutoDelegations,
								hasAutoDelegationsAndParent = true
							)
						} else {
							UserReport(missingAutoDelegations)
						}
					} ?: UserReport(missingAutoDelegations))
				}
			val hcpReport = hcpsById.values.associateWith { hcp ->
				HcpReport(
					hasEmptyPublicKey = hcp.publicKey.isNullOrBlank(),
					hasMissingParent = hcp.parentId != null && getHcp(hcp.parentId) == null,
				)
			}
			usersReport.forEach { (user, report) ->
				if (report.missingAutoDelegations.isNotEmpty()) {
					echo("User ${user.id} has auto-delegations for non-existing hcps: ${report.missingAutoDelegations.joinToString(", ")}")
				}
				if (report.hasMissingHcp != null) {
					echo("User ${user.id} healthcare party (${report.hasMissingHcp}) does not exist or is deleted")
				}
				if (report.hasAutoDelegationsAndParent) {
					echo("User ${user.id} hcp (${user.healthcarePartyId}) has a parent and an auto-delegation for the parent, but also other auto-delegations")
				}
			}
			hcpReport.forEach { (hcp, report) ->
				if (report.hasEmptyPublicKey) {
					echo("Hcp ${hcp.id} has an empty public key")
				}
				if (report.hasMissingParent) {
					echo("Hcp ${hcp.id} parent (${hcp.parentId}) does not exist or is deleted")
				}
			}
		}
	}

	data class HcpReport(
		val hasEmptyPublicKey: Boolean,
		val hasMissingParent: Boolean
	)

	data class UserReport(
		val missingAutoDelegations: Set<String>,
		val hasMissingHcp: String? = null,
		val hasAutoDelegationsAndParent: Boolean = false
	)

	@Serializable
	data class Row<T>(
		val rows: List<Doc<T>>,
	)

	@Serializable
	data class Doc<T> (
		val doc: T,
	)

	@Serializable
	data class UserStub(
		@SerialName("_id") val id: String,
		@SerialName("deleted") val deletionDate: Long? = null,
		val status: String? = null,
		val healthcarePartyId: String? = null,
		val autoDelegations: Map<String, Set<String>> = emptyMap()
	)

	@Serializable
	data class HcpStub(
		@SerialName("_id") val id: String,
		@SerialName("deleted") val deletionDate: Long? = null,
		val parentId: String? = null,
		val publicKey: String? = null,
	)
}
