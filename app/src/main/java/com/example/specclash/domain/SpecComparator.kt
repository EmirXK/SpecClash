package com.example.specclash.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The SpecClash delta engine.
 *
 * Given two raw spec values (e.g. `"4000 mAh"` vs `"3561 mAh"`) it produces a
 * structured [Comparison] describing the winner and a human-readable delta
 * string. Pure functions only — no I/O, no UI dependencies.
 */
object SpecComparator {

    /**
     * Higher-is-better metric: battery capacity, screen size, charging wattage,
     * refresh rate, peak brightness, RAM, etc.
     */
    private val higherIsBetterKeys: Set<String> = setOf(
        "battery", "screen", "size", "charging", "refresh rate", "peak brightness",
        "peak nits", "nits", "ram", "memory", "battery capacity", "battery size",
        "wattage", "watts", "fast charging", "wired charging", "wireless charging",
    )

    /**
     * Lower-is-better metric: weight, thickness, depth (in dimensions), process
     * node. The smaller the number, the better.
     */
    private val lowerIsBetterKeys: Set<String> = setOf(
        "weight", "thickness", "depth", "process", "process node", "nm",
        "dimension", "chipset", "price",
    )

    /**
     * Returns the winner of a single spec value pair.
     *
     * **Whitelist policy:** numeric delta badges are *only* computed for keys
     * listed in [ALLOWED_DELTA_KEYS] (case-insensitive substring match). All
     * other rows return a TIE with no delta badge so the UI renders clean
     * side-by-side text without broken subtraction numbers.
     */
    fun compare(
        rawA: String?,
        rawB: String?,
        specKey: String,
    ): Comparison {
        val k0 = specKey.lowercase()
        if (k0.contains("charg") || k0.contains("watt")) {
            if (!isAllowedDeltaKey(specKey)) {
                return Comparison(null, null, Winner.TIE, "")
            }
            return compareCharging(rawA, rawB)
        }

        val va = rawA?.let { parseNumeric(it, specKey) }
        val vb = rawB?.let { parseNumeric(it, specKey) }

        if (va == null || vb == null) {
            return Comparison(va, vb, Winner.TIE, "—")
        }
        if (abs(va - vb) < 0.0001) {
            return Comparison(va, vb, Winner.TIE, "Same")
        }

        // Whitelist enforcement: only the four whitelisted metric keys may
        // produce a numeric delta badge. Everything else renders as a
        // non-judgemental side-by-side text comparison.
        if (!isAllowedDeltaKey(specKey)) {
            return Comparison(va, vb, Winner.TIE, "")
        }

        val aBetter = when (direction(specKey)) {
            Direction.HIGHER -> va > vb
            Direction.LOWER -> va < vb
            Direction.UNKNOWN -> va > vb // default to "more is more"
        }
        val winner = if (aBetter) Winner.A else Winner.B
        val delta = formatDelta(va, vb, rawA, rawB, specKey)
        return Comparison(va, vb, winner, delta)
    }

    /**
     * True if the given spec key is allowed to produce a numeric delta badge.
     * Matching is case-insensitive substring on the lowercased key, e.g. both
     * "Weight" and "weight" match. "Battery Charging" would match "charging"
     * (which is intended).
     */
    fun isAllowedDeltaKey(specKey: String): Boolean {
        val k = specKey.lowercase()
        return ALLOWED_DELTA_KEYS.any { needle -> k.contains(needle) }
    }

    private fun direction(specKey: String): Direction {
        val k = specKey.lowercase()
        if (lowerIsBetterKeys.any { k.contains(it) }) return Direction.LOWER
        if (higherIsBetterKeys.any { k.contains(it) }) return Direction.HIGHER
        return Direction.UNKNOWN
    }

    /** Wired vs. wireless charging wattage parsed out of one raw "Charging" string. */
    private data class ChargingSpeeds(val wiredW: Double?, val wirelessW: Double?)

    /**
     * Compare two "Charging" spec strings without ever pitting a wired figure
     * against a wireless one - a phone that doesn't disclose its wired wattage
     * (e.g. Apple) must never be judged against another phone's wired number
     * using whichever wattage happened to be parsed first.
     */
    private fun compareCharging(rawA: String?, rawB: String?): Comparison {
        val a = extractChargingSpeeds(rawA)
        val b = extractChargingSpeeds(rawB)
        val (va, vb) = when {
            a.wiredW != null && b.wiredW != null -> a.wiredW to b.wiredW
            a.wirelessW != null && b.wirelessW != null -> a.wirelessW to b.wirelessW
            // No shared charging mode to compare (e.g. one side only
            // discloses wired, the other only wireless) - render a clean
            // side-by-side with no subtraction badge, same as any other
            // unparseable/non-comparable pair.
            else -> null to null
        }
        val displayA = a.wiredW ?: a.wirelessW
        val displayB = b.wiredW ?: b.wirelessW
        if (va == null || vb == null) {
            return Comparison(displayA, displayB, Winner.TIE, "—")
        }
        if (abs(va - vb) < 0.0001) {
            return Comparison(va, vb, Winner.TIE, "Same")
        }
        val winner = if (va > vb) Winner.A else Winner.B
        val w = formatInt(abs(va - vb))
        return Comparison(va, vb, winner, "+$w W faster")
    }

    /**
     * Splits a raw "Charging" string into wired/wireless wattages. GSMArena
     * strings look like "60W wired, PD3.0, 75% in 30 min, 25W wireless
     * (Qi2.2), 4.5W reverse wireless" or "Wired, PD3.2, AVS, 50% in 20 min,
     * 25W wireless MagSafe/Qi2, 50% in 30 min (15W - China), 4.5W reverse
     * wired". Reverse-charging clauses (the phone powering another device)
     * are excluded - they're not the phone's own charging spec.
     */
    private fun extractChargingSpeeds(raw: String?): ChargingSpeeds {
        if (raw.isNullOrBlank()) return ChargingSpeeds(null, null)
        var wiredW: Double? = null
        var wirelessW: Double? = null
        // A trailing regional-variant clause like "(15W - China)" continues
        // describing whichever mode the previous clause introduced, without
        // repeating the word "wireless" - so the current mode carries
        // forward across clauses instead of resetting at every comma.
        var mode = "wired" // GSMArena convention: wired charging is listed first
        for (clause in raw.split(",")) {
            val lower = clause.lowercase()
            if (lower.contains("reverse")) continue
            if (lower.contains("wireless")) mode = "wireless" else if (lower.contains("wired")) mode = "wired"
            val w = CHARGING_W.find(clause)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            if (mode == "wireless") {
                if (wirelessW == null) wirelessW = w
            } else if (wiredW == null) {
                wiredW = w
            }
        }
        return ChargingSpeeds(wiredW, wirelessW)
    }

    // --- Formatting --------------------------------------------------------

    private fun formatDelta(
        a: Double,
        b: Double,
        rawA: String?,
        rawB: String?,
        specKey: String,
    ): String {
        val k = specKey.lowercase()
        val diff = abs(a - b)
        // IMPORTANT: this text is only ever rendered on the WINNING side's
        // cell (see SpecRow: `delta = if (comparison.winner == Winner.A)
        // deltaText else null`, mirrored for B). So every branch below must
        // describe the winner's advantage unconditionally - it must never
        // branch on "is A the bigger raw number", because A isn't
        // necessarily the winner. A "lower is better" metric (price) or a
        // "higher is better" metric where B happens to win (e.g. B has the
        // faster charging) must still read as a win, not a loss.

        return when {
            k.contains("battery") || k.contains("mAh") -> {
                val pct = if (b > 0) ((a - b) / b) * 100.0 else 0.0
                val pctStr = String.format("%.1f", abs(pct))
                val mAhStr = formatInt(diff)
                "+$mAhStr mAh (+$pctStr%)"
            }
            k == "weight" || k.contains("weight") -> {
                val g = formatInt(diff)
                // Inverted polarity: the WINNER is the lighter device. The
                // delta text on the winner's row is always the positive form
                // ("lighter"), never the negative form ("heavier"), so a 177g
                // phone never gets a "2 g heavier" badge just because it's
                // slotted as A.
                "${g} g lighter"
            }
            k.contains("screen") || (k.contains("size") && rawA.orEmpty().contains("inch")) -> {
                val s = formatDecimal(diff)
                "+$s\" larger"
            }
            k.contains("thickness") || k.contains("depth") || k.contains("dimension") -> {
                val s = formatDecimal(diff)
                // Inverted polarity: the WINNER is the thinner device. The
                // delta text on the winner's row is always the positive form
                // ("thinner"), never the negative form ("thicker").
                "$s mm thinner"
            }
            k.contains("price") -> {
                val s = formatDecimal(diff)
                // `a`/`b` here are already USD-normalized (see parseNumeric's
                // "price" branch), so the delta is a real dollar amount.
                "\$$s lower"
            }
            else -> {
                val s = formatDecimal(diff)
                // Direction-aware, not A/B-aware: whichever side won did so
                // by satisfying `direction(specKey)`, so the label just
                // needs to match that direction.
                when (direction(specKey)) {
                    Direction.LOWER -> "$s lower"
                    Direction.HIGHER, Direction.UNKNOWN -> "+$s higher"
                }
            }
        }
    }

