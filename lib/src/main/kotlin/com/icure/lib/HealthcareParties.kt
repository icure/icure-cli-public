package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.filters.HealthcarePartyFilters
import com.icure.cardinal.sdk.model.GroupScoped
import com.icure.cardinal.sdk.model.HealthcareParty
import com.icure.cardinal.sdk.utils.Serialization

suspend fun listHealthcareParties(
    api: CardinalBaseSdk,
    group: String,
    filter: String? = null,
    limit: Int = 100,
    echo: (String) -> Unit = { println(it) },
) {
    val filterOptions = filter?.let { HealthcarePartyFilters.byName(it) } ?: HealthcarePartyFilters.all()
    val iterator = api.healthcareParty.inGroup.filterHealthPartiesBy(group, filterOptions)
    val hcps = iterator.next(limit).map { it.entity }
    hcps.forEach { hcp ->
        val fullName = listOfNotNull(hcp.firstName, hcp.lastName).joinToString(" ")
        echo("${hcp.id} | $fullName | ${hcp.name ?: ""} | ${hcp.speciality ?: ""} | ${hcp.ssin ?: ""}")
    }
    echo("${hcps.size} healthcare party(ies) listed in group $group")
}

suspend fun getHealthcareParty(
    api: CardinalBaseSdk,
    group: String,
    id: String,
    echo: (String) -> Unit = { println(it) },
) {
    val scoped = api.healthcareParty.inGroup.getHealthcareParty(group, id)
    if (scoped == null) {
        echo("Healthcare party $id not found in group $group")
        return
    }
    echo(Serialization.json.encodeToString(scoped.entity))
}

suspend fun createHealthcareParty(
    api: CardinalBaseSdk,
    group: String,
    hcp: HealthcareParty,
    echo: (String) -> Unit = { println(it) },
) {
    val created = api.healthcareParty.inGroup.createHealthcareParty(GroupScoped(hcp, group)).entity
    echo("Created healthcare party ${created.id} in group $group")
}

suspend fun modifyHealthcareParty(
    api: CardinalBaseSdk,
    group: String,
    hcp: HealthcareParty,
    echo: (String) -> Unit = { println(it) },
) {
    val modified = api.healthcareParty.inGroup.modifyHealthcareParty(GroupScoped(hcp, group)).entity
    echo("Modified healthcare party ${modified.id} in group $group")
}
