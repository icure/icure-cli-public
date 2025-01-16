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
			val cluster = listOf("couch-cluster-01", "couch-cluster-02").firstOrNull {
				config.client.get("https://$it.icure.cloud/$db").let {
					it.status.isSuccess()
				}
			} ?: throw IllegalStateException("Cannot find group $group on either cluster")
			val missingEntities = mutableSetOf<String>()
			val hcpsById = mutableMapOf<String, HcpStub>()
			val usersReport =
				config.client.get("https://$cluster.icure.cloud/$db/_design/User/_view/all?include_docs=true").body<Row<UserStub>>().rows.map {
					it.doc
				}.filter {
					it.deletionDate == null && it.status?.lowercase() == "active"
				}.associateWith { user ->
					val missingAutoDelegations = user.autoDelegations["all"]?.filter {
						missingEntities.contains(it)
							|| config.client.get("https://$cluster.icure.cloud/$db/$it").status.value == 404
					}?.toSet() ?: emptySet()
					missingEntities.addAll(missingAutoDelegations)

					(user.healthcarePartyId?.let { hcpId ->
						val hcp = config.client.get("https://$cluster.icure.cloud/$db/$hcpId").takeIf {
							it.status.isSuccess()
						}?.body<HcpStub>()?.also {
							hcpsById[it.id] = it
						}
						if (hcp == null) {
							UserReport(
								missingAutoDelegations = missingAutoDelegations,
								hasMissingHcp = true
							)
						} else if (missingAutoDelegations.isNotEmpty() && hcp.parentId != null) {
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
					hasEmptyPublicKey = hcp.publicKey?.isBlank() == true,
					hasMissingParent = hcp.parentId != null && config.client.get("https://$cluster.icure.cloud/$db/${hcp.parentId}").status.isSuccess(),
				)
			}
			usersReport.forEach { (user, report) ->
				if (report.toLog) {
					if (report.missingAutoDelegations.isNotEmpty()) {
						echo("User ${user.id} has auto-delegations for non-existing users: ${report.missingAutoDelegations.joinToString(", ")}")
					}
					if (report.hasMissingHcp) {
						echo("User ${user.id} healthcare party does not exist or is deleted")
					}
					if (report.hasAutoDelegationsAndParent) {
						echo("User ${user.id} has auto-delegations but the related hcp (${user.healthcarePartyId}) has a parent")
					}
				}
			}
			hcpReport.forEach { (hcp, report) ->
				if (report.toLog) {
					if (report.hasEmptyPublicKey) {
						echo("Hcp ${hcp.id} has an empty public key")
					}
					if (report.hasMissingParent) {
						echo("Hcp ${hcp.id} parent (${hcp.parentId}) does not exist or is deleted")
					}
				}
			}
		}
	}

	data class HcpReport(
		val hasEmptyPublicKey: Boolean,
		val hasMissingParent: Boolean
	) {
		val toLog = hasEmptyPublicKey && hasMissingParent
	}

	data class UserReport(
		val missingAutoDelegations: Set<String>,
		val hasMissingHcp: Boolean = false,
		val hasAutoDelegationsAndParent: Boolean = false
	) {
		val toLog = hasMissingHcp
			|| hasAutoDelegationsAndParent
			|| missingAutoDelegations.isNotEmpty()
	}

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
