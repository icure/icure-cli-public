package com.icure.cli

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert

fun RawOption.byteSize() = convert({ localization.intMetavar() }) {
    val match = Regex("(\\d+)([kKmMgG]?)").matchEntire(it) ?: throw BadParameterValue("$it is not a valid size")
    when(match.groupValues[2]) {
        "k", "K" -> match.groupValues[1].toLong() * 1024
        "m", "M" -> match.groupValues[1].toLong() * 1024 * 1024
        "g", "G" -> match.groupValues[1].toLong() * 1024 * 1024 * 1024
        else -> match.groupValues[1].toLong()
    }
}