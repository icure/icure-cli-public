@file:Suppress("UNCHECKED_CAST")

package com.icure.cli.format.xml

import com.icure.cardinal.sdk.model.Code
import kotlinx.coroutines.runBlocking
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.util.Locale


fun defaultHandler(type: String, persister: (code: Code) -> Unit) = object : DefaultHandler() {
    var initialized = false
    var version: String = "1.0"
    var charsHandler: ((chars: String) -> Unit)? = null
    var code: MutableMap<String, Any> = mutableMapOf()
    var characters: String = ""

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        ch?.let { characters += String(it, start, length) }
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (!initialized && qName != "kmehr-cd") {
            throw IllegalArgumentException("Not supported")
        }
        initialized = true
        characters = ""
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "VERSION" -> charsHandler = {
                    version = it
                }

                "VALUE" -> {
                    code = mutableMapOf(
                        "type" to type,
                        "version" to version,
                        "label" to mapOf<String, String>(),
                        "regions" to setOf<String>()
                    )
                }

                "CODE" -> charsHandler = { code["code"] = it }
                "PARENT" -> charsHandler = { code["qualifiedLinks"] = mapOf("parent" to listOf("$type|$it|$version")) }
                "DESCRIPTION" -> charsHandler = {
                    attributes?.getValue("L")?.let { attributesValue ->
                        code["label"] = (code["label"] as Map<*, *>) + (attributesValue to it.trim())
                    }
                }

                "REGIONS" -> charsHandler = { code["regions"] = (code["regions"] as Set<*>) + it.trim() }
                else -> {
                    charsHandler = null
                }
            }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        charsHandler?.let { it(characters) }
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "VALUE" -> {
                    code["id"] = "${code["type"] as String}|${code["code"] as String}|${code["version"] as String}"
                    persister(
                        Code(
                            id = code["id"] as String,
                            type = code["type"] as String,
                            code = code["code"] as String,
                            version = code["version"] as String,
                            label = code["label"] as Map<String, String>,
                            regions = code["regions"] as Set<String>,
                            qualifiedLinks = (code["qualifiedLinks"] ?: emptyMap<String,List<String>>()) as Map<String, List<String>>
                        )
                    )
                }

                else -> null
            }
        }
    }
}

