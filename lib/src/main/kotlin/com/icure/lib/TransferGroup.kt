package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.model.security.Operation

suspend fun transferGroup(destinationApi: CardinalBaseSdk, sourceApi: CardinalBaseSdk, group: String, destination: String, echo: (String) -> Unit = { println(it) }) {
    val sourceGroupApi = sourceApi.group
    val destinationGroupApi = destinationApi.group

    val transferToken = destinationGroupApi.getOperationTokenForGroup(destination, Operation.TransferGroup, 10000)
    val movedGroup = sourceGroupApi.changeSuperGroup(group, transferToken)

    check(movedGroup.superGroup == destination) { "Failed to move group ${movedGroup.id} to $destination" }

    echo("Moved group ${movedGroup.id} to $destination")
}
