package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.filters.UserFilters
import com.icure.cardinal.sdk.model.GroupScoped
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
            val users = userApi.inGroup.filterUsersBy(groupId, UserFilters.all()).next(1000)
            val healthcareParties = hcpApi.inGroup.getHealthcareParties(groupId, users.mapNotNull { it.entity.healthcarePartyId })
            val toFix = healthcareParties.filter { it.entity.parentId == "" }.map { it.entity.copy(parentId = null) }
            toFix.forEach {
                echo("Fixing parent for ${it.id} in $groupId")
                hcpApi.inGroup.modifyHealthcareParty(GroupScoped(it, groupId))
            }
        } catch (e: CancellationException) {
            //skip
        } catch (e: Exception) {
            echo("Error checking group $groupId: ${e.message}")
        }
    }
}