fun beThesaurusHandler(type: String, persister: (code: Code) -> Unit) = object : DefaultHandler() {
    var initialized = false
    var version: String = "1.0"
    var charsHandler: ((chars: String) -> Unit)? = null
    var code: MutableMap<String, Any> = mutableMapOf()
    var characters: String = ""

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        ch?.let { characters += String(it, start, length) }
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (!initialized && qName != "Root") {
            throw IllegalArgumentException("XML not supported : $type")
        }
        if (!initialized) {
            version = attributes?.getValue("version")
                ?: throw IllegalArgumentException("Unknown version in : $type")
        }

        initialized = true
        characters = ""
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "CLINICAL_LABEL" -> {
                    code = mutableMapOf(
                        "type" to type,
                        "version" to version,
                        "label" to mutableMapOf<String, String>(),
                        "searchTerms" to mutableMapOf<String, Set<String>>(),
                        "links" to mutableSetOf<String>(),
                        "qualifiedLinks" to mutableMapOf<String, List<String>>(),
                        "regions" to setOf<String>()
                    )
                }

                "IBUI" -> charsHandler = { ch -> code["code"] = ch }
                "ICPC_2_CODE_1", "ICPC_2_CODE_1X", "ICPC_2_CODE_1Y",
                "ICPC_2_CODE_2", "ICPC_2_CODE_2X", "ICPC_2_CODE_2Y" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) code["links"] = (code["links"] as Set<*>) + ("ICPC|$ch|2")
                    if (ch.isNotBlank()) (code["qualifiedLinks"] as MutableMap<String, List<String>>)["icpc2"] = ((code["qualifiedLinks"] as Map<String, List<String>>)["icpc2"] ?: emptyList()) + ("ICPC|$ch|2")
                }

                "ICD_10_CODE_1", "ICD_10_CODE_1X", "ICD_10_CODE_1Y",
                "ICD_10_CODE_2", "ICD_10_CODE_2X", "ICD_10_CODE_2Y" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) code["links"] = (code["links"] as Set<*>) + ("ICD|$ch|10")
                    if (ch.isNotBlank()) (code["qualifiedLinks"] as MutableMap<String, List<String>>)["icd10"] = ((code["qualifiedLinks"] as Map<String, List<String>>)["icd10"] ?: emptyList()) + ("ICD|$ch|10")
                }

                "FR_CLINICAL_LABEL" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) {
                        code["label"] = (code["label"] as Map<*, *>) +
                                ("fr" to ch.replace("&apos;", "'"))
                    }
                }

                "NL_CLINICAL_LABEL" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) {
                        code["label"] = (code["label"] as Map<*, *>) + ("nl" to ch)
                    }
                }

                "CLEFS_RECHERCHE_FR" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) {
                        code["searchTerms"] = (code["searchTerms"] as Map<*, *>) +
                                ("fr" to ch.split(" ").map { it.trim() }.toSet())
                    }
                }

                "ZOEKTERMEN_NL" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) {
                        code["searchTerms"] = (code["searchTerms"] as Map<*, *>) +
                                ("nl" to ch.split(" ").map { it.trim() }.toSet())
                    }
                }

                "REGIONS" -> charsHandler = { code["regions"] = (code["regions"] as Set<String>) + it.trim() }
                else -> charsHandler = null
            }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        charsHandler?.let { it(characters) }
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "CLINICAL_LABEL" -> {
                    code["id"] = "${code["type"] as String}|${code["code"] as String}|${code["version"] as String}"
                    persister(
                        Code(
                            id = code["id"] as String,
                            type = code["type"] as String,
                            code = code["code"] as String,
                            version = code["version"] as String,
                            label = code["label"] as Map<String, String>,
                            regions = code["regions"] as Set<String>,
                            links = (code["links"] ?: emptySet<String>()) as Set<String>,
                            qualifiedLinks = (code["qualifiedLinks"] ?: emptyMap<String, List<String>>()) as Map<String, List<String>>
                        )
                    )
                }

                else -> null
            }
        }
    }
}

