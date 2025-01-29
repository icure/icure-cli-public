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

class CheckLegacyUsers : CliktCommand("Checks if in a group there are some users that will not work with the v8 constraints") {
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
			val parentsOfUsersHcps = mutableSetOf<String>()
			val hcpsWithNullParent = mutableSetOf<String>()

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

			suspend fun hasCycleInParentHierarchy(hcp: HcpStub, visited: List<String> = emptyList()): Boolean = when {
				visited.contains(hcp.id) -> true
				hcp.parentId == null -> true
				else -> getHcp(hcp.parentId)?.let { parent ->
					hasCycleInParentHierarchy(parent, visited + hcp.id)
				} ?: false
			}

			val usersReport =
				config.client.get("${config.server}/$db/_design/User/_view/all?include_docs=true").body<Row<UserStub>>().rows.map {
					it.doc
				}.filter {
					it.deletionDate == null
						&& it.status?.lowercase() == "active"
				}.associateWith { user ->
					val autoDelegations = user.autoDelegations.values.flatten().toSet()
					val autoDelegationsToMissingHcp = autoDelegations.filter {
						getHcp(it) == null || getHcp(it)?.deletionDate != null
					}.toSet()

					(user.healthcarePartyId?.let { hcpId ->
						val hcp = getHcp(hcpId)
						hcp?.also {
							if (it.parentId != null) {
								parentsOfUsersHcps.add(it.parentId)
							} else {
								hcpsWithNullParent.add(hcpId)
							}
						}
						if (hcp == null) {
							UserReport(
								autoDelegationsToMissingHcp = autoDelegationsToMissingHcp,
								hasMissingHcp = hcpId
							)
						} else if (autoDelegations.size > 1 && hcp.parentId != null && autoDelegations.contains(hcp.parentId)) {
							UserReport(
								autoDelegationsToMissingHcp = autoDelegationsToMissingHcp,
								hasAutoDelegationsAndParent = true
							)
						} else {
							UserReport(autoDelegationsToMissingHcp)
						}
					} ?: UserReport(autoDelegationsToMissingHcp))
				}
			val hcpReport = hcpsById.values.associateWith { hcp ->
				HcpReport(
					hasEmptyPublicKey = hcp.publicKey.isNullOrBlank(),
					hasMissingParent = hcp.parentId != null && getHcp(hcp.parentId) == null,
					hasCycleInParentHierarchy = hasCycleInParentHierarchy(hcp)
				)
			}
			usersReport.forEach { (user, report) ->
				if (report.autoDelegationsToMissingHcp.isNotEmpty()) {
					echo("User ${user.id} has auto-delegations for non-existing or deleted hcps: ${report.autoDelegationsToMissingHcp.joinToString(", ")}")
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
				if (report.hasMissingParent) {
					echo("Hcp ${hcp.id} has a cycle in his hierarchy of parents")
				}
			}
			if (parentsOfUsersHcps.size > 1) {
				echo("There are multiple hcps that are parents of other hcps in this group: ${parentsOfUsersHcps.joinToString(", ")}")
			}
			if (parentsOfUsersHcps.size == 1 && hcpsWithNullParent.size > 1) {
				echo("There are hcps with a null parent in the group, even if a parent hcp is present in the group ${(hcpsWithNullParent - parentsOfUsersHcps).joinToString(", ")}")
			}
		}
	}

	data class HcpReport(
		val hasEmptyPublicKey: Boolean,
		val hasMissingParent: Boolean,
		val hasCycleInParentHierarchy: Boolean
	)

	data class UserReport(
		val autoDelegationsToMissingHcp: Set<String>,
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
