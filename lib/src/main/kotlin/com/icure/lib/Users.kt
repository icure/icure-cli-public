package com.icure.lib

import com.icure.cardinal.sdk.CardinalBaseSdk
import com.icure.cardinal.sdk.filters.UserFilters
import com.icure.cardinal.sdk.model.GroupScoped
import com.icure.cardinal.sdk.model.User
import com.icure.cardinal.sdk.utils.Serialization

suspend fun listUsers(
    api: CardinalBaseSdk,
    group: String,
    filter: String? = null,
    limit: Int = 100,
    echo: (String) -> Unit = { println(it) },
) {
    val filterOptions = filter?.let { UserFilters.byNameEmailOrPhone(it) } ?: UserFilters.all()
    val iterator = api.user.inGroup.filterUsersBy(group, filterOptions)
    val users = iterator.next(limit).map { it.entity }
    users.forEach { user ->
        echo("${user.id} | ${user.login ?: ""} | ${user.name ?: ""} | ${user.email ?: ""} | ${user.status?.name ?: ""}")
    }
    echo("${users.size} user(s) listed in group $group")
}

suspend fun getUser(
    api: CardinalBaseSdk,
    group: String,
    id: String,
    echo: (String) -> Unit = { println(it) },
) {
    val scoped = api.user.inGroup.getUser(group, id)
    if (scoped == null) {
        echo("User $id not found in group $group")
        return
    }
    echo(Serialization.json.encodeToString(scoped.entity))
}

suspend fun createUser(
    api: CardinalBaseSdk,
    group: String,
    user: User,
    password: String? = null,
    echo: (String) -> Unit = { println(it) },
) {
    val created = api.user.inGroup.createUser(GroupScoped(user, group)).entity
    echo("Created user ${created.id} in group $group")
    if (password != null) {
        try {
            api.user.inGroup.modifyUserPassword(group, created.id, password)
            echo("Set password for user ${created.id}")
        } catch (e: Exception) {
            echo("WARNING: user ${created.id} was created but setting the password failed: ${e.message}")
        }
    }
}

suspend fun modifyUser(
    api: CardinalBaseSdk,
    group: String,
    user: User,
    echo: (String) -> Unit = { println(it) },
) {
    val modified = api.user.inGroup.modifyUser(GroupScoped(user, group)).entity
    echo("Modified user ${modified.id} in group $group")
}
