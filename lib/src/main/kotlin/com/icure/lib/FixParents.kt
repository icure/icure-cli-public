package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import kotlinx.coroutines.CancellationException

suspend fun fixParents(api: CardinalBaseSdk, local: Boolean = false, regexFilter: String? = null, echo: (String) -> Unit = { println(it) }) {
    val regex = regexFilter?.toRegex()
    val groupApi = api.group
    val userApi = api.user
    val hcpApi = api.healthcareParty
    val groups = if (!local) groupApi.listGroups().filter { regex == null || regex.matches(it.id) }.map { it.id } else listOf(api.user.getCurrentUser().groupId!!)

    groups.forEach { groupId ->
        try {
            echo("Checking group $groupId")
            val users = userApi.listUsersInGroup(groupId, limit = 1000).rows
            val healthcareParties = hcpApi.getHealthcarePartiesInGroup(groupId, users.mapNotNull { it.healthcarePartyId })
            val toFix = healthcareParties.filter { it.parentId == "" }.map { it.copy(parentId = null) }
            toFix.forEach {
                echo("Fixing parent for ${it.id} in $groupId")
                hcpApi.modifyHealthcarePartyInGroup(groupId, it)
            }
        } catch (e: CancellationException) {
            //skip
        } catch (e: Exception) {
            echo("Error checking group $groupId: ${e.message}")
        }
    }
}
