package com.example.specclash.ui.home.components

import com.example.specclash.domain.PhoneSpec
import com.example.specclash.domain.SpecComparator

/**
 * One collapsible comparison section.
 *
 * @param title        Display title (e.g. "Display").
 * @param sourceKey    Key in [PhoneSpec.specs] whose sub-map holds the rows
 *                     (e.g. "Display", "Selfie Camera", "Comms").
 * @param keys         Ordered list of row labels to render inside the
 *                     section.
 * @param defaultOpen  If `true`, the section starts expanded; secondary
 *                     metadata groups (Launch, EU Label) start collapsed.
 */
data class SpecCategory(
    val title: String,
    val sourceKey: String,
    val keys: List<String>,
    val defaultOpen: Boolean = true,
)

object SpecMatrixBuilder {

    /**
     * All upstream categories returned by the GSMArena proxy. The order
     * here is also the display order in the matrix.
     */
    val CATEGORY_DEFINITIONS: List<SpecCategory> = listOf(
        SpecCategory(
            title = "Display",
            sourceKey = "Display",
            keys = listOf("Type", "Size", "Resolution", "Protection"),
            defaultOpen = true,
        ),
        SpecCategory(
            title = "Platform & Performance",
            sourceKey = "Platform",
            keys = listOf("OS", "Chipset", "CPU", "GPU"),
            defaultOpen = true,
        ),
        SpecCategory(
            title = "Memory & Storage",
            sourceKey = "Memory",
            keys = listOf("Internal", "Card slot"),
        ),
        SpecCategory(
            title = "Rear Cameras",
            sourceKey = "Main Camera",
            keys = listOf("Rear Sensors", "Features", "Video"),
            defaultOpen = true,
        ),
        SpecCategory(
            title = "Front / Selfie Camera",
            sourceKey = "Selfie Camera",
            keys = listOf("Front Sensor", "Front Video", "Front Features"),
        ),
        SpecCategory(
            title = "Sound & Audio",
            sourceKey = "Sound",
            keys = listOf("Loudspeaker", "3.5mm jack"),
        ),
        SpecCategory(
            title = "Connectivity & Comms",
            sourceKey = "Comms",
            keys = listOf("USB", "WLAN", "Bluetooth", "NFC", "Positioning", "Radio"),
        ),
        SpecCategory(
            title = "Sensors & Biometrics",
            sourceKey = "Features",
            keys = listOf("Sensors"),
        ),
        SpecCategory(
            title = "Battery & Charging",
            sourceKey = "Battery",
            keys = listOf("Type", "Charging"),
            defaultOpen = true,
        ),
        SpecCategory(
            title = "Body & Dimensions",
            sourceKey = "Body",
            keys = listOf("Dimensions", "Weight", "Build", "SIM"),
        ),
        SpecCategory(
            title = "Release & Status",
            sourceKey = "Launch",
            keys = listOf("Announced", "Status"),
            defaultOpen = false,
        ),
        SpecCategory(
            title = "Durability & Repairability",
            sourceKey = "EU LABEL",
            keys = listOf(
                "Battery Longevity",
                "Drop Resistance (Free Fall)",
                "Repairability Index",
                "Energy Efficiency",
            ),
            defaultOpen = false,
        ),
        SpecCategory(
            title = "Lab Benchmarks",
            sourceKey = "Our Tests",
            // "Geekbench 6", "AnTuTu v10", and "3DMark" are synthetic keys:
            // upstream packs all three scores into a single "Performance"
            // blob string, so buildRows() special-cases these three names
            // to parse and render them as separate rows instead of doing a
            // raw map lookup (see LAB_SYNTHETIC_KEYS below).
            keys = listOf("Geekbench 6", "AnTuTu v10", "3DMark", "Display", "Battery", "Loudspeaker"),
        ),
        SpecCategory(
            title = "Misc & Pricing",
            sourceKey = "Misc",
            keys = listOf("Price", "Models", "Colors", "SAR", "SAR EU"),
            defaultOpen = true,
        ),
    )

    /**
     * Backwards-compatible alias: the matrix renders [CATEGORY_DEFINITIONS]
     * in the exact order declared here.
     */
    val categories: List<SpecCategory> = CATEGORY_DEFINITIONS

    /**
     * Synthetic row keys for the "Lab Benchmarks" category (`sourceKey ==
     * "Our Tests"`). Upstream packs all three scores into a single
     * `"Performance"` blob string (e.g. `"AnTuTu: 2207809 (v10)\n\nGeekBench:
     * 9846 (v6)\n\n3DMark: 6687 (Wild Life Extreme)"`), so these keys are
     * resolved via [SpecComparator.extractLabBenchmarks] instead of a raw
     * map lookup - see [buildRows].
     */
    private val LAB_SYNTHETIC_KEYS: Set<String> = setOf("Geekbench 6", "AnTuTu v10", "3DMark")

    /**
     * GSMArena's proxy has scraped the front-camera block under a few
     * different casings over time ("Selfie Camera", "Selfie camera",
     * "SelfieCamera"). Try each known variant in turn so the "Front /
     * Selfie Camera" category doesn't silently render empty just because
     * the upstream casing shifted.
     */
    private val SOURCE_KEY_ALIASES: Map<String, List<String>> = mapOf(
        "Selfie Camera" to listOf("Selfie Camera", "Selfie camera", "SelfieCamera"),
    )

