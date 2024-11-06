package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.model.Code
import com.icure.cardinal.sdk.model.Tarification
import com.icure.cardinal.sdk.model.base.StoredDocument
import com.icure.cardinal.sdk.model.filter.predicate.Predicate
import com.icure.cardinal.sdk.utils.Serialization
import com.icure.cli.commands.api.Patch
import com.reidsync.kxjsonpatch.JsonPatch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

suspend fun valorisationPatch(
    api: CardinalBaseSdk,
    ids: List<String>,
    ref: String,
    predicate: String,
    local: Boolean = false,
    regexFilter: String? = null,
    echo: (String) -> Unit = { println(it) }
) {
    val regex = regexFilter?.toRegex()
    val groupApi = api.group

    val groups = if (!local) groupApi.listGroups().filter { regex == null || regex.matches(it.id) }
        .map { it.id } else listOf(api.user.getCurrentUser().groupId!!)

    groups.forEach { groupId ->
        try {
            echo("Checking group $groupId")
            val entities = api.tarification.getTarifications(ids)
            entities.forEach { e ->
                val patched = e.copy(valorisations = e.valorisations.map {
                    if ((it.reference ?: emptySet()).contains(ref.toInt())) {
                        it.copy(predicate = predicate)
                    } else {
                        it
                    }
                }.toSet())
                api.tarification.modifyTarification(patched)
            }
        } catch (e: CancellationException) {
            //skip
        } catch (e: Exception) {
            echo("Error patching group $groupId: ${e.message}")
        }
    }
}
