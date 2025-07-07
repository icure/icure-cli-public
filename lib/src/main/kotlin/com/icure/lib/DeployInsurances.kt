package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.model.Insurance
import kotlinx.coroutines.CancellationException

suspend fun deployInsurances(api: CardinalBaseSdk, insurances: List<Insurance>, local: Boolean = false, regexFilter: String? = null, echo: (String) -> Unit = { println(it) }) {
    val regex = regexFilter?.toRegex()
    val groupApi = api.group
    val insuranceApi = api.insurance
    val groups = if (!local) groupApi.listGroups().filter { regex == null || regex.matches(it.id) }.map { it.id } else listOf(api.user.getCurrentUser().groupId!!)

    groups.forEach { groupId ->
        try {
            echo("Importing into group $groupId")
            val existingInsurances =
                (if (!local) insuranceApi.getInsurancesInGroup(groupId,
                    insurances.joinToString(",") { c -> c.id }) else insuranceApi.getInsurances(insurances.map { c -> c.id })).associateBy { c -> c.id }
            val insurancesToAdd = insurances.filter { insurance -> !existingInsurances.containsKey(insurance.id) }
            val insurancesToUpdate = insurances.filter { insurance -> existingInsurances.containsKey(insurance.id) }
                .map { insurance -> insurance.copy(rev = existingInsurances[insurance.id]?.rev) }

            insurancesToAdd.chunked(100).forEach { chunk ->
                insuranceApi.createInsurancesInGroup(groupId, chunk)
            }
            insurancesToUpdate.chunked(100).forEach { chunk ->
                insuranceApi.modifyInsurancesInGroup(groupId, chunk)
            }
            echo("Created ${insurancesToAdd.size} and updated ${insurancesToUpdate.size} insurances into group $groupId")
        } catch (e: CancellationException) {
            //skip
        } catch (e: Exception) {
            echo("Error importing into group $groupId: ${e.message}")
        }
    }
}
