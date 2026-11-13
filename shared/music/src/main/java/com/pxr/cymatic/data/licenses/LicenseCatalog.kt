package com.pxr.cymatic.data.licenses

import android.content.res.AssetManager
import org.json.JSONArray

internal class LicenseCatalog(private val assets: AssetManager) {
    private val documents = mutableMapOf<String, String>()

    private val libraries = run {
        val rows =
            JSONArray(
                assets.open("licenses/dependencies.json").bufferedReader().use {
                    it.readText()
                }
            )
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val texts = row.getJSONArray("documents")
            val licenses = row.getJSONArray("licenses")
            LicenseEntry(
                row.getString("title"),
                List(texts.length()) { texts.getString(it) },
                buildString {
                    appendLine(row.getString("coordinate"))
                    appendLine()
                    appendLine(row.optString("project"))
                    appendLine()
                    repeat(licenses.length()) { i ->
                        val license = licenses.getJSONObject(i)
                        appendLine(license.getString("name"))
                        appendLine(license.optString("url"))
                    }
                },
                List(licenses.length()) { licenses.getJSONObject(it).getString("name") }.toSet(),
            )
        }
    }

    private fun components(entries: List<LicenseEntry>) =
        entries.joinToString("\n\n") { entry ->
            "## ${entry.title}\n\n${entry.summary}"
        }

    private val apacheLibraries = libraries.filter { entry ->
        entry.licenseNames.any { it.contains("Apache", ignoreCase = true) }
    }
    private val mitTexts =
        libraries
            .filter { entry -> entry.licenseNames.any { it.contains("MIT") } }
            .flatMap { it.assets }
            .distinct()
    private val bsdTexts =
        libraries
            .filter { entry -> entry.licenseNames.any { it.contains("BSD") } }
            .flatMap { it.assets }
            .distinct()
    private val extraNotices = run {
        libraries
            .flatMap { it.assets }
            .distinct()
            .filter { path ->
                path !in mitTexts &&
                    path !in bsdTexts &&
                    !assets.open(path).bufferedReader().use {
                        it.readText().trimStart().startsWith("Apache License")
                    }
            }
    }

    fun entries(group: LicenseGroup): List<LicenseEntry> =
        when (group) {
            LicenseGroup.APACHE ->
                listOf(
                    LicenseEntry("License text", listOf("licenses/contour/LICENSE")),
                    LicenseEntry(
                        "Covered components",
                        emptyList(),
                        "## Vendored code\n\n- Contour — Copyright 2026 Chronosaur\n- Apple ALAC — Copyright 2011 Apple Inc.\n\n" +
                            components(apacheLibraries),
                    ),
                )
            LicenseGroup.OFL ->
                listOf(
                    LicenseEntry("License text", listOf("licenses/fonts/Shared-OFL.txt")),
                    LicenseEntry("Fonts & credits", listOf("licenses/fonts/FONT-CREDITS.txt")),
                )
            LicenseGroup.OTHER ->
                listOf(
                    LicenseEntry("BSD · libFLAC", listOf("licenses/libFLAC.txt")),
                    LicenseEntry(
                        "BSD · Protobuf",
                        bsdTexts,
                    ),
                    LicenseEntry(
                        "MIT",
                        mitTexts,
                    ),
                    LicenseEntry("0BSD", listOf("licenses/contour/LICENSE-devicePEQ.txt")),
                    LicenseEntry(
                        "LLVM exceptions & legacy terms",
                        listOf("licenses/LLVM-exceptions.txt"),
                    ),
                )
            LicenseGroup.ATTRIBUTIONS ->
                listOf(
                    LicenseEntry("Library credits", emptyList(), components(libraries)),
                    LicenseEntry("Decoder credits", listOf("licenses/DECODER-CREDITS.txt")),
                    LicenseEntry(
                        "DAC EQ credits",
                        listOf("licenses/contour/CYMATIC-NOTICE.txt", "licenses/contour/NOTICE"),
                    ),
                    LicenseEntry(
                        "Upstream third-party notices",
                        listOf("licenses/contour/THIRD_PARTY_NOTICES.md"),
                    ),
                    LicenseEntry(
                        "Additional library notices",
                        extraNotices,
                    ),
                )
            LicenseGroup.ROOT -> emptyList()
        }

    fun document(entry: LicenseEntry): String = buildString {
        if (entry.summary.isNotBlank()) appendLine(entry.summary)
        entry.assets.forEach { path ->
            appendLine()
            appendLine(read(path))
        }
    }

    init {
        LicenseGroup.entries.flatMap(::entries).flatMap { it.assets }.distinct().forEach(::read)
    }

    private fun read(path: String) =
        documents.getOrPut(path) {
            assets.open(path).bufferedReader().use { it.readText() }
        }
}