fun beThesaurusProcHandler(type: String, persister: (code: Code) -> Unit) = object : DefaultHandler() {
    var initialized = false
    var version: String = "1.0"
    var charsHandler: ((chars: String) -> Unit)? = null
    var code: MutableMap<String, Any> = mutableMapOf()
    var characters: String = ""

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        ch?.let { characters += String(it, start, length) }
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (!initialized && qName != "Root") {
            throw IllegalArgumentException("XML not supported : $type")
        }
        if (!initialized) {
            version = attributes?.getValue("version")
                ?: throw IllegalArgumentException("Unknown version in : $type")
        }

        initialized = true
        characters = ""
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "PROCEDURE" -> {
                    code = mutableMapOf(
                        "type" to type,
                        "version" to version,
                        "label" to mutableMapOf<String, String>(),
                        "searchTerms" to mutableMapOf<String, Set<String>>(),
                        "qualifiedLinks" to mutableMapOf<String, List<String>>().apply { put("ibui", emptyList()) },
                        "regions" to setOf<String>()
                    )
                }

                "CISP" -> charsHandler = { ch -> code["code"] = ch }
                "IBUI" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) code["links"] = setOf("BE-THESAURUS|$ch|$version")
                    if (ch.isNotBlank()) (code["qualifiedLinks"] as MutableMap<String, List<String>>)["icd10"] = ((code["qualifiedLinks"] as Map<String, List<String>>)["icd10"] ?: emptyList()) + ("BE-THESAURUS|$ch|$version")
                }

                "IBUI_NOT_EXACT" -> charsHandler = { ch ->
                    if (ch.isNotBlank() && !code.containsKey("links")) {
                        code["links"] = setOf("BE-THESAURUS|$ch|$version")
                        (code["qualifiedLinks"] as MutableMap<String, List<String>>)["icd10"] = ((code["qualifiedLinks"] as Map<String, List<String>>)["icd10"] ?: emptyList()) + ("BE-THESAURUS|$ch|$version")
                    }
                }

                "LABEL_FR" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) code["label"] = (code["label"] as Map<*, *>) + ("fr" to ch)
                }

                "LABEL_NL" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) code["label"] = (code["label"] as Map<*, *>) + ("nl" to ch)
                }

                "SYN_FR" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) {
                        code["searchTerms"] = (code["searchTerms"] as Map<*, *>) +
                                ("fr" to ch.split(" ").map { it.trim() }.toSet())
                    }
                }

                "SYN_NL" -> charsHandler = { ch ->
                    if (ch.isNotBlank()) {
                        code["searchTerms"] = (code["searchTerms"] as Map<*, *>) +
                                ("nl" to ch.split(" ").map { it.trim() }.toSet())
                    }
                }

                "REGIONS" -> charsHandler = { code["regions"] = (code["regions"] as Set<*>) + it.trim() }
                else -> charsHandler = null
            }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        charsHandler?.let { it(characters) }
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "PROCEDURE" -> {
                    code["id"] = "${code["type"] as String}|${code["code"] as String}|${code["version"] as String}"
                    persister(
                        Code(
                            id = code["id"] as String,
                            type = code["type"] as String,
                            code = code["code"] as String,
                            version = code["version"] as String,
                            label = code["label"] as Map<String, String>,
                            regions = code["regions"] as Set<String>,
                            searchTerms = (code["searchTerms"]?: emptyMap<String, List<String>>()) as Map<String, Set<String>>,
                            links = (code["links"] ?: emptySet<String>()) as Set<String>,
                            qualifiedLinks = (code["qualifiedLinks"] ?: emptyMap<String, List<String>>()) as Map<String, List<String>>
                        )
                    )
                }

                else -> null
            }
        }
    }
}

fun iso6391Handler(type: String, persister: suspend (code: Code) -> Unit) = object : DefaultHandler() {
    var initialized = false
    var version: String = "1.0"
    var charsHandler: ((chars: String) -> Unit)? = null
    var code: MutableMap<String, Any> = mutableMapOf()
    var characters: String = ""

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        ch?.let { characters += String(it, start, length) }
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (!initialized && qName != "ISO639-1") {
            throw IllegalArgumentException("XML not supported : $type")
        }

        initialized = true
        characters = ""
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "VERSION" -> charsHandler = { ch ->
                    version = ch
                }

                "VALUE" -> {
                    code = mutableMapOf(
                        "type" to type,
                        "version" to version,
                        "label" to mapOf<String, String>(),
                        "regions" to setOf<String>()
                    )
                }

                "CODE" -> charsHandler = { ch -> code["code"] = ch }
                "DESCRIPTION" -> charsHandler = {
                    attributes?.getValue("L")?.let { attributesValue ->
                        code["label"] = (code["label"] as Map<*, *>) + (attributesValue to it.trim())
                    }
                }

                "REGIONS" -> charsHandler = { code["regions"] = (code["regions"] as Set<String>) + it.trim() }
                else -> charsHandler = null
            }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        charsHandler?.let { it(characters) }
        qName?.let {
            when (it.uppercase(Locale.getDefault())) {
                "VALUE" -> {
                    runBlocking {
                        code["id"] = "${code["type"] as String}|${code["code"] as String}|${code["version"] as String}"
                        persister(
                            Code(
                                id = code["id"] as String,
                                type = code["type"] as String,
                                code = code["code"] as String,
                                version = code["version"] as String,
                                label = code["label"] as Map<String, String>,
                                regions = code["regions"] as Set<String>,
                                qualifiedLinks = (code["qualifiedLinks"] ?: emptyMap<String, List<String>>()) as Map<String, List<String>>
                            )
                        )
                    }
                }

                else -> null
            }
        }
    }
}
