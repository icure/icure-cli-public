package com.icure.lib

import com.icure.sdk.IcureBaseSdk
import com.icure.sdk.auth.UsernamePassword
import com.icure.sdk.model.Code
import com.icure.sdk.options.AuthenticationMethod
import kotlinx.coroutines.CancellationException

suspend fun deployCodes(serverUrl: String, codes: List<Code>, userName: String, password: String, local: Boolean = false, regexFilter: String? = null, echo: (String) -> Unit = { println(it) }) {
    val api = IcureBaseSdk.initialise(
        serverUrl,
        AuthenticationMethod.UsingCredentials(UsernamePassword(userName, password))
    )

    val regex = regexFilter?.toRegex()
    val groupApi = api.group
    val codeApi = api.code
    val groups = if (!local) groupApi.listGroups().filter { regex == null || regex.matches(it.id) }.map { it.id } else listOf(api.user.getCurrentUser().groupId!!)

    groups.forEach { groupId ->
        try {
            echo("Importing into group $groupId")
            val existingCodes =
                (if (!local) codeApi.getCodes(groupId, codes.map { c -> c.id }) else codeApi.getCodes(codes.map { c -> c.id })).associateBy { c -> c.id }
            val codesToAdd = codes.filter { code -> !existingCodes.containsKey(code.id) }
            val codesToUpdate = codes.filter { code -> existingCodes.containsKey(code.id) }
                .map { code -> code.copy(rev = existingCodes[code.id]?.rev) }

            codesToAdd.chunked(100).forEach { chunk ->
                if (!local) {
                    codeApi.createCodes(groupId, chunk)
                } else {
                    codeApi.createCodes(chunk)
                }
            }
            codesToUpdate.chunked(100).forEach { chunk ->
                if (!local) codeApi.modifyCodes(groupId, chunk) else codeApi.modifyCodes(chunk)
            }
            echo("Created ${codesToAdd.size} and updated ${codesToUpdate.size} codes into group $groupId")
        } catch (e: CancellationException) {
            //skip
        } catch (e: Exception) {
            echo("Error importing into group $groupId: ${e.message}")
        }
    }
}
