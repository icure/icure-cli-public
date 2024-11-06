package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.model.Code
import com.icure.cardinal.sdk.model.Tarification
import com.icure.cardinal.sdk.model.base.StoredDocument
import com.icure.cardinal.sdk.utils.Serialization
import com.icure.cli.commands.api.Patch
import com.reidsync.kxjsonpatch.JsonPatch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

suspend fun patch(
    api: CardinalBaseSdk,
    entity: Patch.Entity,
    ids: List<String>,
    patchs: List<String>,
    local: Boolean = false,
    regexFilter: String? = null,
    echo: (String) -> Unit = { println(it) }
) {
    val regex = regexFilter?.toRegex()
    val groupApi = api.group
    val loader: suspend (ids: List<String>) -> List<StoredDocument> = when (entity) {
        Patch.Entity.code -> { codeIds: List<String> -> api.code.getCodes(codeIds) }
        Patch.Entity.tarification -> { invoicingCodeIds: List<String> ->
            api.tarification.getTarifications(
                invoicingCodeIds
            )
        }
    }
    val saver: suspend (ids: List<StoredDocument>) -> List<StoredDocument> = when (entity) {
        Patch.Entity.code -> { codes: List<StoredDocument> -> api.code.modifyCodes(codes.mapNotNull { it as? Code }) }
        Patch.Entity.tarification -> { invoicingCodes: List<StoredDocument> ->
            invoicingCodes.mapNotNull { it as? Tarification }.map { api.tarification.modifyTarification(it) }
        }
    }
    val toJsonElement: (StoredDocument) -> JsonElement = when (entity) {
        Patch.Entity.code -> { code: StoredDocument ->
            Serialization.json.encodeToJsonElement(
                Code.serializer(),
                code as Code
            )
        }

        Patch.Entity.tarification -> { tarification: StoredDocument ->
            Serialization.json.encodeToJsonElement(
                Tarification.serializer(),
                tarification as Tarification
            )
        }
    }
    val fromJsonElement: (JsonElement) -> StoredDocument = when (entity) {
        Patch.Entity.code -> { json: JsonElement -> Serialization.json.decodeFromJsonElement(Code.serializer(), json) }
        Patch.Entity.tarification -> { json: JsonElement ->
            Serialization.json.decodeFromJsonElement(
                Tarification.serializer(),
                json
            )
        }
    }

    val groups = if (!local) groupApi.listGroups().filter { regex == null || regex.matches(it.id) }
        .map { it.id } else listOf(api.user.getCurrentUser().groupId!!)

    groups.forEach { groupId ->
        try {
            echo("Checking group $groupId")
            val entities = loader(ids)
            saver(patchs.fold(entities) { acc, patch ->
                acc.map {
                    fromJsonElement(JsonPatch.apply(
                            patch = Serialization.json.decodeFromString(JsonElement.serializer(), patch),
                            source = toJsonElement(it)
                    ))
                }
            })
        } catch (e: CancellationException) {
            //skip
        } catch (e: Exception) {
            echo("Error patching group $groupId: ${e.message}")
        }
    }
}