    private fun formatInt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)

    private fun formatDecimal(d: Double): String =
        String.format("%.2f", d).trimEnd('0').trimEnd('.')

    // --- Regexes -----------------------------------------------------------

    // (\d{3,5})\s*mAh  → "4000 mAh", "Li-Ion 4000 mAh"
    private val BATTERY_MAH = Regex("""(\d{3,5})\s*mAh""", RegexOption.IGNORE_CASE)

    // (\d{2,3})\s*g → "167 g", "167g"
    private val WEIGHT_G = Regex("""(\d{2,3})\s*g\b""", RegexOption.IGNORE_CASE)

    // (\d+(\.\d+)?)\s*inches → "6.2 inches"
    private val SCREEN_INCHES = Regex(
        """(\d+(?:\.\d+)?)\s*inches""",
        RegexOption.IGNORE_CASE
    )

    // x\s*(\d+(\.\d+)?)\s*mm  → matches "x 7.6 mm" inside a dimensions string
    private val THICKNESS_MM = Regex(
        """x\s*(\d+(?:\.\d+)?)\s*mm""",
        RegexOption.IGNORE_CASE
    )

    // (\d{2,3})W  → "25W", "45 W"
    private val CHARGING_W = Regex(
        """(\d{2,3})\s*W""",
        RegexOption.IGNORE_CASE
    )

    // (\d+)\s*nm  → "4 nm", "3nm"
    private val PROCESS_NM = Regex(
        """(\d+)\s*nm""",
        RegexOption.IGNORE_CASE
    )

    // First numeric value in a string (fallback).
    private val GENERIC_NUMBER = Regex("""(\d+(?:\.\d+)?)""")

    // --- Types -------------------------------------------------------------

    enum class Direction { HIGHER, LOWER, UNKNOWN }

    enum class Winner { A, B, TIE }

    data class Comparison(
        val valueA: Double?,
        val valueB: Double?,
        val winner: Winner,
        val deltaText: String,
    ) {
        val hasNumericValues: Boolean get() = valueA != null && valueB != null
    }

    /**
     * Strict allow-list of spec keys for which a numeric delta badge may be
     * rendered. **All other rows get `delta = null` and `Winner.TIE`**, so
     * the UI shows clean side-by-side text without subtraction badges
     * (e.g. no "+545 higher" on a GPU model name or "10 lower" on an OS).
     */
    val ALLOWED_DELTA_KEYS: Set<String> = setOf(
        "size",         // Screen size in inches (+0.4" larger)
        "weight",       // Weight in grams (2g lighter - LOWER IS BETTER)
        "charging",     // Fast charging in Watts (+20W faster)
        "price",        // Price in USD ($232 cheaper - LOWER IS BETTER)
    )

    /**
     * Lab-tested benchmark metrics scraped from a phone's `Our Tests` section
     * (Geekbench 6, AnTuTu v10, 3DMark, peak display brightness, active
     * battery life). All values are `null` when the source data is missing
     * or unparsed.
     */
    data class LabBenchmarks(
        val geekbenchV6: Double? = null,
        val antutuV10: Double? = null,
        /** 3DMark score, e.g. `6687` from "3DMark: 6687 (Wild Life Extreme)". */
        val threeDMarkScore: Double? = null,
        /** 3DMark suite name, e.g. "Wild Life Extreme". `null` if unparsed. */
        val threeDMarkSuite: String? = null,
        val measuredNits: Double? = null,
        val activeUseMinutes: Int? = null,
    ) {
        /** True when the spec sheet contains at least one lab benchmark. */
        val hasAny: Boolean
            get() = geekbenchV6 != null ||
                antutuV10 != null ||
                threeDMarkScore != null ||
                measuredNits != null ||
                activeUseMinutes != null
    }

    // ---------------------------------------------------------------------
    // Public output types for the Weighted Multi-Attribute Scoring Engine
    // ---------------------------------------------------------------------

    /** The five scored categories, in display order. */
    enum class Category(val displayName: String, val emoji: String) {
        DISPLAY("Display", "\uD83D\uDCF1"),
        CAMERA("Camera", "\uD83D\uDCF8"),
        PERFORMANCE("Performance", "\u26A1"),
        BATTERY("Battery", "\uD83D\uDD0B"),
        BUILD("Build", "\uD83E\uDEB6"),
        /** Price is metadata only - it does not affect the composite hardware score. */
        PRICE("Price", "\uD83D\uDCB0"),
    }

    /**
     * User-selectable scoring profiles. Each preset re-weights the five
     * hardware categories so the composite overall score reflects a
     * different priority (e.g. a gamer wants raw compute, a creator
     * wants camera versatility, a road warrior wants battery life).
     *
     * Weights must sum to 1.0.
     */
    enum class ScoringPreset(
        val title: String,
        val description: String,
        val displayWeight: Double,
        val cameraWeight: Double,
        val performanceWeight: Double,
        val batteryWeight: Double,
        val buildWeight: Double,
    ) {
        BALANCED(
            title = "Balanced (Default)",
            description = "Equal real-world emphasis across all specs",
            displayWeight = 0.25,
            cameraWeight = 0.25,
            performanceWeight = 0.20,
            batteryWeight = 0.20,
            buildWeight = 0.10,
        ),
        GAMER_PERFORMANCE(
            title = "Gamer / Performance",
            description = "Heavy focus on raw compute, SoC & high refresh rate",
            displayWeight = 0.35,
            cameraWeight = 0.10,
            performanceWeight = 0.40,
            batteryWeight = 0.10,
            buildWeight = 0.05,
        ),
        CAMERA_CREATOR(
            title = "Camera / Creator",
            description = "Prioritizes optical versatility, zoom, & display accuracy",
            displayWeight = 0.20,
            cameraWeight = 0.50,
            performanceWeight = 0.15,
            batteryWeight = 0.10,
            buildWeight = 0.05,
        ),
        BATTERY_ROAD_WARRIOR(
            title = "Battery / Endurance",
            description = "Prioritizes capacity, charging wattage & battery life",
            displayWeight = 0.15,
            cameraWeight = 0.10,
            performanceWeight = 0.15,
            batteryWeight = 0.50,
            buildWeight = 0.10,
        ),
        ;

        /** Returns the weight map for this preset (excludes [Category.PRICE]). */
        fun weightMap(): Map<Category, Double> = mapOf(
            Category.DISPLAY to displayWeight,
            Category.CAMERA to cameraWeight,
            Category.PERFORMANCE to performanceWeight,
            Category.BATTERY to batteryWeight,
            Category.BUILD to buildWeight,
        )
    }

    /** Per-category composite result, ready for the UI. */
    data class CategoryScore(
        val category: Category,
        val scoreA: Double,                 // 0..100
        val scoreB: Double,                 // 0..100
        val ratioText: String,              // e.g. "Galaxy S26 Ultra is 1.7x more powerful (A57 is 58% of Ultra)"
        val winner: Winner,
        val summaryA: String,               // e.g. "Snapdragon 8 Elite (3nm)"
        val summaryB: String,               // e.g. "Exynos 1580 (4nm)"
        val advantagesA: List<String> = emptyList(),
        val advantagesB: List<String> = emptyList(),
    ) {
        /** Larger of the two - convenient for the UI to render % bars. */
        val maxScore: Double get() = max(scoreA, scoreB)

        /** scoreA / scoreB clamped to (0, 4] - used for "1.7x" ratios. */
        val multiplierA: Double get() = if (scoreB > 0) scoreA / scoreB else 1.0

        /** scoreB / scoreA clamped to (0, 4]. */
        val multiplierB: Double get() = if (scoreA > 0) scoreB / scoreA else 1.0
    }

    /** Severity of the overall gap between two devices. */
    enum class WinnerBadge {
        /** Gap >= 15 pts on the composite score. */
        DECISIVE_HARDWARE_WINNER,
        /** 5 <= gap < 15 pts. */
        MARGINAL_LEAD,
        /** Gap < 5 pts. */
        CLOSE_MATCHUP,
    }

    /** Full comparison verdict composited from all categories. */
    data class FullComparisonVerdict(
        val nameA: String,
        val nameB: String,
        val display: CategoryScore,
        val camera: CategoryScore,
        val performance: CategoryScore,
        val battery: CategoryScore,
        val build: CategoryScore,
        val overallScoreA: Double,           // 0..100
        val overallScoreB: Double,           // 0..100
        val winner: Winner,
        val badge: WinnerBadge,
        val headline: String,                // e.g. "Decisive Win for Galaxy S25 FE"
        val summary: String,                 // 2-3 sentence trade-off summary
        val advantagesA: List<String>,       // top 3 bullets
        val advantagesB: List<String>,
        /** Value-for-money analysis. `null` if either device lacks a price. */
        val value: ValueAnalysis? = null,
        /** The scoring profile that produced this verdict (default = BALANCED). */
        val preset: ScoringPreset = ScoringPreset.BALANCED,
        /** True when the legacy-depreciation penalty is active. */
        val excludeLegacy: Boolean = false,
    )

    /** Parsed, currency-aware price for a single device. */
    data class PriceInfo(
        /** Price in USD (always set when [PriceInfo] is returned, may be derived via FX). */
        val amountUsd: Double,
        /** Price in EUR when extractable from the source string. */
        val amountEur: Double? = null,
        /** Price in GBP when extractable from the source string. */
        val amountGbp: Double? = null,
        /** Price in INR when extractable from the source string. */
        val amountInr: Double? = null,
        /** True if the source string used an approximation word like "About". */
        val isApproximate: Boolean = false,
        /** Original raw text after whitespace normalization. */
        val rawText: String,
        /** Compact, human-readable display string (e.g. "$689 / \u20AC770" or "~\u20AC100"). */
        val formattedDisplay: String,
    ) {
        val isUnpriced: Boolean get() = amountUsd <= 0.0
    }

    /** Outright vs. value-for-money analysis. */
    data class ValueAnalysis(
        val priceA: PriceInfo?,
        val priceB: PriceInfo?,
        /** Hardware score per $100 spent. Higher is better value. `null` if unpriced. */
        val valueScoreA: Double?,
        val valueScoreB: Double?,
        /** Outright hardware leader (ignoring price). */
        val outrightWinner: Winner,
        /** Value-for-money champion. `null` if either side is unpriced. */
        val valueWinner: Winner?,
        /** Multiplier `valueScoreA / valueScoreB` from A's perspective. */
        val valueRatioA: Double?,
        /** One-line plain-English explanation of the value gap (or null). */
        val valueAdvantageText: String?,
        /** Formatted ratio e.g. "2.1x" or "0.6x" - convenience for the UI. */
        val formattedRatio: String?,
    )

    /** Weights for the composite overall score (must sum to 1.0). */
    private val CATEGORY_WEIGHTS: Map<Category, Double> = mapOf(
        Category.DISPLAY to 0.25,
        Category.CAMERA to 0.25,
        Category.PERFORMANCE to 0.20,
        Category.BATTERY to 0.20,
        Category.BUILD to 0.10,
    )

    // --- Parsing -----------------------------------------------------------

    private fun parseNumeric(raw: String, specKey: String): Double? {
        val k = specKey.lowercase()

        // Price — delegate to the currency-aware extractor so a price row
        // is always normalized to USD before any subtraction/ratio math,
        // instead of grabbing whichever raw digit run appears first
        // regardless of currency (GENERIC_NUMBER below has no currency or
        // thousands-separator awareness).
        if (k.contains("price")) {
            return extractPrice(raw)?.amountUsd
        }

        // Battery capacity — e.g. "4000 mAh", "Li-Ion 4000 mAh"
        if (k.contains("battery") || k.contains("mAh")) {
            BATTERY_MAH.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        }

        // Weight — "167 g", "168 g (5.89 oz)"
        if (k == "weight" || k.contains("weight")) {
            WEIGHT_G.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        }

        // Screen size — "6.2 inches", "6.2 inches, 94.4 cm2"
        if (k.contains("screen") || k.contains("size") || k.contains("display")) {
            SCREEN_INCHES.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        }

        // Thickness / Dimensions — "147 x 70.6 x 7.6 mm" → capture the 3rd number.
        // The regex looks for "x <num> mm" so the last group wins by greedy matching.
        if (k.contains("thickness") || k.contains("depth") || k.contains("dimension")) {
            THICKNESS_MM.findAll(raw).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        }

        // Note: "charging"/"watt" keys never reach here — compare() routes
        // them to compareCharging() before calling parseNumeric, since a
        // charging spec needs wired-vs-wired/wireless-vs-wireless matching
        // rather than a single generic numeric value.

        // Process node — "4 nm", "3 nm". Even if the specKey doesn't say "nm"
        // or "chipset", we can detect the trailing "nm" in the raw value.
        if (PROCESS_NM.containsMatchIn(raw)) {
            PROCESS_NM.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        }

        // Generic refresh rate / peak brightness numeric value (e.g. "120Hz", "2600 nits")
        return GENERIC_NUMBER.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    // =====================================================================
    //  WEIGHTED MULTI-ATTRIBUTE SCORING ENGINE
    // =====================================================================
    //
    //  Public entry point: [buildVerdict].
    //
    //  Scoring flow:
    //    1. For each [Category], pull raw spec strings out of [PhoneSpec.specs]
    //       and compute a raw 0..100 subscore.
    //    2. Cross-apply category-level dealbreakers (e.g. 60Hz penalty).
    //    3. Build a [CategoryScore] with summary + ratio text.
    //    4. Composite overall = sum(score_i * weight_i), normalised to 0..100.
    //    5. Roll up a [FullComparisonVerdict] with headline, summary, badge
    //       and top-3 advantage bullets.
    // ---------------------------------------------------------------------

    /**
     * Build a [FullComparisonVerdict] for two fully-loaded [PhoneSpec]s.
     *
     * @param preset  Scoring profile that re-weights the five hardware
     *                categories. Defaults to [ScoringPreset.BALANCED] which
     *                preserves the legacy 25/25/20/20/10 weights.
     * @param excludeLegacy  When `true`, the value-for-money index applies
     *                a 25% penalty to phones whose chipset is >= 7 nm
     *                (heuristic for legacy / obsolete silicon). The
     *                outright hardware score is unchanged.
     */
    fun buildVerdict(
        specA: PhoneSpec,
        specB: PhoneSpec,
        preset: ScoringPreset = ScoringPreset.BALANCED,
        excludeLegacy: Boolean = false,
        /**
         * Manual user-entered street price for device A in USD. When
         * non-null, this overrides the price extracted from the upstream
         * spec sheet for the value-for-money calculation.
         */
        overridePriceAUsd: Double? = null,
        /** Manual user-entered street price for device B in USD. */
        overridePriceBUsd: Double? = null,
    ): FullComparisonVerdict {
        val display = scoreDisplay(specA, specB)
        val camera = scoreCamera(specA, specB)
        val performance = scorePerformance(specA, specB)
        val battery = scoreBattery(specA, specB)
        val build = scoreBuild(specA, specB)

        val weights = preset.weightMap()
        val overallA = compositeScore(
            mapOf(
                Category.DISPLAY to display.scoreA,
                Category.CAMERA to camera.scoreA,
                Category.PERFORMANCE to performance.scoreA,
                Category.BATTERY to battery.scoreA,
                Category.BUILD to build.scoreA,
            ),
            weights = weights,
        )
        val overallB = compositeScore(
            mapOf(
                Category.DISPLAY to display.scoreB,
                Category.CAMERA to camera.scoreB,
                Category.PERFORMANCE to performance.scoreB,
                Category.BATTERY to battery.scoreB,
                Category.BUILD to build.scoreB,
            ),
            weights = weights,
        )

        val overallWinner = when {
            overallA > overallB + 0.5 -> Winner.A
            overallB > overallA + 0.5 -> Winner.B
            else -> Winner.TIE
        }
        val gap = abs(overallA - overallB)
        val badge = when {
            gap >= 15.0 -> WinnerBadge.DECISIVE_HARDWARE_WINNER
            gap >= 5.0 -> WinnerBadge.MARGINAL_LEAD
            else -> WinnerBadge.CLOSE_MATCHUP
        }
        val winnerName = when (overallWinner) {
            Winner.A -> specA.name
            Winner.B -> specB.name
            Winner.TIE -> null
        }
        val headline = buildHeadline(winnerName, badge)
        val summary = buildSummary(
            winnerName = winnerName,
            specA = specA.name,
            specB = specB.name,
            display = display,
            camera = camera,
            performance = performance,
            battery = battery,
            build = build,
        )
        val advantagesA = collectAdvantages(
            specA.name,
            listOf(display, camera, performance, battery, build),
            forA = true,
        ) + featureAdvantageBullets(specA, specB, forA = true)
        val advantagesB = collectAdvantages(
            specB.name,
            listOf(display, camera, performance, battery, build),
            forA = false,
        ) + featureAdvantageBullets(specA, specB, forA = false)

        val value = buildValueAnalysis(
            specA = specA,
            specB = specB,
            overallA = overallA,
            overallB = overallB,
            outrightWinner = overallWinner,
            excludeLegacy = excludeLegacy,
            overrideUsdA = overridePriceAUsd,
            overrideUsdB = overridePriceBUsd,
        )

        return FullComparisonVerdict(
            nameA = specA.name,
            nameB = specB.name,
            display = display,
            camera = camera,
            performance = performance,
            battery = battery,
            build = build,
            overallScoreA = overallA,
            overallScoreB = overallB,
            winner = overallWinner,
            badge = badge,
            headline = headline,
            summary = summary,
            advantagesA = advantagesA,
            advantagesB = advantagesB,
            value = value,
            preset = preset,
            excludeLegacy = excludeLegacy,
        )
    }

    /**
     * A single device's slot in a 3-way comparison. The [rank] is 1-based
     * (1 = highest composite score). [score] is the composite 0..100.
     */
    data class ThreeWayEntry(
        val slot: Char,           // 'A', 'B', or 'C'
        val name: String,
        val score: Double,
        val rank: Int,
    )

    /**
     * Result of a 3-way comparison: the headline 2-way verdict (highest
     * vs lowest) plus the full sorted ranking.
     */
    data class ThreeWayVerdict(
        val headline: FullComparisonVerdict, // top vs bottom pairing
        val ranking: List<ThreeWayEntry>,    // sorted by score desc
        val preset: ScoringPreset = ScoringPreset.BALANCED,
    ) {
        val winner: SpecComparator.Winner
            get() = when (ranking.first().slot) {
                'A' -> SpecComparator.Winner.A
                'B' -> SpecComparator.Winner.B
                else -> SpecComparator.Winner.A // 'C' is a tie/stand-in
            }
    }

    /**
     * Build a 3-way comparison verdict. The composite scores for each
     * device are computed under [preset], then the top scorer is paired
     * against the bottom scorer to produce a 2-way [FullComparisonVerdict]
     * that the existing UI can render without changes.
     */
    fun buildThreeWayVerdict(
        specA: PhoneSpec,
        specB: PhoneSpec,
        specC: PhoneSpec,
        preset: ScoringPreset = ScoringPreset.BALANCED,
        excludeLegacy: Boolean = false,
    ): ThreeWayVerdict {
        val scores = listOf(
            'A' to computeOverall(specA, preset, excludeLegacy),
            'B' to computeOverall(specB, preset, excludeLegacy),
            'C' to computeOverall(specC, preset, excludeLegacy),
        )
        val ranking = scores
            .sortedByDescending { it.second }
            .mapIndexed { idx, (slot, score) ->
                ThreeWayEntry(
                    slot = slot,
                    name = when (slot) {
                        'A' -> specA.name
                        'B' -> specB.name
                        else -> specC.name
                    },
                    score = score,
                    rank = idx + 1,
                )
            }
        val top = ranking[0]
        val bottom = ranking[2]
        val headline = when {
            top.slot == 'A' && bottom.slot == 'B' -> buildVerdict(specA, specB, preset, excludeLegacy)
            top.slot == 'A' && bottom.slot == 'C' -> buildVerdict(specA, specC, preset, excludeLegacy)
            top.slot == 'B' && bottom.slot == 'A' -> buildVerdict(specB, specA, preset, excludeLegacy)
            top.slot == 'B' && bottom.slot == 'C' -> buildVerdict(specB, specC, preset, excludeLegacy)
            top.slot == 'C' && bottom.slot == 'A' -> buildVerdict(specC, specA, preset, excludeLegacy)
            else -> buildVerdict(specC, specB, preset, excludeLegacy)
        }
        return ThreeWayVerdict(
            headline = headline,
            ranking = ranking,
            preset = preset,
        )
    }

    /**
     * Compute a device's overall composite score without pairing it against
     * a second device. Mirrors the math inside [buildVerdict].
     */
    private fun computeOverall(
        spec: PhoneSpec,
        preset: ScoringPreset,
        excludeLegacy: Boolean,
    ): Double {
        val d = scoreDisplay(spec, spec)
        val c = scoreCamera(spec, spec)
        val p = scorePerformance(spec, spec)
        val b = scoreBattery(spec, spec)
        val bd = scoreBuild(spec, spec)
        val weights = preset.weightMap()
        return compositeScore(
            mapOf(
                Category.DISPLAY to d.scoreA,
                Category.CAMERA to c.scoreA,
                Category.PERFORMANCE to p.scoreA,
                Category.BATTERY to b.scoreA,
                Category.BUILD to bd.scoreA,
            ),
            weights = weights,
        )
    }

    // ---------------------------------------------------------------------
    //  Chipset / Silicon Tiering (PERFORMANCE)
    // ---------------------------------------------------------------------

    /** Tiered chipset lookup: chipset name (lowercased substrings) -> base score. */
    private val CHIPSET_TIER: List<Pair<List<String>, IntRange>> = listOf(
        // Tier 1: Flagship (90-100)
        listOf("a18 pro") to 100..100,
        listOf("a18") to 97..97,
        listOf("a17 pro") to 94..94,
        listOf("8 elite") to 98..98,
        listOf("8 gen 3") to 95..95,
        listOf("dimensity 9400") to 95..95,
        listOf("dimensity 9300") to 92..92,
        // Tier 2: Upper Midrange (75-89)
        // NOTE: "exynos 2400e" must be listed before the shorter "exynos
        // 2400" — lookup matches on first substring hit, and "exynos 2400"
        // is itself a substring of "exynos 2400e".
        listOf("exynos 2400e") to 85..85,
        listOf("exynos 2400") to 88..88,
        listOf("8s gen 3") to 82..82,
        listOf("a16") to 80..80,
        listOf("a15") to 76..76,
        listOf("dimensity 8300") to 78..78,
        // Tier 3: Midrange (50-74)
        listOf("exynos 1580") to 58..58,
        listOf("exynos 1480") to 55..55,
        listOf("exynos 1380") to 50..50,
        listOf("7+ gen 3") to 65..65,
        listOf("7s gen 2") to 55..55,
        listOf("snapdragon 778g") to 52..52,
        listOf("dimensity 7200") to 58..58,
        // Tier 4: Entry (25-49)
        listOf("helio g99") to 38..38,
        listOf("helio g96") to 32..32,
        listOf("snapdragon 680") to 30..30,
        listOf("4 gen 2") to 36..36,
        listOf("dimensity 6100") to 34..34,
    )

    private fun scorePerformance(specA: PhoneSpec, specB: PhoneSpec): CategoryScore {
        val platformA = specA.specs["Platform"].orEmpty()
        val platformB = specB.specs["Platform"].orEmpty()
        val chipsetA = platformA["Chipset"].orEmpty()
        val chipsetB = platformB["Chipset"].orEmpty()
        val nmA = extractNm(chipsetA)
        val nmB = extractNm(chipsetB)
        val ramA = extractRamGb(specA.specs["Memory"].orEmpty()["Internal"].orEmpty())
        val ramB = extractRamGb(specB.specs["Memory"].orEmpty()["Internal"].orEmpty())

        // 1) Lab benchmark primary path: when both devices have Geekbench 6
        //    (or AnTuTu v10) numbers from `Our Tests`, prefer those for
        //    the ratio + summaries. Fall back to the chipset tier dictionary
        //    when either side is unreviewed.
        val labA = extractLabBenchmarks(specA.specs)
        val labB = extractLabBenchmarks(specB.specs)

        val (gbA, gbB) = labA.geekbenchV6 to labB.geekbenchV6
        val (antutuA, antutuB) = labA.antutuV10 to labB.antutuV10

        val scoreA: Double
        val scoreB: Double
        val summaryA: String
        val summaryB: String

        when {
            gbA != null && gbB != null -> {
                // Lab-benchmark mode (Geekbench 6 multi-core).
                scoreA = clamp01_100(geekbench6ToScore(gbA))
                scoreB = clamp01_100(geekbench6ToScore(gbB))
                val chipsetNameA = chipsetLabel(chipsetA, nmA, ramA ?: 0.0)
                val chipsetNameB = chipsetLabel(chipsetB, nmB, ramB ?: 0.0)
                summaryA = formatLabSummary(chipsetNameA, "Geekbench 6", gbA)
                summaryB = formatLabSummary(chipsetNameB, "Geekbench 6", gbB)
            }
            antutuA != null && antutuB != null -> {
                // AnTuTu v10 fallback.
                scoreA = clamp01_100(antutu10ToScore(antutuA))
                scoreB = clamp01_100(antutu10ToScore(antutuB))
                val chipsetNameA = chipsetLabel(chipsetA, nmA, ramA ?: 0.0)
                val chipsetNameB = chipsetLabel(chipsetB, nmB, ramB ?: 0.0)
                summaryA = formatLabSummary(chipsetNameA, "AnTuTu v10", antutuA)
                summaryB = formatLabSummary(chipsetNameB, "AnTuTu v10", antutuB)
            }
            else -> {
                // Chipset architecture fallback (one or both unreviewed).
                val (rawA, nameA) = chipsetScore(chipsetA, nmA, ramA ?: 0.0)
                val (rawB, nameB) = chipsetScore(chipsetB, nmB, ramB ?: 0.0)
                scoreA = clamp01_100(rawA)
                scoreB = clamp01_100(rawB)
                summaryA = nameA
                summaryB = nameB
            }
        }

        val (winner, _) = ratioWinner(scoreA, scoreB, "powerful", "powerful")
        return CategoryScore(
            category = Category.PERFORMANCE,
            scoreA = scoreA,
            scoreB = scoreB,
            ratioText = ratioTextFor(
                specA.name, specB.name, scoreA, scoreB, winner, "powerful", "more powerful",
            ),
            winner = winner,
            summaryA = summaryA,
            summaryB = summaryB,
        )
    }

    /**
     * Resolve a chipset's display label (used when lab benchmarks override
     * the score but we still want to show the SoC name alongside the metric).
     */
    private fun chipsetLabel(
        chipsetRaw: String,
        nm: Double?,
        ramGb: Double,
    ): String {
        if (chipsetRaw.isNotEmpty()) {
            val lower = chipsetRaw.lowercase()
            for ((needles, range) in CHIPSET_TIER) {
                if (needles.all { needle -> lower.contains(needle) }) {
                    return chipsetRaw
                }
            }
        }
        // Fallback label when chipset isn't recognized.
        val nmPart = nm?.let { "${it.toInt()}nm" } ?: "?"
        val ramPart = if (ramGb > 0) " + ${ramGb.toInt()}GB RAM" else ""
        return if (chipsetRaw.isNotEmpty()) "$chipsetRaw ($nmPart)" else "Unknown SoC ($nmPart)$ramPart"
    }

    /** Format a one-line performance summary that embeds the lab metric. */
    private fun formatLabSummary(
        chipset: String,
        metricLabel: String,
        rawScore: Double,
    ): String {
        return "$chipset \u00B7 $metricLabel ${formatLabScoreNumber(rawScore)}"
    }

    /** Formats a lab-benchmark score with thousands separators, e.g. "9,846". */
    fun formatLabScoreNumber(value: Double): String =
        if (value >= 1000.0) String.format("%,.0f", value) else String.format("%.0f", value)

    /**
     * Resolve a chipset's score: tier table first; otherwise derive from the
     * fabrication node and RAM amount.
     */
    private fun chipsetScore(chipsetRaw: String, nm: Double?, ramGb: Double): Pair<Double, String> {
        val chipset = chipsetRaw.trim()
        if (chipset.isNotEmpty()) {
            val lower = chipset.lowercase()
            for ((needles, range) in CHIPSET_TIER) {
                if (needles.all { needle -> lower.contains(needle) }) {
                    return midpoint(range).toDouble() to chipset
                }
            }
        }
        // Fallback: nm + RAM
        val nmScore = when (nm) {
            null -> 50.0
            in 0.0..3.5 -> 90.0
            in 3.5..4.5 -> 80.0
            in 4.5..5.5 -> 70.0
            in 5.5..6.5 -> 60.0
            else -> 40.0
        }
        val ramBonus = when {
            ramGb >= 16 -> 10.0
            ramGb >= 12 -> 6.0
            ramGb >= 8 -> 3.0
            else -> 0.0
        }
        val score = clamp01_100(nmScore + ramBonus)
        val label = if (chipset.isNotEmpty()) chipset else "Unknown SoC"
        return score to label
    }

    private fun midpoint(range: IntRange): Int = (range.first + range.last) / 2

    /** Extract a "N nm" fabrication number from a chipset string. */
    private fun extractNm(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        return PROCESS_NM.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** Extract RAM in GB from strings like "256GB 8GB RAM" or "128GB 12GB". */
    private fun extractRamGb(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val labelled = Regex("""(\d{1,3})\s*GB\s*RAM""", RegexOption.IGNORE_CASE).find(text)
        if (labelled != null) return labelled.groupValues[1].toDoubleOrNull()
        val all = Regex("""(\d{1,3})\s*GB""", RegexOption.IGNORE_CASE).findAll(text).toList()
        return all.lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()
    }

    // ---------------------------------------------------------------------
    //  Camera Versatility Index (CAMERA)
    // ---------------------------------------------------------------------

    private fun scoreCamera(specA: PhoneSpec, specB: PhoneSpec): CategoryScore {
        val mainA = specA.specs["Main Camera"].orEmpty()
        val mainB = specB.specs["Main Camera"].orEmpty()
        val featuresA = mainA["Features"].orEmpty()
        val featuresB = mainB["Features"].orEmpty()
        val videoA = mainA["Video"].orEmpty()
        val videoB = mainB["Video"].orEmpty()
        val hayA = mainA.entries.joinToString(" | ") { it.value }
        val hayB = mainB.entries.joinToString(" | ") { it.value }

        val (subA, lensA) = cameraSubspec(mainA)
        val (subB, lensB) = cameraSubspec(mainB)
        val oisA = hasOis(featuresA) || hasOis(videoA) || hasOis(hayA)
        val oisB = hasOis(featuresB) || hasOis(videoB) || hasOis(hayB)
        val sensorA = hasLargeSensor(featuresA) || hasLargeSensor(videoA)
        val sensorB = hasLargeSensor(featuresB) || hasLargeSensor(videoB)
        val teleA = hasTelephoto(mainA) || hasTelephoto(featuresA)
        val teleB = hasTelephoto(mainB) || hasTelephoto(featuresB)
        val ultraA = hasUltrawide(mainA) || hasUltrawide(featuresA)
        val ultraB = hasUltrawide(mainB) || hasUltrawide(featuresB)
        val videoCapA = hasHighEndVideo(videoA) || hasHighEndVideo(featuresA)
        val videoCapB = hasHighEndVideo(videoB) || hasHighEndVideo(featuresB)

        // --- Selfie camera bonuses (+5 each, up to +10 total) ---
        val selfieA = selfieMap(specA)
        val selfieB = selfieMap(specB)
        val selfieVideoA = selfieA["Video"].orEmpty()
        val selfieVideoB = selfieB["Video"].orEmpty()
        val selfieMpA = selfieMegapixels(selfieA)
        val selfieMpB = selfieMegapixels(selfieB)
        val selfieHayA = selfieA.entries.joinToString(" | ") { it.value }
        val selfieHayB = selfieB.entries.joinToString(" | ") { it.value }
        val selfie4KA = hasHighEndVideo(selfieVideoA) || hasHighEndVideo(selfieHayA)
        val selfie4KB = hasHighEndVideo(selfieVideoB) || hasHighEndVideo(selfieHayB)
        val selfieHiresPDAFA = selfieMpA >= 12.0 &&
            (selfieHayA.contains("PDAF", ignoreCase = true) ||
                selfieHayA.contains("phase detection", ignoreCase = true))
        val selfieHiresPDAFB = selfieMpB >= 12.0 &&
            (selfieHayB.contains("PDAF", ignoreCase = true) ||
                selfieHayB.contains("phase detection", ignoreCase = true))

        var sA = cameraRawScore(subA, oisA, sensorA, teleA, ultraA, videoCapA)
        var sB = cameraRawScore(subB, oisB, sensorB, teleB, ultraB, videoCapB)
        if (selfie4KA) sA += 5.0
        if (selfie4KB) sB += 5.0
        if (selfieHiresPDAFA) sA += 5.0
        if (selfieHiresPDAFB) sB += 5.0

        val scoreA = clamp01_100(sA)
        val scoreB = clamp01_100(sB)
        val (winner, _) = ratioWinner(scoreA, scoreB, "versatile", "versatile")
        return CategoryScore(
            category = Category.CAMERA,
            scoreA = scoreA,
            scoreB = scoreB,
            ratioText = ratioTextFor(specA.name, specB.name, scoreA, scoreB, winner, "versatile", "more versatile"),
            winner = winner,
            summaryA = cameraSummary(lensA, teleA, ultraA, oisA),
            summaryB = cameraSummary(lensB, teleB, ultraB, oisB),
        )
    }

    /**
     * GSMArena exposes the selfie section under a few different keys
     * depending on the proxy revision. Resolve all known variants.
     */
    private fun selfieMap(spec: PhoneSpec): Map<String, String> {
        val specs = spec.specs
        return specs["Selfie camera"]
            ?: specs["Selfie Camera"]
            ?: specs["SelfieCamera"]
            ?: emptyMap()
    }

    /** Extracts the selfie-camera megapixel count from a "Single" or "Dual" row. */
    private fun selfieMegapixels(selfie: Map<String, String>): Double {
        val source = selfie["Single"] ?: selfie["Dual"] ?: selfie["Modules"] ?: ""
        val match = Regex("""(\d+(?:\.\d+)?)\s*MP""", RegexOption.IGNORE_CASE).find(source)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    /** Sub-spec like "Triple" or "Single" + a count. */
    private fun cameraSubspec(main: Map<String, String>): Pair<Int, String> {
        // First, the value text (may contain e.g. "Triple camera" or "48 MP, ...")
        val sub = main["Triple"] ?: main["Quad"] ?: main["Dual"] ?: main["Single"] ?: main["Modules"] ?: ""
        val lower = sub.lowercase()
        // Count also from the key set: presence of "Triple" key alone signals 3 lenses.
        val hasTripleKey = main.containsKey("Triple")
        val hasQuadKey = main.containsKey("Quad")
        val hasDualKey = main.containsKey("Dual")
        val hasSingleKey = main.containsKey("Single")
        val count = when {
            lower.contains("quad") || hasQuadKey -> 4
            lower.contains("triple") || hasTripleKey -> 3
            lower.contains("dual") || hasDualKey -> 2
            lower.contains("single") || hasSingleKey -> 1
            else -> 0
        }
        val label = when (count) {
            4 -> "Quad camera"
            3 -> "Triple camera"
            2 -> "Dual camera"
            1 -> "Single camera"
            else -> sub.ifBlank { "Unknown camera" }
        }
        return count to label
    }

    private fun cameraRawScore(
        lensCount: Int,
        ois: Boolean,
        bigSensor: Boolean,
        tele: Boolean,
        ultra: Boolean,
        video: Boolean,
    ): Double {
        var score = 0.0
        // Base main camera: 35 pts (single-cam), +10 OIS, +5 big sensor.
        val baseMain = if (lensCount >= 1) 35.0 else 0.0
        score += baseMain
        if (ois) score += 10.0
        if (bigSensor) score += 5.0
        // Dedicated optical zoom
        if (tele) score += 30.0
        // Ultrawide
        if (ultra) score += 20.0
        // Video capability
        if (video) score += 5.0
        // Single-lens penalty: cap at 45
        if (lensCount <= 1) score = min(score, 45.0)
        return score
    }

    private fun cameraSummary(lens: String, tele: Boolean, ultra: Boolean, ois: Boolean): String {
        val parts = mutableListOf(lens)
        if (ois) parts.add("OIS")
        if (tele) parts.add("telephoto")
        if (ultra) parts.add("ultrawide")
        return parts.joinToString(" + ")
    }

    private fun hasOis(text: String): Boolean =
        text.contains("OIS", ignoreCase = true) ||
            text.contains("optical image", ignoreCase = true)

    private fun hasLargeSensor(text: String): Boolean {
        // Sensor size >= 1/1.5"
        val match = Regex("""1\s*/\s*([\d.]+)\s*['"]""", RegexOption.IGNORE_CASE).find(text)
        val n = match?.groupValues?.get(1)?.toDoubleOrNull() ?: return false
        return n <= 1.5
    }

    private fun hasTelephoto(text: Map<String, String>): Boolean {
        if (text.isEmpty()) return false
        val hay = text.entries.joinToString(" ") { it.value }.lowercase()
        return hay.contains("telephoto") ||
            hay.contains("periscope") ||
            Regex("""\d+\s*x\s*optical""").containsMatchIn(hay) ||
            hay.contains("optical zoom")
    }

    /** String overload so callers can pass a raw spec value as well. */
    private fun hasTelephoto(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return lower.contains("telephoto") ||
            lower.contains("periscope") ||
            Regex("""\d+\s*x\s*optical""").containsMatchIn(lower) ||
            lower.contains("optical zoom")
    }

    private fun hasUltrawide(text: Map<String, String>): Boolean {
        if (text.isEmpty()) return false
        val hay = text.entries.joinToString(" ") { it.value }.lowercase()
        return hay.contains("ultrawide") ||
            hay.contains("ultra wide") ||
            hay.contains("ultra-wide")
    }

    /** String overload so callers can pass a raw spec value as well. */
    private fun hasUltrawide(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return lower.contains("ultrawide") ||
            lower.contains("ultra wide") ||
            lower.contains("ultra-wide")
    }

    private fun hasHighEndVideo(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("8k") ||
            Regex("""4k\s*@\s*60""").containsMatchIn(lower) ||
            Regex("""4k\s*@\s*120""").containsMatchIn(lower)
    }

    // ---------------------------------------------------------------------
    //  Display Fluidity & Brightness (DISPLAY)
    // ---------------------------------------------------------------------

    private fun scoreDisplay(specA: PhoneSpec, specB: PhoneSpec): CategoryScore {
        val dispA = specA.specs["Display"].orEmpty()
        val dispB = specB.specs["Display"].orEmpty()
        val rawA = displayRawScore(dispA)
        val rawB = displayRawScore(dispB)
        val hzA = displayRefreshHz(dispA)
        val hzB = displayRefreshHz(dispB)
        val (scoreA, scoreB) = apply60HzPenalty(rawA, rawB, hzA, hzB)

        val (winner, _) = ratioWinner(scoreA, scoreB, "fluid", "fluid")
        return CategoryScore(
            category = Category.DISPLAY,
            scoreA = scoreA,
            scoreB = scoreB,
            ratioText = ratioTextFor(specA.name, specB.name, scoreA, scoreB, winner, "fluid", "smoother display"),
            winner = winner,
            summaryA = displaySummary(dispA, hzA),
            summaryB = displaySummary(dispB, hzB),
        )
    }

    private fun displayRawScore(display: Map<String, String>): Double {
        // Refresh rate
        val typeRaw = display["Type"].orEmpty() + " " + display.entries.joinToString(" ") { it.value }
        val hz = displayRefreshHz(display)
        val refreshPts = when {
            hz >= 120.0 -> 50.0
            hz >= 90.0 -> 35.0
            hz > 0.0 -> 15.0
            else -> 0.0
        }
        // Panel quality
        val ltpo = typeRaw.contains("LTPO", ignoreCase = true)
        val oled = typeRaw.contains("OLED", ignoreCase = true) ||
            typeRaw.contains("AMOLED", ignoreCase = true) ||
            typeRaw.contains("Super AMOLED", ignoreCase = true) ||
            typeRaw.contains("Dynamic AMOLED", ignoreCase = true)
        val ips = typeRaw.contains("IPS", ignoreCase = true) ||
            typeRaw.contains("LCD", ignoreCase = true) ||
            typeRaw.contains("PLS", ignoreCase = true)
        val panelPts = when {
            ltpo || oled -> 25.0
            ips -> 10.0
            else -> 0.0
        }
        // Peak brightness
        val nits = displayPeakNits(display)
        val brightnessPts = when {
            nits >= 2500 -> 25.0
            nits >= 1500 -> 20.0
            nits >= 1000 -> 15.0
            nits > 0.0 -> 8.0
            else -> 0.0
        }
        return refreshPts + panelPts + brightnessPts
    }

    private fun displayRefreshHz(display: Map<String, String>): Double {
        val text = display.entries.joinToString(" ") { it.value }
        val match = Regex("""(\d{2,3})\s*Hz""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun displayPeakNits(display: Map<String, String>): Double {
        val text = display.entries.joinToString(" ") { it.value }
        val match = Regex("""(\d{3,4})\s*nits?""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun displaySummary(display: Map<String, String>, hz: Double): String {
        val typeText = display["Type"].orEmpty()
        val shortType = when {
            typeText.contains("LTPO", ignoreCase = true) -> "LTPO"
            typeText.contains("Dynamic AMOLED", ignoreCase = true) -> "Dynamic AMOLED"
            typeText.contains("Super AMOLED", ignoreCase = true) -> "Super AMOLED"
            typeText.contains("AMOLED", ignoreCase = true) -> "AMOLED"
            typeText.contains("OLED", ignoreCase = true) -> "OLED"
            typeText.contains("IPS", ignoreCase = true) -> "IPS LCD"
            typeText.contains("LCD", ignoreCase = true) -> "LCD"
            else -> typeText.ifBlank { "Unknown panel" }
        }
        return if (hz > 0) "$shortType ${hz.toInt()}Hz" else shortType
    }

    /** The 60Hz penalty: if one phone is >=120Hz and the other is 60Hz, dock the 60Hz one by 30%. */
    private fun apply60HzPenalty(
        rawA: Double,
        rawB: Double,
        hzA: Double,
        hzB: Double,
    ): Pair<Double, Double> {
        val aIsHigh = hzA >= 120.0
        val bIsHigh = hzB >= 120.0
        val aIsLow = hzA in 1.0..75.0
        val bIsLow = hzB in 1.0..75.0
        return when {
            aIsHigh && bIsLow -> Pair(rawA, rawB * 0.7)
            bIsHigh && aIsLow -> Pair(rawA * 0.7, rawB)
            else -> Pair(rawA, rawB)
        }
    }

    // ---------------------------------------------------------------------
    //  Battery & Charging (BATTERY)
    // ---------------------------------------------------------------------

    private fun scoreBattery(specA: PhoneSpec, specB: PhoneSpec): CategoryScore {
        val batA = specA.specs["Battery"].orEmpty()
        val batB = specB.specs["Battery"].orEmpty()
        val sA = batteryRawScore(batA)
        val sB = batteryRawScore(batB)
        val scoreA = clamp01_100(sA)
        val scoreB = clamp01_100(sB)
        val (winner, _) = ratioWinner(scoreA, scoreB, "long-lasting", "long-lasting")
        return CategoryScore(
            category = Category.BATTERY,
            scoreA = scoreA,
            scoreB = scoreB,
            ratioText = ratioTextFor(specA.name, specB.name, scoreA, scoreB, winner, "long-lasting", "longer battery life"),
            winner = winner,
            summaryA = batterySummary(batA),
            summaryB = batterySummary(batB),
        )
    }

    /**
     * Capacity (65% weight) + Charging (35% weight) with diminishing returns.
     */
    private fun batteryRawScore(battery: Map<String, String>): Double {
        val typeRaw = battery["Type"].orEmpty()
        val chargingRaw = battery["Charging"].orEmpty()
        val hay = battery.entries.joinToString(" ") { it.value }
        val mAh = extractMah(typeRaw) ?: extractMah(chargingRaw) ?: extractMah(hay)
        val watts = extractChargingWatts(chargingRaw)
        val capacityPts = when {
            mAh == null -> 30.0
            mAh >= 6000 -> 65.0
            mAh >= 5000 -> 55.0
            mAh >= 4000 -> 45.0
            mAh >= 3000 -> 30.0
            else -> linearScale(mAh, 1500.0, 3000.0, 10.0, 30.0)
        }
        val chargingPts = when {
            watts == null -> 15.0
            watts >= 65.0 -> 35.0
            watts >= 45.0 -> 32.0
            watts >= 25.0 -> 25.0
            watts >= 20.0 -> 15.0
            else -> 10.0
        }
        return capacityPts * 0.65 + chargingPts * 0.35
    }

    private fun batterySummary(battery: Map<String, String>): String {
        val typeRaw = battery["Type"].orEmpty()
        val chargingRaw = battery["Charging"].orEmpty()
        val mAh = extractMah(typeRaw)
        val watts = extractChargingWatts(chargingRaw)
        val parts = mutableListOf<String>()
        if (mAh != null) parts.add("${mAh.toInt()} mAh")
        if (watts != null) parts.add("${watts.toInt()}W")
        return if (parts.isEmpty()) "Unknown battery" else parts.joinToString(" + ")
    }

    private fun extractMah(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        return BATTERY_MAH.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractChargingWatts(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""(\d{2,3})\s*W""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    // ---------------------------------------------------------------------
    //  Ergonomics & Build (BUILD)
    // ---------------------------------------------------------------------

    private fun scoreBuild(specA: PhoneSpec, specB: PhoneSpec): CategoryScore {
        val bodyA = specA.specs["Body"].orEmpty()
        val bodyB = specB.specs["Body"].orEmpty()
        val usbA = commsUsbValue(specA)
        val usbB = commsUsbValue(specB)
        var sA = buildRawScore(bodyA)
        var sB = buildRawScore(bodyB)
        // +3 pts for fast USB: Type-C 3.x or DisplayPort alt-mode (desktop mode).
        if (hasFastUsb(usbA)) sA += 3.0
        if (hasFastUsb(usbB)) sB += 3.0
        val scoreA = clamp01_100(sA)
        val scoreB = clamp01_100(sB)
        val (winner, _) = ratioWinner(scoreA, scoreB, "portable", "portable")
        return CategoryScore(
            category = Category.BUILD,
            scoreA = scoreA,
            scoreB = scoreB,
            ratioText = ratioTextFor(specA.name, specB.name, scoreA, scoreB, winner, "portable", "more portable"),
            winner = winner,
            summaryA = buildSummary(bodyA),
            summaryB = buildSummary(bodyB),
        )
    }

    /** Returns the "Comms → USB" string for a spec (empty if missing). */
    private fun commsUsbValue(spec: PhoneSpec): String {
        val comms = spec.specs["Comms"] ?: return ""
        return comms["USB"] ?: comms["Usb"] ?: ""
    }

    /** True if the USB string advertises a high-speed variant. */
    private fun hasFastUsb(usb: String): Boolean {
        if (usb.isBlank()) return false
        val lower = usb.lowercase()
        if (lower.contains("type-c 3") || lower.contains("type c 3") || lower.contains("usb 3")) return true
        if (lower.contains("displayport") || lower.contains("alt mode") || lower.contains("thunderbolt")) return true
        return false
    }

    private fun buildRawScore(body: Map<String, String>): Double {
        val weight = extractWeightG(body["Weight"])
        val thickness = extractThicknessMm(body["Dimensions"])
        val ip = extractIpRating(body.entries.joinToString(" ") { it.value })

        // Weight: < 180g ideal, 250g bottom
        val weightPts = when {
            weight == null -> 30.0
            weight < 170 -> 45.0
            weight < 180 -> 42.0
            weight < 200 -> 36.0
            weight < 220 -> 28.0
            weight < 240 -> 20.0
            else -> 12.0
        }
        // Thickness: < 8mm ideal
        val thicknessPts = when {
            thickness == null -> 25.0
            thickness < 7.5 -> 30.0
            thickness < 8.0 -> 27.0
            thickness < 8.5 -> 22.0
            thickness < 9.0 -> 17.0
            thickness < 9.5 -> 12.0
            else -> 8.0
        }
        // IP
        val ipPts = when (ip) {
            "IP68", "IP69", "IP69K" -> 15.0
            "IP67" -> 10.0
            "IP54", "IP55", "IP56" -> 5.0
            else -> 0.0
        }
        return weightPts + thicknessPts + ipPts
    }

    private fun buildSummary(body: Map<String, String>): String {
        val weight = extractWeightG(body["Weight"])
        val thickness = extractThicknessMm(body["Dimensions"])
        val ip = extractIpRating(body.entries.joinToString(" ") { it.value })
        val parts = mutableListOf<String>()
        if (weight != null) parts.add("${weight.toInt()}g")
        if (thickness != null) parts.add("${"%.1f".format(thickness)}mm")
        if (ip != null) parts.add(ip)
        return if (parts.isEmpty()) "Unknown build" else parts.joinToString(" + ")
    }

    private fun extractWeightG(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        return Regex("""(\d{2,3})\s*g\b""", RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractThicknessMm(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        // Dimensions: "147 x 70.6 x 7.6 mm" -> take the last "x N mm".
        val all = Regex("""x\s*(\d+(?:\.\d+)?)\s*mm""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()
        return if (all.isNotEmpty()) all.last() else null
    }

    private fun extractIpRating(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""IP\s*(\d{2})""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val first = match.groupValues[1].substring(0, 1).toIntOrNull() ?: return null
        val second = match.groupValues[1].substring(1, 2).toIntOrNull() ?: return null
        return "IP$first$second"
    }

    // ---------------------------------------------------------------------
    //  Score composition, ratio text, headlines, advantage bullets
    // ---------------------------------------------------------------------

    private fun compositeScore(
        byCategory: Map<Category, Double>,
        weights: Map<Category, Double> = CATEGORY_WEIGHTS,
    ): Double {
        if (byCategory.isEmpty()) return 0.0
        var sum = 0.0
        var weight = 0.0
        for ((cat, score) in byCategory) {
            val w = weights[cat] ?: continue
            sum += score * w
            weight += w
        }
        if (weight == 0.0) return 0.0
        return clamp01_100(sum / weight)
    }

    private fun clamp01_100(v: Double): Double = max(0.0, min(100.0, v))

    private fun linearScale(
        value: Double,
        inMin: Double,
        inMax: Double,
        outMin: Double,
        outMax: Double,
    ): Double {
        if (inMax == inMin) return outMin
        val t = ((value - inMin) / (inMax - inMin)).coerceIn(0.0, 1.0)
        return outMin + t * (outMax - outMin)
    }

    /** Decide winner by a small epsilon so a perfect tie stays a tie. */
    private fun ratioWinner(
        scoreA: Double,
        scoreB: Double,
        @Suppress("UNUSED_PARAMETER") labelA: String,
        @Suppress("UNUSED_PARAMETER") labelB: String,
    ): Pair<Winner, Double> {
        val winner = when {
            scoreA > scoreB + 0.5 -> Winner.A
            scoreB > scoreA + 0.5 -> Winner.B
            else -> Winner.TIE
        }
        val ratio = if (scoreB == 0.0) 1.0 else scoreA / scoreB
        return winner to ratio
    }

    private fun ratioTextFor(
        nameA: String,
        nameB: String,
        scoreA: Double,
        scoreB: Double,
        winner: Winner,
        @Suppress("UNUSED_PARAMETER") singular: String,
        pluralPhrase: String,
    ): String {
        val winnerName: String
        val loserName: String
        val winnerScore: Double
        val loserScore: Double
        when (winner) {
            Winner.A -> { winnerName = nameA; loserName = nameB; winnerScore = scoreA; loserScore = scoreB }
            Winner.B -> { winnerName = nameB; loserName = nameA; winnerScore = scoreB; loserScore = scoreA }
            Winner.TIE -> {
                return "Even matchup - both score ${scoreA.toInt()}%"
            }
        }
        if (loserScore <= 0.5) {
            val topicWord = pluralPhrase.substringBefore(' ').ifBlank { "category" }
            return "$winnerName wins (no measurable $topicWord data for $loserName)"
        }
        val multiplier = winnerScore / loserScore
        val pctOfWinner = ((loserScore / winnerScore) * 100.0).coerceIn(0.0, 100.0)
        val shortLoser = shortProductName(loserName)
        val multStr = String.format("%.1fx", multiplier)
        return "$winnerName is $multStr $pluralPhrase ($shortLoser is ${pctOfWinner.toInt()}% of $winnerName)"
    }

    private fun buildHeadline(winnerName: String?, badge: WinnerBadge): String = when (badge) {
        WinnerBadge.DECISIVE_HARDWARE_WINNER ->
            if (winnerName != null) "Decisive Win for $winnerName" else "Decisive Edge Overall"
        WinnerBadge.MARGINAL_LEAD ->
            if (winnerName != null) "Marginal Lead for $winnerName" else "Marginal Lead Overall"
        WinnerBadge.CLOSE_MATCHUP ->
            "Close Matchup"
    }

    private fun buildSummary(
        winnerName: String?,
        specA: String,
        specB: String,
        display: CategoryScore,
        camera: CategoryScore,
        performance: CategoryScore,
        battery: CategoryScore,
        build: CategoryScore,
    ): String {
        val winners = listOf(display, camera, performance, battery, build)
        val leadingName = winnerName
            ?: if (winners.count { it.winner == Winner.A } > winners.count { it.winner == Winner.B }) specA else specB
        val trailingName = if (leadingName == specA) specB else specA

        val leadCategory = winners.filter { it.winner != Winner.TIE }
            .maxByOrNull { abs(it.scoreA - it.scoreB) }
        val tradeOffs = mutableListOf<String>()
        if (leadCategory != null) {
            val cat = leadCategory.category.displayName
            val winName = if (leadCategory.winner == Winner.A) specA else specB
            tradeOffs.add("$cat leans $winName")
        }
        val tightest = winners.filter { it.winner != Winner.TIE }
            .minByOrNull { abs(it.scoreA - it.scoreB) }
        if (tightest != null && tightest != leadCategory) {
            tradeOffs.add("${tightest.category.displayName} is essentially a wash")
        }
        val phrase = if (tradeOffs.isEmpty()) "Both devices are tightly balanced across categories." else tradeOffs.joinToString("; ")
        return when {
            winnerName == null -> "$specA and $specB are tightly matched. $phrase."
            else -> "$leadingName leads on the composite hardware score. $phrase. $trailingName still holds its own in the categories where it wins."
        }
    }

    private fun collectAdvantages(
        @Suppress("UNUSED_PARAMETER") deviceName: String,
        categories: List<CategoryScore>,
        forA: Boolean,
    ): List<String> {
        val bullets = mutableListOf<Pair<Double, String>>()
        for (cat in categories) {
            val my = if (forA) cat.scoreA else cat.scoreB
            val their = if (forA) cat.scoreB else cat.scoreA
            val gap = my - their
            val text = advantageText(cat, my, their, gap, forA = forA)
            if (text.isNotBlank()) {
                bullets.add(abs(gap) to text)
            }
        }
        return bullets.sortedByDescending { it.first }
            .take(3)
            .map { it.second }
            .ifEmpty { listOf("No clear advantage") }
    }

    /**
     * Feature-difference bullets that bypass the category gap model:
     *  * Headphone jack: only one device has a 3.5mm port.
     *  * Ultrasonic under-display fingerprint: one device has it, the
     *    other does not.
     *
     * Returned as additional entries that the caller can append to the
     * collected advantage bullets.
     */
    private fun featureAdvantageBullets(
        specA: PhoneSpec,
        specB: PhoneSpec,
        forA: Boolean,
    ): List<String> {
        val mySpec = if (forA) specA else specB
        val theirSpec = if (forA) specB else specA
        val out = mutableListOf<String>()

        val myJack = hasHeadphoneJack(mySpec)
        val theirJack = hasHeadphoneJack(theirSpec)
        if (myJack && !theirJack) {
            out.add("+ 3.5mm headphone jack")
        } else if (!myJack && theirJack) {
            // The losing device doesn't get a bullet - the winner implicitly has it.
        }

        val myUltra = hasUltrasonicFp(mySpec)
        val theirUltra = hasUltrasonicFp(theirSpec)
        if (myUltra && !theirUltra) {
            out.add("+ Ultrasonic under-display fingerprint")
        }
        return out
    }

    /** True when the device's "Sound → 3.5mm jack" row indicates a port. */
    private fun hasHeadphoneJack(spec: PhoneSpec): Boolean {
        val sound = spec.specs["Sound"] ?: return false
        val jack = sound["3.5mm jack"] ?: return false
        val lower = jack.lowercase().trim()
        return lower == "yes" || lower.contains("present") || lower.contains("available")
    }

    /** True when the device advertises an ultrasonic under-display fingerprint. */
    private fun hasUltrasonicFp(spec: PhoneSpec): Boolean {
        // GSMArena ships fingerprint info under "Features" or a dedicated
        // "Sensors" row. Probe both, plus the full spec blob.
        val features = spec.specs["Features"] ?: emptyMap()
        val sensorsRow = features["Sensors"] ?: ""
        val sensorsMap = spec.specs["Sensors"] ?: emptyMap()
        val all = buildString {
            append(sensorsRow)
            append(" | ")
            append(sensorsMap.entries.joinToString(" | ") { it.value })
        }
        return all.contains("ultrasonic", ignoreCase = true)
    }

    private fun advantageText(
        cat: CategoryScore,
        myScore: Double,
        theirScore: Double,
        gap: Double,
        forA: Boolean,
    ): String {
        if (abs(gap) < 1.0) return ""
        val winning = gap > 0
        return when (cat.category) {
            Category.DISPLAY -> displayAdvantage(cat, myScore, theirScore, winning, forA)
            Category.CAMERA -> cameraAdvantageLine(if (forA) cat.summaryA else cat.summaryB)
            Category.PERFORMANCE -> performanceAdvantage(cat, myScore, theirScore, winning, forA)
            Category.BATTERY -> batteryAdvantageLine(if (forA) cat.summaryA else cat.summaryB, winning)
            Category.BUILD -> buildAdvantageLine(if (forA) cat.summaryA else cat.summaryB)
            Category.PRICE -> ""  // price is metadata; no per-category bullet
        }
    }

    private fun displayAdvantage(
        cat: CategoryScore,
        my: Double,
        their: Double,
        winning: Boolean,
        forA: Boolean,
    ): String {
        val summary = if (forA) cat.summaryA else cat.summaryB
        val pct = if (their > 0.0) ((my - their) / their * 100).toInt() else 0
        val sign = if (winning) "+" else "-"
        return "$sign Display: $summary (${if (pct >= 0) "+$pct%" else "$pct%"})"
    }

    private fun performanceAdvantage(
        cat: CategoryScore,
        my: Double,
        their: Double,
        winning: Boolean,
        forA: Boolean,
    ): String {
        // Always express the ratio as >= 1.0 in the direction of travel:
        // the winner's ratio is my/their, the loser's is their/my (its
        // deficit relative to the winner) - never a raw my/their ratio
        // labeled "faster" regardless of who's actually ahead.
        val ratio = if (winning) my / max(their, 0.01) else their / max(my, 0.01)
        val pctStr = String.format("%.0f", (ratio - 1.0) * 100.0)
        val verb = if (winning) "faster" else "slower"
        val label = if (forA) cat.summaryA else cat.summaryB
        val sign = if (winning) "+" else "-"
        // Surface the lab benchmark number directly in the bullet when the
        // summary embeds one (e.g. "Apple A18 · Geekbench 6 9,846"). For
        // chipset-tier fallbacks the summary is just the SoC name and we
        // fall back to the generic phrasing.
        val gbMatch = Regex("""Geekbench\s*6\s*([\d,]+)""", RegexOption.IGNORE_CASE).find(label)
        val antutuMatch = Regex("""AnTuTu\s*v?10\s*([\d,]+)""", RegexOption.IGNORE_CASE).find(label)
        val metricBullets = when {
            gbMatch != null -> "\u26A1 Geekbench 6: ${gbMatch.groupValues[1]} multi-core"
            antutuMatch != null -> "\u26A1 AnTuTu v10: ${antutuMatch.groupValues[1]}"
            else -> label
        }
        return "$sign $pctStr% $verb silicon ($metricBullets)"
    }

    private fun cameraAdvantageLine(summary: String): String {
        val lower = summary.lowercase()
        val parts = mutableListOf<String>()
        if (lower.contains("triple") || lower.contains("quad")) parts.add("multi-lens system")
        if (lower.contains("telephoto")) parts.add("dedicated telephoto / optical zoom")
        if (lower.contains("ultrawide")) parts.add("ultrawide")
        if (lower.contains("ois")) parts.add("OIS stabilization")
        return if (parts.isEmpty()) "+ Camera: $summary" else "+ " + parts.joinToString(" + ")
    }

    private fun batteryAdvantageLine(summary: String, winning: Boolean): String {
        val mAhMatch = Regex("""(\d{3,5})\s*mAh""").find(summary)
        val wMatch = Regex("""(\d{2,3})W""").find(summary)
        val parts = mutableListOf<String>()
        if (mAhMatch != null) parts.add("${mAhMatch.groupValues[1]} mAh battery")
        if (wMatch != null) {
            val w = wMatch.groupValues[1].toInt()
            parts.add("${w}W fast charging")
        }
        val prefix = if (winning) "+" else "-"
        return if (parts.isEmpty()) "$prefix Battery: $summary" else "$prefix " + parts.joinToString(" + ")
    }

    private fun buildAdvantageLine(summary: String): String {
        val weightMatch = Regex("""(\d{2,3})g""").find(summary)
        if (weightMatch != null) {
            val w = weightMatch.groupValues[1].toIntOrNull()
            if (w != null && w < 200) return "+ Lighter ${w}g build ($summary)"
        }
        return "+ Build: $summary"
    }

    private fun shortProductName(name: String): String {
        val cleaned = name.replace(Regex("""^Apple\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Samsung\s+""", RegexOption.IGNORE_CASE), "Galaxy ")
            .replace(Regex("""\s*\(.+?\)\s*"""), "")
        return cleaned.trim()
    }

    // =====================================================================
    //  PRICE PARSING + VALUE-FOR-MONEY ANALYSIS
    // =====================================================================

    /**
     * Baseline FX rates against USD. These are *static reference rates* (2026-Q1
     * snapshot) — they're only used as a fallback when USD is not present in
     * the source string. Always favour the explicit USD value when available.
     */
    private val USD_PER_UNIT: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "EUR" to 1.08,   // 1 EUR  ≈ 1.08 USD
        "GBP" to 1.28,   // 1 GBP  ≈ 1.28 USD
        "INR" to 0.012,  // 1 INR  ≈ 0.012 USD (≈ 83 INR per USD)
    )

    /**
     * Collapses Unicode whitespace variants (thin space, NBSP, narrow NBSP)
     * and runs of regular whitespace down to single ASCII spaces, then trims.
     * GSMArena's scraped text is inconsistent about which whitespace
     * character it uses, so two "identical" values from different device
     * pages can differ at the byte level without this normalization.
     * Returns `""` for a `null` input so callers can compare the result
     * directly without a separate null-check.
     */
    fun normalizeWhitespace(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace('\u2009', ' ')
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Parse a free-form GSMArena price string into a normalized [PriceInfo].
     * Returns `null` if no usable price is present (e.g. null, blank, or a
     * "Coming soon" / "Not announced" placeholder).
     */
    fun extractPrice(rawPrice: String?): PriceInfo? {
        if (rawPrice.isNullOrBlank()) return null
        // 1) Normalize Unicode whitespace (thin space, NBSP) to ASCII space.
        val cleaned = normalizeWhitespace(rawPrice)
        if (cleaned.isEmpty()) return null

        // 2) Quick reject for known "no price" sentinels.
        val lower = cleaned.lowercase()
        val noPriceMarkers = listOf(
            "not announced",
            "coming soon",
            "rumored",
            "expected",
            "tba",
            "tbd",
            "n/a",
        )
        if (noPriceMarkers.any { lower.contains(it) }) return null

        val isApproximate = lower.contains("about") || lower.startsWith("~")

        // 3) Per-currency extraction.
        val amountUsd = extractCurrencyAmount(cleaned, CurrencyPattern.USD)
        val amountEur = extractCurrencyAmount(cleaned, CurrencyPattern.EUR)
        val amountGbp = extractCurrencyAmount(cleaned, CurrencyPattern.GBP)
        val amountInr = extractCurrencyAmount(cleaned, CurrencyPattern.INR)

        // 4) If nothing matched, give up.
        if (amountUsd == null && amountEur == null && amountGbp == null && amountInr == null) {
            return null
        }

        // 5) Normalize to USD.
        val usdAmount: Double = amountUsd
            ?: (amountEur?.let { it * (USD_PER_UNIT["EUR"] ?: 1.08) }
                ?: amountGbp?.let { it * (USD_PER_UNIT["GBP"] ?: 1.28) }
                ?: amountInr?.let { it * (USD_PER_UNIT["INR"] ?: 0.012) }
                ?: 0.0)

        // 6) Build a compact human-readable string.
        val parts = mutableListOf<String>()
        if (amountUsd != null) parts.add("$" + trimNumber(amountUsd))
        if (amountEur != null) parts.add("\u20AC" + trimNumber(amountEur))
        if (amountGbp != null) parts.add("\u00A3" + trimNumber(amountGbp))
        if (amountInr != null) parts.add("\u20B9" + trimNumber(amountInr))
        val displayCore = if (parts.isEmpty()) {
            "Unpriced"
        } else {
            parts.joinToString(" / ")
        }
        val formatted = if (isApproximate) "~$displayCore" else displayCore

        return PriceInfo(
            amountUsd = usdAmount,
            amountEur = amountEur,
            amountGbp = amountGbp,
            amountInr = amountInr,
            isApproximate = isApproximate,
            rawText = cleaned,
            formattedDisplay = formatted,
        )
    }

    /** Per-currency recognition. */
    private enum class CurrencyPattern(val symbol: String, val code: String) {
        USD("$", "USD"),
        EUR("\u20AC", "EUR"),
        GBP("\u00A3", "GBP"),
        INR("\u20B9", "INR"),
    }

    /**
     * Pull the first numeric amount that follows a given currency marker
     * (either the symbol or the ISO code).
     */
    private fun extractCurrencyAmount(text: String, pattern: CurrencyPattern): Double? {
        // 1) Symbol-first: "$ 688.97", "€769.99", "₹ 99,995"
        // The numeric fragment is digits and optional separators (no further spaces).
        val symRegex = Regex(
            """\Q${pattern.symbol}\E\s*([0-9](?:[0-9.,]*[0-9])?)""",
            RegexOption.IGNORE_CASE,
        )
        val symMatch = symRegex.find(text)
        if (symMatch != null) {
            parsePriceNumber(symMatch.groupValues[1])?.let { return it }
        }
        // 2) Code AFTER number: "100 EUR", "100EUR"
        val numCodeRegex = Regex(
            """([0-9](?:[0-9.,]*[0-9])?)\s*\b${pattern.code}\b""",
            RegexOption.IGNORE_CASE,
        )
        val numCodeMatch = numCodeRegex.find(text)
        if (numCodeMatch != null) {
            parsePriceNumber(numCodeMatch.groupValues[1])?.let { return it }
        }
        // 3) Code BEFORE number: "USD 688.97", "EUR 769.99"
        val codeRegex = Regex(
            """\b${pattern.code}\b\s*([0-9](?:[0-9.,]*[0-9])?)""",
            RegexOption.IGNORE_CASE,
        )
        val codeMatch = codeRegex.find(text)
        if (codeMatch != null) {
            parsePriceNumber(codeMatch.groupValues[1])?.let { return it }
        }
        return null
    }

    /**
     * Parse a numeric fragment like "769.99", "99,995", "1 200", "12.345,67".
     * Strips grouping characters (spaces, commas) and tries the cleanest decimal
     * interpretation. If both `.` and `,` are present, the rightmost one is the
     * decimal separator. Otherwise, use the standard heuristic:
     *   - One separator + 3 digits after it + 1-3 digits before  -> thousands
     *   - One separator + anything else                          -> decimal
     */
    private fun parsePriceNumber(fragment: String): Double? {
        if (fragment.isBlank()) return null
        val noSpaces = fragment.replace(Regex("""[\s\u00A0\u2009\u202F]+"""), "")
        val lastDot = noSpaces.lastIndexOf('.')
        val lastComma = noSpaces.lastIndexOf(',')
        val cleaned: String = when {
            lastDot == -1 && lastComma == -1 -> noSpaces
            // BOTH separators present: rightmost wins.
            lastDot != -1 && lastComma != -1 && lastDot > lastComma -> {
                // dot is decimal; strip commas as thousands sep
                noSpaces.replace(",", "")
            }
            lastDot != -1 && lastComma != -1 -> {
                // comma is decimal; strip dots as thousands sep
                noSpaces.replace(".", "").replace(",", ".")
            }
            // ONLY commas present.
            lastDot == -1 -> {
                // Group as thousands if pattern is "1-3 digits, comma, 3 digits"
                // (with possibly more groups of 3).
                if (looksLikeThousandsGrouped(noSpaces, ',')) {
                    noSpaces.replace(",", "")
                } else {
                    // Single comma - treat as decimal if right side <= 2 digits,
                    // else thousands.
                    val tail = noSpaces.length - lastComma - 1
                    if (tail <= 2) noSpaces.replace(",", ".")
                    else noSpaces.replace(",", "")
                }
            }
            // ONLY dots present.
            else -> {
                if (looksLikeThousandsGrouped(noSpaces, '.')) {
                    noSpaces.replace(".", "")
                } else {
                    val tail = noSpaces.length - lastDot - 1
                    if (tail <= 2) noSpaces
                    else noSpaces.replace(".", "")
                }
            }
        }
        return cleaned.toDoubleOrNull()
    }

    /**
     * "99,995" or "1,234,567" -> true (US thousands grouping).
     * "12,3" or "7,99" -> false.
     */
    private fun looksLikeThousandsGrouped(s: String, sep: Char): Boolean {
        val groups = s.split(sep)
        if (groups.size < 2) return false
        // First group can be 1-3 digits; subsequent groups must be exactly 3 digits.
        if (groups.first().length !in 1..3) return false
        return groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) }
    }

    private fun trimNumber(v: Double): String {
        if (v == v.toLong().toDouble()) return v.toLong().toString()
        return String.format("%.2f", v).trimEnd('0').trimEnd('.')
    }

    /**
     * Build the [ValueAnalysis] for two phones given their already-computed
     * composite hardware scores. If either side lacks a price, still returns
     * a [ValueAnalysis] (with `valueWinner = null`) so the UI can render
     * "N/A" chips and gracefully degrade the value section.
     *
     * When [excludeLegacy] is `true`, phones whose chipset is >= 7 nm
     * (heuristic for legacy / obsolete silicon) receive a 25 % penalty
     * to their value-for-money score.
     */
    private fun buildValueAnalysis(
        specA: PhoneSpec,
        specB: PhoneSpec,
        overallA: Double,
        overallB: Double,
        outrightWinner: Winner,
        excludeLegacy: Boolean = false,
        /**
         * Manual override of device A's street price in USD. When non-null,
         * replaces the upstream-extracted price in the value-for-money
         * index. The original [PriceInfo] (with its display formatting) is
         * preserved so the UI can still show the source value if it wants.
         */
        overrideUsdA: Double? = null,
        overrideUsdB: Double? = null,
    ): ValueAnalysis {
        val basePriceA = extractPrice(specA.specs["Misc"]?.get("Price"))
        val basePriceB = extractPrice(specB.specs["Misc"]?.get("Price"))

        // Apply manual override on top of the upstream price. The override
        // is the source of truth when set; we still preserve the base
        // price info for display so the UI can show "Was $1299, now $899".
        val priceA: PriceInfo? = when {
            overrideUsdA != null && overrideUsdA > 0.0 ->
                (basePriceA ?: PriceInfo(
                    amountUsd = overrideUsdA,
                    rawText = overrideUsdA.toString(),
                    formattedDisplay = "$" + trimNumber(overrideUsdA),
                )).copy(
                    amountUsd = overrideUsdA,
                    formattedDisplay = "$" + trimNumber(overrideUsdA) + " (Custom)",
                )
            else -> basePriceA
        }
        val priceB: PriceInfo? = when {
            overrideUsdB != null && overrideUsdB > 0.0 ->
                (basePriceB ?: PriceInfo(
                    amountUsd = overrideUsdB,
                    rawText = overrideUsdB.toString(),
                    formattedDisplay = "$" + trimNumber(overrideUsdB),
                )).copy(
                    amountUsd = overrideUsdB,
                    formattedDisplay = "$" + trimNumber(overrideUsdB) + " (Custom)",
                )
            else -> basePriceB
        }

        if (priceA == null || priceB == null || priceA.amountUsd <= 0.0 || priceB.amountUsd <= 0.0) {
            return ValueAnalysis(
                priceA = priceA,
                priceB = priceB,
                valueScoreA = null,
                valueScoreB = null,
                outrightWinner = outrightWinner,
                valueWinner = null,
                valueRatioA = null,
                valueAdvantageText = null,
                formattedRatio = null,
            )
        }

        // Apply legacy-depreciation penalty (only to value score, not to
        // outright hardware score). Phones with 7nm+ silicon lose 25% of
        // their per-dollar value, which is the standard "you're paying for
        // outdated chips" warning.
        val valueA = (overallA / priceA.amountUsd) * 100.0 *
            if (excludeLegacy && isLegacySilicon(specA)) LEGACY_PENALTY else 1.0
        val valueB = (overallB / priceB.amountUsd) * 100.0 *
            if (excludeLegacy && isLegacySilicon(specB)) LEGACY_PENALTY else 1.0

        val valueWinner = when {
            valueA > valueB + 0.01 -> Winner.A
            valueB > valueA + 0.01 -> Winner.B
            else -> Winner.TIE
        }

        val ratioA: Double? = if (valueB > 0.0) valueA / valueB else null
        val formattedRatio: String? = ratioA?.let { String.format("%.1fx", it) }

        val advantageText = buildValueAdvantageText(
            specA.name,
            specB.name,
            priceA,
            priceB,
            valueA,
            valueB,
            valueWinner,
        )

        return ValueAnalysis(
            priceA = priceA,
            priceB = priceB,
            valueScoreA = valueA,
            valueScoreB = valueB,
            outrightWinner = outrightWinner,
            valueWinner = valueWinner,
            valueRatioA = ratioA,
            valueAdvantageText = advantageText,
            formattedRatio = formattedRatio,
        )
    }

    private fun buildValueAdvantageText(
        nameA: String,
        nameB: String,
        priceA: PriceInfo,
        priceB: PriceInfo,
        valueA: Double,
        valueB: Double,
        winner: Winner,
    ): String? {
        if (winner == Winner.TIE) return null
        val winnerName: String
        val loserName: String
        val winnerValue: Double
        val loserValue: Double
        val winnerPrice: PriceInfo
        val loserPrice: PriceInfo
        when (winner) {
            Winner.A -> {
                winnerName = nameA; loserName = nameB
                winnerValue = valueA; loserValue = valueB
                winnerPrice = priceA; loserPrice = priceB
            }
            Winner.B -> {
                winnerName = nameB; loserName = nameA
                winnerValue = valueB; loserValue = valueA
                winnerPrice = priceB; loserPrice = priceA
            }
            else -> return null
        }
        if (loserValue <= 0.0) return null
        val multiplier = winnerValue / loserValue
        val multStr = String.format("%.1f", multiplier)
        val priceStr = formatUsd(winnerPrice.amountUsd)
        val otherStr = formatUsd(loserPrice.amountUsd)
        return "$winnerName delivers ${multStr}\u00D7 more spec-per-dollar at \u2248$priceStr vs \u2248$otherStr for $loserName"
    }

    private fun formatUsd(usd: Double): String = "$" + trimNumber(usd)

    /**
     * Value penalty applied to phones whose chipset is deemed "legacy"
     * (>= 7 nm) when the user toggles `excludeLegacyDevices` on.
     */
    private const val LEGACY_PENALTY: Double = 0.75

    /**
     * Heuristic to decide whether a phone should be classified as legacy
     * silicon. Looks at the chipset's fabrication node ("7 nm" or larger)
     * and falls back to the phone name if the node is missing.
     */
    fun isLegacySilicon(spec: PhoneSpec): Boolean {
        val chipset = spec.specs["Platform"]?.get("Chipset").orEmpty()
        val nm = extractNm(chipset)
        if (nm != null && nm >= 7.0) return true
        // Fallback: 4-digit year >= 2019 in the name suggests an old flagship.
        val nameMatch = Regex("""(?:19|20)(\d{2})""").find(spec.name)
        if (nameMatch != null) {
            val year = nameMatch.groupValues[1].toIntOrNull() ?: return false
            if (year <= 20) return true  // 2019 or 2020
        }
        return false
    }

    // =====================================================================
    //  LAB BENCHMARKS (Geekbench 6, AnTuTu v10, 3DMark, Display, Battery)
    // =====================================================================

    /**
     * Extract lab benchmark metrics from the raw `PhoneSpec.specs` map.
     * Looks under the `"Our Tests"` source category for `Performance`, `Display`,
     * and `Battery` rows.
     */
    fun extractLabBenchmarks(
        specs: Map<String, Map<String, String>>,
    ): LabBenchmarks {
        val tests = specs["Our Tests"] ?: emptyMap()
        val perf = tests["Performance"].orEmpty()
        val disp = tests["Display"].orEmpty()
        val batt = tests["Battery"].orEmpty()

        val gbScore = LAB_GB6.find(perf)?.groupValues?.get(1)?.toDoubleOrNull()
        val antutuScore = LAB_ANTUTU.find(perf)?.groupValues?.get(1)?.toDoubleOrNull()
        // Two known upstream shapes for 3DMark:
        //  - "3DMark: 6687 (Wild Life Extreme)" - score then parenthetical suite.
        //  - "3DMark Wild Life: 17822" - suite name inline, no parens.
        // Group order is reversed between the two, so each is extracted separately.
        val threeDMarkParen = LAB_3DMARK_PAREN.find(perf)
        val threeDMarkInline = LAB_3DMARK_INLINE.find(perf)
        val threeDMarkScore = threeDMarkParen?.groupValues?.get(1)?.toDoubleOrNull()
            ?: threeDMarkInline?.groupValues?.get(2)?.toDoubleOrNull()
        val threeDMarkSuite = threeDMarkParen?.groupValues?.get(2)?.trim()
            ?: threeDMarkInline?.groupValues?.get(1)?.trim()
        val nits = LAB_NITS.find(disp)?.groupValues?.get(1)?.toDoubleOrNull()
        val activeMinutes = LAB_BATT_H.find(batt)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val mins = match.groupValues[2].toIntOrNull() ?: 0
            hours * 60 + mins
        }

        return LabBenchmarks(
            geekbenchV6 = gbScore,
            antutuV10 = antutuScore,
            threeDMarkScore = threeDMarkScore,
            threeDMarkSuite = threeDMarkSuite,
            measuredNits = nits,
            activeUseMinutes = activeMinutes,
        )
    }

    // "GeekBench: 9846 (v6)" — GSMArena convention.
    private val LAB_GB6 = Regex(
        """GeekBench:\s*(\d+)\s*\(v6\)""",
        RegexOption.IGNORE_CASE,
    )
    // "AnTuTu: 2207809 (v10)"
    private val LAB_ANTUTU = Regex(
        """AnTuTu:\s*(\d+)\s*\(v10\)""",
        RegexOption.IGNORE_CASE,
    )
    // "3DMark: 6687 (Wild Life Extreme)" — score, then parenthetical suite name.
    private val LAB_3DMARK_PAREN = Regex(
        """3DMark:\s*(\d+)\s*\(([^)]+)\)""",
        RegexOption.IGNORE_CASE,
    )
    // "3DMark Wild Life: 17822" — suite name inline before the colon, no parens.
    private val LAB_3DMARK_INLINE = Regex(
        """3DMark\s+([^:]+):\s*(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    // "1417 nits" (brightness)
    private val LAB_NITS = Regex(
        """(\d{2,5})\s*nits""",
        RegexOption.IGNORE_CASE,
    )
    // "14:49h" or "14:49 h" (active-use battery endurance in H:M)
    private val LAB_BATT_H = Regex(
        """(\d{1,2}):(\d{2})\s*h""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Convert a Geekbench 6 multi-core score to a 0-100 performance rating.
     * A score of 20,000 -> 100; the curve is linear and saturates at 100.
     * The reference ceiling needs periodic bumping as flagship scores climb
     * (2026 flagships already clear 11,000+) - if two devices both saturate
     * at 100 they become indistinguishable (tie, no advantage bullet) even
     * when their raw scores clearly differ, so keep this comfortably above
     * the current top real-world score.
     */
    private fun geekbench6ToScore(gb: Double): Double =
        min(100.0, (gb / 20000.0) * 100.0)

    /**
     * Convert an AnTuTu v10 score to a 0-100 performance rating. A score of
     * 4,000,000 -> 100; the curve is linear and saturates at 100. See
     * [geekbench6ToScore] for why the ceiling needs headroom above current
     * real-world scores (2026 flagships already clear 2,600,000+).
     */
    private fun antutu10ToScore(antutu: Double): Double =
        min(100.0, (antutu / 4_000_000.0) * 100.0)
}