    /**
     * Synthetic row keys resolved from a raw upstream sub-map, keyed by
     * [SpecCategory.sourceKey]. Used for categories whose display rows
     * don't map 1:1 onto upstream keys - either because upstream packs
     * several rows into one friendlier label (EU Label) or because the
     * upstream key that holds the value varies by device (a single vs.
     * dual front sensor).
     */
    private val SYNTHETIC_RESOLVERS: Map<String, Map<String, (Map<String, String>) -> String?>> = mapOf(
        "Main Camera" to mapOf(
            // Phones expose their rear lens breakdown under whichever key
            // matches their actual lens count ("Single", "Dual", "Triple",
            // "Quad") - never more than one of these at once. Collapsing
            // them into a single "Rear Sensors" row means two phones with
            // different lens counts land on the same row instead of each
            // rendering into its own row with a "—" on the other side.
            "Rear Sensors" to { m: Map<String, String> ->
                m["Quad"] ?: m["Triple"] ?: m["Dual"] ?: m["Single"]
            },
        ),
        "Selfie Camera" to mapOf(
            "Front Sensor" to { m: Map<String, String> -> m["Single"] ?: m["Dual"] },
            "Front Video" to { m: Map<String, String> -> m["Video"] },
            "Front Features" to { m: Map<String, String> -> m["Features"] },
        ),
        "EU LABEL" to mapOf(
            "Battery Longevity" to { m: Map<String, String> -> m["Battery"] },
            "Drop Resistance (Free Fall)" to { m: Map<String, String> -> m["Free fall"] },
            "Repairability Index" to { m: Map<String, String> -> m["Repairability"] },
            "Energy Efficiency" to { m: Map<String, String> -> m["Energy"] },
        ),
    )

    /**
     * Build a list of [SpecRowData] for the given category, applying the
     * optional differences-only filter. Missing keys are silently skipped.
     *
     * The differences-only equality check normalizes whitespace (including
     * Unicode NBSP/thin-space variants that GSMArena's scrape is
     * inconsistent about) and folds case, so two values that only differ by
     * incidental formatting are correctly treated as "the same" and hidden.
     */
    fun buildRows(
        specA: PhoneSpec,
        specB: PhoneSpec,
        category: SpecCategory,
        differencesOnly: Boolean,
    ): List<SpecRowData> {
        val mapA = resolveSourceMap(specA.specs, category.sourceKey)
        val mapB = resolveSourceMap(specB.specs, category.sourceKey)
        val isLabBenchmarks = category.sourceKey == "Our Tests"
        val labA = if (isLabBenchmarks) SpecComparator.extractLabBenchmarks(specA.specs) else null
        val labB = if (isLabBenchmarks) SpecComparator.extractLabBenchmarks(specB.specs) else null
        val resolver = SYNTHETIC_RESOLVERS[category.sourceKey]

        return category.keys.mapNotNull { key ->
            val (rawA, rawB) = when {
                isLabBenchmarks && key in LAB_SYNTHETIC_KEYS ->
                    labBenchmarkValue(key, labA!!) to labBenchmarkValue(key, labB!!)
                resolver != null && key in resolver ->
                    resolver.getValue(key).invoke(mapA) to resolver.getValue(key).invoke(mapB)
                else -> mapA[key] to mapB[key]
            }
            if (rawA == null && rawB == null) return@mapNotNull null
            val cmp = SpecComparator.compare(rawA, rawB, key)
            val sameIgnoringFormatting = SpecComparator.normalizeWhitespace(rawA).lowercase() ==
                SpecComparator.normalizeWhitespace(rawB).lowercase()
            if (differencesOnly && sameIgnoringFormatting) return@mapNotNull null
            SpecRowData(
                key = key,
                valueA = formatBulletedValue(rawA),
                valueB = formatBulletedValue(rawB),
                comparison = cmp,
            )
        }
    }

    /** Resolves a category's raw sub-map, trying known upstream casing aliases first. */
    private fun resolveSourceMap(
        specs: Map<String, Map<String, String>>,
        sourceKey: String,
    ): Map<String, String> {
        SOURCE_KEY_ALIASES[sourceKey]?.forEach { alias ->
            specs[alias]?.let { return it }
        }
        return specs[sourceKey].orEmpty()
    }

    /**
     * Multi-lens camera values arrive as a double-newline delimited blob,
     * e.g. `"200 MP wide...\n\n10 MP telephoto...\n\n50 MP periscope..."`.
     * Render each lens as its own bulleted line instead of letting them run
     * together (or get clipped) as a single block of text. Values with no
     * double-newline are left untouched.
     */
    private fun formatBulletedValue(raw: String?): String? {
        if (raw.isNullOrEmpty() || !raw.contains("\n\n")) return raw
        return raw.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "• $it" }
    }

    /** Formats one [LAB_SYNTHETIC_KEYS] row's display value from parsed lab benchmarks. */
    private fun labBenchmarkValue(key: String, lab: SpecComparator.LabBenchmarks): String? = when (key) {
        "Geekbench 6" -> lab.geekbenchV6?.let { "${SpecComparator.formatLabScoreNumber(it)} (v6)" }
        "AnTuTu v10" -> lab.antutuV10?.let { "${SpecComparator.formatLabScoreNumber(it)} (v10)" }
        "3DMark" -> lab.threeDMarkScore?.let { score ->
            val scoreText = SpecComparator.formatLabScoreNumber(score)
            lab.threeDMarkSuite?.let { suite -> "$scoreText ($suite)" } ?: scoreText
        }
        else -> null
    }
}
