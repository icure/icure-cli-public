package com.icure.cli.api

import io.ktor.client.*

class CliktConfig {
    private var _server: String? = null
    private var _username: String? = null
    private var _password: String? = null

    var server: String
        get() = _server!!
        set(value) {
            _server = value
        }

    var username: String
        get() = _username!!
        set(value) {
            _username = value
        }

    var password: String
        get() = _password!!
        set(value) {
            _password = value
        }

}
