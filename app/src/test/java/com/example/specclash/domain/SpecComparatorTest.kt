package com.example.specclash.domain

import com.example.specclash.domain.SpecComparator.Winner
import com.example.specclash.domain.SpecComparator.WinnerBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecComparatorTest {

    // -----------------------------------------------------------------
    //  Legacy per-spec comparison API (unchanged)
    // -----------------------------------------------------------------

    @Test
    fun `battery - non-whitelisted key returns TIE with no delta badge`() {
        // "Battery Type" is NOT in ALLOWED_DELTA_KEYS, so the comparator
        // returns a clean side-by-side TIE with no subtraction badge.
        val c = SpecComparator.compare("4000 mAh", "3561 mAh", "Battery Type")
        assertEquals(Winner.TIE, c.winner)
        assertEquals(4000.0, c.valueA!!, 0.0)
        assertEquals(3561.0, c.valueB!!, 0.0)
        assertEquals("", c.deltaText)
    }

    @Test
    fun `weight - lower value wins and reports grams lighter`() {
        val c = SpecComparator.compare("167 g or 168 g", "170 g (6.00 oz)", "Weight")
        assertEquals(Winner.A, c.winner)
        assertTrue(c.deltaText.contains("g lighter"))
    }

    @Test
    fun `weight - inverted polarity - the lighter device gets a lighter badge regardless of slot`() {
        // A=179g (heavier), B=177g (lighter). B is the winner and the
        // delta text on B's row must read "lighter" - never "heavier".
        val c = SpecComparator.compare("179 g", "177 g", "Weight")
        assertEquals(Winner.B, c.winner)
        assertTrue(
            "Expected 'g lighter' on the winner's row, got '${c.deltaText}'",
            c.deltaText.contains("g lighter"),
        )
        assertFalse(
            "Delta must never read 'heavier' on the winner's row, got '${c.deltaText}'",
            c.deltaText.contains("heavier"),
        )
    }

    @Test
    fun `screen size - higher value reports larger inches`() {
        val c = SpecComparator.compare(
            "6.2 inches, 94.4 cm2",
            "6.1 inches, 91.7 cm2",
            "Display Size"
        )
        assertEquals(Winner.A, c.winner)
        assertTrue(c.deltaText.contains("larger"))
    }

    @Test
    fun `screen size - inverted polarity - B has the larger screen and its badge reads larger, not smaller`() {
        // A=6.1in (smaller), B=6.2in (larger). B is the winner and the
        // delta text on B's row must read "larger" - never "smaller".
        val c = SpecComparator.compare(
            "6.1 inches, 91.7 cm2",
            "6.2 inches, 94.4 cm2",
            "Display Size"
        )
        assertEquals(Winner.B, c.winner)
        assertTrue(
            "Expected 'larger' on the winner's row, got '${c.deltaText}'",
            c.deltaText.contains("larger"),
        )
        assertFalse(
            "Delta must never read 'smaller' on the winner's row, got '${c.deltaText}'",
            c.deltaText.contains("smaller"),
        )
    }

    @Test
    fun `thickness - non-whitelisted Dimensions key returns TIE`() {
        // "Dimensions" is NOT in ALLOWED_DELTA_KEYS, so the comparator
        // returns a clean side-by-side TIE with no subtraction badge.
        val c = SpecComparator.compare(
            "147 x 70.6 x 7.6 mm (5.79 x 2.78 x 0.30 in)",
            "147.6 x 71.6 x 7.8 mm (5.81 x 2.82 x 0.31 in)",
            "Dimensions"
        )
        assertEquals(Winner.TIE, c.winner)
        assertEquals("", c.deltaText)
    }

    @Test
    fun `charging wattage - higher W reports faster`() {
        val c = SpecComparator.compare("45W wired", "25W wired", "Charging")
        assertEquals(Winner.A, c.winner)
        assertEquals("+20 W faster", c.deltaText)
    }

    @Test
    fun `charging wattage - inverted polarity - B charges faster and its badge reads faster, not slower`() {
        // Regression test: A=33W (slower), B=67W (faster). B is the winner
        // and the delta text on B's row must read "faster" - previously it
        // read "34 W slower" because formatDelta branched on "is A the
        // bigger raw number" instead of describing whichever side actually
        // won.
        val c = SpecComparator.compare(
            "33W wired, PD 2.0, PPS, QC3, 18W reverse wired",
            "67W wired, PD3.0, 50% in 17 min, 100% in 44 min",
            "Charging",
        )
        assertEquals(Winner.B, c.winner)
        assertEquals("+34 W faster", c.deltaText)
    }

    @Test
    fun `charging wattage - regression - never compares wired against wireless`() {
        // Regression test for a real-world report: Samsung Galaxy S26 Ultra
        // discloses 60W wired charging; Apple never discloses the iPhone's
        // wired wattage (only "Wired, PD3.2, ..." with no number) but does
        // disclose 25W wireless. The old GENERIC/CHARGING_W parser grabbed
        // whichever "<num>W" happened to appear first regardless of mode,
        // so it compared S26U's 60W *wired* against the iPhone's 25W
        // *wireless* and declared "+35 W faster" - a meaningless
        // cross-standard comparison. With no wired figure disclosed on the
        // iPhone's side, the only apples-to-apples comparison left is
        // wireless-vs-wireless, and both happen to disclose 25W wireless -
        // a genuine tie, not a fabricated "wired winner".
        val c = SpecComparator.compare(
            "60W wired, PD3.0, 75% in 30 min, 25W wireless (Qi2.2), 4.5W reverse wireless",
            "Wired, PD3.2, AVS, 50% in 20 min, 25W wireless MagSafe/Qi2, 50% in 30 min (15W - China), 4.5W reverse wired",
            "Charging",
        )
        assertEquals(Winner.TIE, c.winner)
        assertEquals("Same", c.deltaText)
    }

    @Test
    fun `charging wattage - regression - no comparable mode on either side ties with no badge`() {
        // Neither side discloses a wired number, and only one side
        // discloses a wireless number - there is nothing comparable at all.
        val c = SpecComparator.compare(
            "Wired, PD3.2, 50% in 20 min",
            "Wired, 50% in 25 min",
            "Charging",
        )
        assertEquals(Winner.TIE, c.winner)
        assertEquals("—", c.deltaText)
    }

    @Test
    fun `charging wattage - falls back to wireless-vs-wireless when neither side discloses wired wattage`() {
        val c = SpecComparator.compare(
            "Wired, 50% in 20 min, 20W wireless, 4.5W reverse wired",
            "Wired, 50% in 30 min, 15W wireless, 4.5W reverse wired",
            "Charging",
        )
        assertEquals(Winner.A, c.winner)
        assertEquals("+5 W faster", c.deltaText)
    }

    @Test
    fun `price - lower price wins when A is cheaper`() {
        // €164.00 and €210.99 are normalized to USD (x1.08) before diffing,
        // so the delta is a dollar amount, not a raw EUR subtraction:
        // 177.12 vs 227.8692 -> $50.75.
        val c = SpecComparator.compare("€ 164.00", "€ 210.99", "Price")
        assertEquals(Winner.A, c.winner)
        assertEquals("$50.75 lower", c.deltaText)
        assertFalse(
            "Delta must never read 'higher' on the winner's row, got '${c.deltaText}'",
            c.deltaText.contains("higher"),
        )
    }

    @Test
    fun `price - inverted polarity - regression - the cheaper device wins even when it is slotted as B`() {
        // Regression test: A=€210.99 (pricier), B=€164.00 (cheaper). "price"
        // was previously missing from lowerIsBetterKeys, so direction()
        // fell through to Direction.UNKNOWN, which defaults to "more is
        // more" - the pricier device (A) was incorrectly declared the
        // winner and highlighted, with the cheaper device's delta text
        // nonsensically reading "46.99 lower" despite A "winning".
        val c = SpecComparator.compare("€ 210.99", "€ 164.00", "Price")
        assertEquals(Winner.B, c.winner)
        assertEquals("$50.75 lower", c.deltaText)
    }

    @Test
    fun `price - multi-currency regression - a currency-tagged total is never truncated or compared raw across currencies`() {
        // Regression test for a real-world report: device A lists three
        // currencies ("£249 / €219 / ₹46,990"), device B lists only INR
        // ("₹26,690"). The old GENERIC_NUMBER fallback ignored currency
        // symbols entirely and grabbed whichever digit run appeared first,
        // so it compared A's raw "249" (from £249) against B's raw "26"
        // (truncated at the first comma in "26,690") - a nonsense
        // cross-currency, cross-magnitude "223.99 lower" result.
        //
        // Correct behavior: both sides are normalized to USD (no literal $
        // figure is present in either string, so extractPrice falls back to
        // its EUR/GBP/INR conversion table, preferring EUR when present):
        //   A -> €219 * 1.08 = $236.52
        //   B -> ₹26,690 * 0.012 = $320.28
        val c = SpecComparator.compare("£249 / €219 / ₹46,990", "₹26,690", "Price")
        assertEquals(Winner.A, c.winner)
        assertEquals("$83.76 lower", c.deltaText)
    }

    @Test
    fun `equal values report tie`() {
        val c = SpecComparator.compare("4000 mAh", "4000 mAh", "Battery")
        assertEquals(Winner.TIE, c.winner)
    }

    @Test
    fun `null values do not crash and report tie`() {
        val c = SpecComparator.compare(null, "4000 mAh", "Battery")
        assertEquals(Winner.TIE, c.winner)
    }

    @Test
    fun `lower-is-better for nm process node - Chipset is whitelisted out`() {
        // "Chipset" is NOT in ALLOWED_DELTA_KEYS, so the comparator
        // returns a clean side-by-side TIE with no delta badge. nm-process
        // ranking now happens inside the lab-benchmark performance engine.
        val c = SpecComparator.compare(
            "Qualcomm SM8650-AC Snapdragon 8 Gen 3 (4 nm)",
            "Apple A18 (3 nm)",
            "Chipset"
        )
        assertEquals(Winner.TIE, c.winner)
        assertEquals("", c.deltaText)
    }

    // -----------------------------------------------------------------
    //  Weighted Multi-Attribute Scoring Engine
    // -----------------------------------------------------------------

    @Test
    fun `Galaxy S25 FE vs iPhone 16e - S25 FE wins overall despite 16e's A18 SoC`() {
        val s25FE = phoneSpec(
            slug = "samsung-galaxy-s25-fe",
            name = "Samsung Galaxy S25 FE",
            display = mapOf(
                "Type" to "Dynamic AMOLED 2X, 120Hz, HDR10+, 1900 nits (peak)",
                "Size" to "6.4 inches",
                "Resolution" to "1080 x 2340 pixels",
            ),
            platform = mapOf(
                "OS" to "Android 15, One UI 7",
                "Chipset" to "Exynos 2400e (4 nm)",
                "CPU" to "Deca-core",
                "GPU" to "Xclipse 940",
            ),
            memory = mapOf("Internal" to "256GB 8GB RAM"),
            camera = mapOf(
                "Triple" to "50 MP, f/1.8, 24mm (wide), 1/1.56\", PDAF, OIS",
                "Features" to "LED flash, auto-HDR, panorama, ultrawide, 3x optical zoom",
                "Video" to "8K@30fps, 4K@60fps, 1080p@120fps",
            ),
            battery = mapOf(
                "Type" to "Li-Ion 4700 mAh",
                "Charging" to "25W wired, 15W wireless",
            ),
            body = mapOf(
                "Dimensions" to "161.3 x 76.6 x 7.9 mm",
                "Weight" to "195 g",
                "Build" to "Glass front (Gorilla Glass Victus+), aluminum frame, IP68 dust/water resistant",
                "SIM" to "Nano-SIM + eSIM",
            ),
        )
        val iphone16e = phoneSpec(
            slug = "apple-iphone-16e",
            name = "Apple iPhone 16e",
            display = mapOf(
                "Type" to "Super Retina XDR OLED, 60Hz, 1200 nits (peak)",
                "Size" to "6.1 inches",
                "Resolution" to "1170 x 2532 pixels",
            ),
            platform = mapOf(
                "OS" to "iOS 18",
                "Chipset" to "Apple A18 (3 nm)",
                "CPU" to "Hexa-core",
                "GPU" to "Apple GPU (4-core graphics)",
            ),
            memory = mapOf("Internal" to "256GB 8GB RAM"),
            camera = mapOf(
                "Single" to "48 MP, f/1.6, 26mm (wide), 1/2.0\", PDAF, OIS",
                "Features" to "Dual-LED dual-tone flash, HDR",
                "Video" to "4K@60fps, 1080p@240fps",
            ),
            battery = mapOf(
                "Type" to "Li-Ion 4005 mAh",
                "Charging" to "20W wired, 7.5W wireless",
            ),
            body = mapOf(
                "Dimensions" to "138.7 x 64.2 x 8.0 mm",
                "Weight" to "172 g",
                "Build" to "Glass front, glass back, aluminum frame, IP68 dust/water resistant",
                "SIM" to "Nano-SIM + eSIM",
            ),
        )
        val verdict = SpecComparator.buildVerdict(s25FE, iphone16e)

        // S25 FE must win overall despite iPhone's A18 silicon lead.
        assertEquals("S25 FE expected to win overall", Winner.A, verdict.winner)
        // 16e's A18 is 97 vs Exynos 2400e 85 - B should win performance.
        assertEquals(Winner.B, verdict.performance.winner)
        // 120Hz vs 60Hz - 16e must be docked by the 60Hz penalty.
        assertEquals(Winner.A, verdict.display.winner)
        assertTrue(
            "Expected 60Hz penalty to dock 16e display score; got ${verdict.display.scoreB}",
            verdict.display.scoreB < 70.0,
        )
        // S25 FE has triple camera vs 16e single - S25 FE wins.
        assertEquals(Winner.A, verdict.camera.winner)
        // Battery: S25 FE 4700 + 25W vs 16e 4005 + 20W.
        assertEquals(Winner.A, verdict.battery.winner)
        // Build: 16e is lighter, but S25 FE is more portable overall - close.
        // Just ensure both are scored.
        assertTrue(verdict.build.scoreA > 0.0)
        assertTrue(verdict.build.scoreB > 0.0)
    }

    @Test
    fun `Exynos 2400e is scored below the plain Exynos 2400, not shadowed by the substring match`() {
        // Regression test: CHIPSET_TIER lookup matches on the first
        // substring hit, and "exynos 2400" is itself a substring of
        // "exynos 2400e". With "exynos 2400" listed first, any "Exynos
        // 2400e" chipset was silently scored as the faster plain 2400 (88
        // instead of its own, lower, tier of 85).
        val common = mapOf(
            "Type" to "AMOLED, 120Hz, 1200 nits (peak)",
            "Size" to "6.4 inches",
            "Resolution" to "1080 x 2340 pixels",
        )
        val commonBattery = mapOf("Type" to "Li-Ion 4500 mAh", "Charging" to "25W wired")
        val commonBody = mapOf("Weight" to "190 g")
        val commonCamera = mapOf("Triple" to "50 MP, f/1.8, OIS")

        val plain2400 = phoneSpec(
            slug = "plain-2400",
            name = "Plain 2400 Phone",
            display = common,
            platform = mapOf("Chipset" to "Exynos 2400 (4 nm)"),
            battery = commonBattery,
            body = commonBody,
            camera = commonCamera,
        )
        val the2400e = phoneSpec(
            slug = "2400e",
            name = "2400e Phone",
            display = common,
            platform = mapOf("Chipset" to "Exynos 2400e (4 nm)"),
            battery = commonBattery,
            body = commonBody,
            camera = commonCamera,
        )

        val verdict = SpecComparator.buildVerdict(plain2400, the2400e)
        assertTrue(
            "Plain Exynos 2400 (88) must score strictly higher than Exynos 2400e (85), " +
                "got A=${verdict.performance.scoreA} B=${verdict.performance.scoreB}",
            verdict.performance.scoreA > verdict.performance.scoreB,
        )
        assertEquals(Winner.A, verdict.performance.winner)
    }

    @Test
    fun `performance advantage bullets - winner reads faster, loser reads slower with matching percentages`() {
        // Regression test: performanceAdvantage() used to compute my/their
        // independently for both sides and label both "faster", producing
        // nonsense pairs like "+ 1.5x faster silicon" / "- 0.7x faster
        // silicon". Both sides must now report the same percentage, worded
        // "faster" for the winner and "slower" for the loser.
        val common = mapOf(
            "Type" to "AMOLED, 120Hz, 1200 nits (peak)",
            "Size" to "6.4 inches",
            "Resolution" to "1080 x 2340 pixels",
        )
        val commonBattery = mapOf("Type" to "Li-Ion 4500 mAh", "Charging" to "25W wired")
        val commonBody = mapOf("Weight" to "190 g")
        val commonCamera = mapOf("Triple" to "50 MP, f/1.8, OIS")

        val flagship = phoneSpec(
            slug = "flagship",
            name = "Flagship Phone",
            display = common,
            platform = mapOf("Chipset" to "Snapdragon 8 Elite (3 nm)"),
            battery = commonBattery,
            body = commonBody,
            camera = commonCamera,
        )
        val midrange = phoneSpec(
            slug = "midrange",
            name = "Midrange Phone",
            display = common,
            platform = mapOf("Chipset" to "Snapdragon 7s Gen 2 (4 nm)"),
            battery = commonBattery,
            body = commonBody,
            camera = commonCamera,
        )

        val verdict = SpecComparator.buildVerdict(flagship, midrange)
        assertEquals(Winner.A, verdict.performance.winner)

        val winnerBullet = verdict.advantagesA.first { it.contains("silicon") }
        val loserBullet = verdict.advantagesB.first { it.contains("silicon") }
        assertTrue("Winner bullet should read 'faster': $winnerBullet", winnerBullet.contains("faster"))
        assertTrue("Loser bullet should read 'slower', not 'faster': $loserBullet", loserBullet.contains("slower"))
        assertFalse("Loser bullet must never say 'faster': $loserBullet", loserBullet.contains("faster"))

        val pct = Regex("""(\d+)%""")
        val winnerPct = pct.find(winnerBullet)!!.groupValues[1]
        val loserPct = pct.find(loserBullet)!!.groupValues[1]
        assertEquals(
            "Both sides should quote the same percentage, just with an opposite verb",
            winnerPct,
            loserPct,
        )
    }

    @Test
    fun `Galaxy S26 Ultra vs Galaxy A57 - performance gap is 1_6 to 1_8x with A57 at 55 to 60 percent`() {
        val s26Ultra = phoneSpec(
            slug = "samsung-galaxy-s26-ultra",
            name = "Samsung Galaxy S26 Ultra",
            display = mapOf(
                "Type" to "Dynamic LTPO AMOLED 2X, 120Hz, HDR10+, 2600 nits (peak)",
                "Size" to "6.9 inches",
            ),
            platform = mapOf(
                "OS" to "Android 16, One UI 8",
                "Chipset" to "Qualcomm SM8750 Snapdragon 8 Elite (3 nm)",
                "CPU" to "Octa-core",
                "GPU" to "Adreno 830",
            ),
            memory = mapOf("Internal" to "512GB 12GB RAM"),
            camera = mapOf(
                "Quad" to "200 MP + 50 MP + 10 MP + 12 MP",
                "Features" to "Laser AF, 5x optical zoom, OIS, ultrawide",
                "Video" to "8K@30fps, 4K@120fps",
            ),
            battery = mapOf(
                "Type" to "Li-Ion 5000 mAh",
                "Charging" to "45W wired, 15W wireless",
            ),
            body = mapOf(
                "Dimensions" to "162.3 x 79 x 8.2 mm",
                "Weight" to "232 g",
                "Build" to "Glass front + back, titanium frame, IP68 dust/water resistant",
            ),
        )
        val a57 = phoneSpec(
            slug = "samsung-galaxy-a57",
            name = "Samsung Galaxy A57",
            display = mapOf(
                "Type" to "Super AMOLED, 120Hz, 1000 nits (peak)",
                "Size" to "6.7 inches",
            ),
            platform = mapOf(
                "OS" to "Android 15, One UI 7",
                "Chipset" to "Exynos 1580 (4 nm)",
                "CPU" to "Octa-core",
                "GPU" to "Xclipse 540",
            ),
            memory = mapOf("Internal" to "128GB 6GB RAM"),
            camera = mapOf(
                "Triple" to "50 MP + 12 MP + 5 MP",
                "Features" to "LED flash, ultrawide, macro",
                "Video" to "4K@30fps, 1080p@30fps",
            ),
            battery = mapOf(
                "Type" to "Li-Ion 5000 mAh",
                "Charging" to "25W wired",
            ),
            body = mapOf(
                "Dimensions" to "161.7 x 78 x 7.6 mm",
                "Weight" to "200 g",
                "Build" to "Glass front, plastic back, IP67 dust/water resistant",
            ),
        )
        val verdict = SpecComparator.buildVerdict(s26Ultra, a57)

        // Performance: S26 Ultra must lead A57 by 1.6x to 1.8x.
        val perf = verdict.performance
        assertEquals("S26 Ultra expected to lead performance", Winner.A, perf.winner)
        val ratio = perf.scoreA / perf.scoreB
        assertTrue(
            "Expected performance ratio 1.6x-1.8x, got ${"%.2f".format(ratio)}x (A=${perf.scoreA} B=${perf.scoreB})",
            ratio >= 1.6 && ratio <= 1.85,
        )
        // A57 must score ~55-60% of Ultra.
        val pct = (perf.scoreB / perf.scoreA) * 100.0
        assertTrue(
            "Expected A57 performance at 55-60% of Ultra, got ${"%.1f".format(pct)}%",
            pct >= 55.0 && pct <= 62.0,
        )
        // Overall: Ultra wins decisively.
        assertEquals(Winner.A, verdict.winner)
        assertEquals(WinnerBadge.DECISIVE_HARDWARE_WINNER, verdict.badge)
    }

    @Test
    fun `Identical devices produce a close matchup and balanced scores`() {
        val spec = mapOf(
            "Display" to mapOf(
                "Type" to "Dynamic AMOLED 2X, 120Hz, 1500 nits (peak)",
                "Size" to "6.5 inches",
            ),
            "Platform" to mapOf(
                "Chipset" to "Qualcomm SM8550 Snapdragon 8 Gen 2 (4 nm)",
            ),
            "Memory" to mapOf("Internal" to "256GB 8GB RAM"),
            "Main Camera" to mapOf(
                "Triple" to "50 MP + 12 MP + 10 MP",
                "Features" to "OIS, ultrawide, 3x optical zoom",
                "Video" to "4K@60fps",
            ),
            "Battery" to mapOf(
                "Type" to "Li-Ion 4500 mAh",
                "Charging" to "25W wired",
            ),
            "Body" to mapOf(
                "Dimensions" to "160 x 74 x 7.8 mm",
                "Weight" to "190 g",
                "Build" to "Glass front, plastic back, IP68 dust/water resistant",
            ),
        )
        val a = PhoneSpec(slug = "phone-x", name = "Phone X", image = "", specs = spec)
        val b = PhoneSpec(slug = "phone-x", name = "Phone X", image = "", specs = spec)

        val verdict = SpecComparator.buildVerdict(a, b)
        // Tied composite -> all categories TIE.
        assertEquals(Winner.TIE, verdict.winner)
        assertEquals(WinnerBadge.CLOSE_MATCHUP, verdict.badge)
        assertEquals(verdict.overallScoreA, verdict.overallScoreB, 0.0001)
        assertTrue("Headline should mention close matchup",
            verdict.headline.contains("Close", ignoreCase = true) ||
                verdict.headline.contains("Matchup", ignoreCase = true))
        // Even though each side has 3 default "no advantage" bullets, gap is zero everywhere.
        for (cat in listOf(verdict.display, verdict.camera, verdict.performance, verdict.battery, verdict.build)) {
            assertEquals(Winner.TIE, cat.winner)
            assertEquals(cat.scoreA, cat.scoreB, 0.0001)
        }
    }

    @Test
    fun `Identical devices with empty spec maps still produce a verdict without crashing`() {
        val a = PhoneSpec(slug = "a", name = "Device A", image = "", specs = emptyMap())
        val b = PhoneSpec(slug = "b", name = "Device B", image = "", specs = emptyMap())
        val verdict = SpecComparator.buildVerdict(a, b)
        // When both devices have identical empty specs, no category can pick a winner.
        assertEquals(Winner.TIE, verdict.winner)
        assertEquals(WinnerBadge.CLOSE_MATCHUP, verdict.badge)
        // The composite score is identical for both devices.
        assertEquals(verdict.overallScoreA, verdict.overallScoreB, 0.0001)
        // Sanity: headline + summary are non-empty.
        assertNotNull(verdict.headline)
        assertNotNull(verdict.summary)
        assertTrue(verdict.headline.isNotBlank())
        assertTrue(verdict.summary.isNotBlank())
    }

    @Test
    fun `60Hz penalty kicks in when one device is 120Hz and the other is 60Hz`() {
        val highHz = phoneSpec(
            slug = "high",
            name = "120Hz Phone",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits", "Size" to "6.5 inches"),
        )
        val lowHz = phoneSpec(
            slug = "low",
            name = "60Hz Phone",
            display = mapOf("Type" to "IPS LCD, 60Hz, 600 nits", "Size" to "6.5 inches"),
        )
        val verdict = SpecComparator.buildVerdict(highHz, lowHz)
        // The 60Hz device must have its display score docked.
        val rawLow = (15.0 + 10.0 + 8.0) * 0.7  // refresh 60Hz=15, IPS=10, <1000 nits=8, *0.7 penalty
        assertTrue(
            "Expected 60Hz penalty to dock low-Hz display below raw score, got ${verdict.display.scoreB}",
            verdict.display.scoreB <= rawLow + 0.5,
        )
        assertEquals(Winner.A, verdict.display.winner)
    }

    // -----------------------------------------------------------------
    //  Price parsing + value-for-money engine
    // -----------------------------------------------------------------

    @Test
    fun `extractPrice - multi-currency with thin spaces parses USD and EUR`() {
        // Real-world GSMArena format: "€ 769.99 / $ 688.97 / £ 619.00 / ₹ 99,995"
        // Uses a mix of regular and thin (\u2009) spaces.
        val raw = "\u20AC\u2009769.99 / \$\u2009688.97 / \u00A3\u2009619.00 / \u20B9\u200999,995"
        val info = SpecComparator.extractPrice(raw)
        assertNotNull("Expected PriceInfo for multi-currency string", info)
        // USD amount: explicit "$" matched first; 688.97.
        assertEquals(688.97, info!!.amountUsd, 0.001)
        // EUR amount: 769.99.
        assertEquals(769.99, info.amountEur!!, 0.001)
        // GBP: 619.00.
        assertEquals(619.0, info.amountGbp!!, 0.001)
        // INR: 99,995 (with thousands comma).
        assertEquals(99995.0, info.amountInr!!, 0.001)
        // Display string should include both USD and EUR.
        assertTrue(
            "Expected USD in display: ${info.formattedDisplay}",
            info.formattedDisplay.contains("\$"),
        )
        assertTrue(
            "Expected EUR in display: ${info.formattedDisplay}",
            info.formattedDisplay.contains("\u20AC"),
        )
    }

    @Test
    fun `extractPrice - About approximation parses EUR and falls back to USD`() {
        val info = SpecComparator.extractPrice("About 100 EUR")
        assertNotNull("Expected PriceInfo for 'About 100 EUR'", info)
        assertEquals(100.0, info!!.amountEur!!, 0.001)
        // USD should be derived via the EUR->USD rate (~1.08), in the 100-120 range.
        assertTrue(
            "Expected USD amount to be in 100-120 range, got ${info.amountUsd}",
            info.amountUsd in 100.0..120.0,
        )
        // The "About" prefix should be reflected in isApproximate + the tilde.
        assertTrue("Expected isApproximate=true", info.isApproximate)
        assertTrue(
            "Expected display to start with '~', got ${info.formattedDisplay}",
            info.formattedDisplay.startsWith("~"),
        )
    }

    @Test
    fun `extractPrice - returns null for missing or unannounced prices`() {
        assertEquals(null, SpecComparator.extractPrice(null))
        assertEquals(null, SpecComparator.extractPrice(""))
        assertEquals(null, SpecComparator.extractPrice("   "))
        assertEquals(null, SpecComparator.extractPrice("Not announced yet"))
        assertEquals(null, SpecComparator.extractPrice("Coming soon"))
        assertEquals(null, SpecComparator.extractPrice("TBA"))
    }

    @Test
    fun `value-for-money - midrange beats flagship on value despite lower score`() {
        // Midrange: Score ~70, price ~$400.
        val midrange = phoneSpec(
            slug = "midrange",
            name = "Midrange X",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits", "Size" to "6.5 inches"),
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
            camera = mapOf("Triple" to "50 MP", "Features" to "ultrawide, OIS"),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "25W wired"),
            body = mapOf("Dimensions" to "160 x 74 x 7.8 mm", "Weight" to "190 g", "Build" to "IP67"),
            misc = mapOf("Price" to "\$ 399.99"),
        )
        // Flagship: Score ~92, price ~$1300.
        val flagship = phoneSpec(
            slug = "flagship",
            name = "Flagship Z",
            display = mapOf("Type" to "Dynamic LTPO AMOLED 2X, 120Hz, 2500 nits", "Size" to "6.8 inches"),
            platform = mapOf("Chipset" to "Qualcomm SM8750 Snapdragon 8 Elite (3 nm)"),
            camera = mapOf("Quad" to "200 MP", "Features" to "OIS, ultrawide, 5x optical zoom"),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "45W wired"),
            body = mapOf("Dimensions" to "162 x 79 x 8.2 mm", "Weight" to "232 g", "Build" to "IP68"),
            misc = mapOf("Price" to "\$ 1299.99"),
        )
        val verdict = SpecComparator.buildVerdict(midrange, flagship)
        val value = verdict.value
        assertNotNull("Expected ValueAnalysis when both sides are priced", value)
        // The flagship must still win outright on hardware.
        assertEquals("Flagship should win outright on hardware", Winner.B, verdict.winner)
        // But the midrange must win on value-for-money.
        assertEquals("Midrange should win value-for-money", Winner.A, value!!.valueWinner)
        // The ratio should be substantial (midrange delivers more score per dollar).
        val ratio = value.valueRatioA ?: 0.0
        assertTrue(
            "Expected value ratio >= 1.5x, got ${"%.2f".format(ratio)}",
            ratio >= 1.5,
        )
        // The value advantage text should reference the actual device names.
        assertTrue(
            "Expected advantage text to mention Midrange, got: ${value.valueAdvantageText}",
            value.valueAdvantageText?.contains("Midrange", ignoreCase = true) == true,
        )
    }

    @Test
    fun `value-for-money - missing price falls back to outright verdict only`() {
        val a = phoneSpec(
            slug = "a", name = "Phone A",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits", "Size" to "6.5 inches"),
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
            camera = mapOf("Triple" to "50 MP", "Features" to "ultrawide, OIS"),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "25W wired"),
            body = mapOf("Dimensions" to "160 x 74 x 7.8 mm", "Weight" to "190 g", "Build" to "IP67"),
            misc = mapOf("Price" to "\$ 399.99"),
        )
        val b = phoneSpec(
            slug = "b", name = "Phone B",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits", "Size" to "6.5 inches"),
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
            camera = mapOf("Triple" to "50 MP", "Features" to "ultrawide, OIS"),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "25W wired"),
            body = mapOf("Dimensions" to "160 x 74 x 7.8 mm", "Weight" to "190 g", "Build" to "IP67"),
            // No price.
        )
        val verdict = SpecComparator.buildVerdict(a, b)
        val value = verdict.value
        assertNotNull("ValueAnalysis should still be returned (with null valueWinner)", value)
        assertEquals(null, value!!.valueWinner)
        assertEquals(null, value.valueAdvantageText)
        // Outright verdict still computed.
        assertNotNull(verdict.winner)
    }

    @Test
    fun `value-for-money - both unpriced is graceful`() {
        val a = phoneSpec(
            slug = "a", name = "Phone A",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits"),
            misc = mapOf("Price" to "Not announced"),
        )
        val b = phoneSpec(
            slug = "b", name = "Phone B",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits"),
            misc = mapOf("Price" to "Coming soon"),
        )
        val verdict = SpecComparator.buildVerdict(a, b)
        // No crash.
        val value = verdict.value
        assertNotNull(value)
        assertEquals(null, value!!.valueWinner)
    }

    // -----------------------------------------------------------------
    //  Lab benchmarks + whitelist enforcement
    // -----------------------------------------------------------------

    @Test
    fun `extractLabBenchmarks - S25 Ultra fixture parses GB6 9846, AnTuTu 2207809, 1417 nits`() {
        val s25Ultra = phoneSpec(
            slug = "samsung-galaxy-s25-ultra",
            name = "Samsung Galaxy S25 Ultra",
            ourTests = mapOf(
                "Performance" to "AnTuTu: 2207809 (v10)\nGeekBench: 9846 (v6)\n3DMark Wild Life: 17822",
                "Display" to "1417 nits max brightness (measured)",
                "Battery" to "Active use score 14:49h",
            ),
        )
        val lab = SpecComparator.extractLabBenchmarks(s25Ultra.specs)
        assertEquals(9846.0, lab.geekbenchV6!!, 0.001)
        assertEquals(2207809.0, lab.antutuV10!!, 0.001)
        assertEquals(1417.0, lab.measuredNits!!, 0.001)
        assertEquals(14 * 60 + 49, lab.activeUseMinutes)
        assertEquals(17822.0, lab.threeDMarkScore!!, 0.001)
        assertEquals("Wild Life", lab.threeDMarkSuite)
        assertTrue(lab.hasAny)
    }

    @Test
    fun `extractLabBenchmarks - returns all nulls for missing Our Tests section`() {
        val lab = SpecComparator.extractLabBenchmarks(emptyMap())
        assertEquals(null, lab.geekbenchV6)
        assertEquals(null, lab.antutuV10)
        assertEquals(null, lab.threeDMarkScore)
        assertEquals(null, lab.threeDMarkSuite)
        assertEquals(null, lab.measuredNits)
        assertEquals(null, lab.activeUseMinutes)
        assertFalse(lab.hasAny)
    }

    @Test
    fun `extractLabBenchmarks - parses parenthetical 3DMark shape (score then suite)`() {
        val spec = phoneSpec(
            slug = "test-phone",
            name = "Test Phone",
            ourTests = mapOf(
                "Performance" to "AnTuTu: 2207809 (v10)\n\nGeekBench: 9846 (v6)\n\n3DMark: 6687 (Wild Life Extreme)",
            ),
        )
        val lab = SpecComparator.extractLabBenchmarks(spec.specs)
        assertEquals(6687.0, lab.threeDMarkScore!!, 0.001)
        assertEquals("Wild Life Extreme", lab.threeDMarkSuite)
        // The other two metrics still parse correctly alongside the blank lines.
        assertEquals(9846.0, lab.geekbenchV6!!, 0.001)
        assertEquals(2207809.0, lab.antutuV10!!, 0.001)
    }

    @Test
    fun `extractLabBenchmarks - 3DMark absent leaves score and suite null`() {
        val spec = phoneSpec(
            slug = "test-phone",
            name = "Test Phone",
            ourTests = mapOf("Performance" to "GeekBench: 9846 (v6)"),
        )
        val lab = SpecComparator.extractLabBenchmarks(spec.specs)
        assertEquals(null, lab.threeDMarkScore)
        assertEquals(null, lab.threeDMarkSuite)
    }

    @Test
    fun `normalizeWhitespace - collapses NBSP, thin space, and narrow NBSP to plain spaces`() {
        assertEquals("6.7 inches", SpecComparator.normalizeWhitespace("6.7 inches"))
        assertEquals("Dual SIM", SpecComparator.normalizeWhitespace("Dual SIM"))
        assertEquals("IP68 rated", SpecComparator.normalizeWhitespace("IP68 rated"))
    }

    @Test
    fun `normalizeWhitespace - NBSP vs plain-space variants of the same value normalize equal`() {
        val plain = SpecComparator.normalizeWhitespace("6.7 inches")
        val withNbsp = SpecComparator.normalizeWhitespace("6.7 inches")
        assertEquals(plain, withNbsp)
    }

    @Test
    fun `normalizeWhitespace - collapses runs of whitespace and trims`() {
        assertEquals("Dual SIM", SpecComparator.normalizeWhitespace("  Dual   SIM  "))
    }

    @Test
    fun `normalizeWhitespace - null input returns empty string`() {
        assertEquals("", SpecComparator.normalizeWhitespace(null))
    }

    @Test
    fun `buildVerdict - two Geekbench-equipped devices use exact GB6 ratio`() {
        // Both phones reviewed with measured GB6 numbers.
        val flagship = phoneSpec(
            slug = "ultra", name = "Ultra",
            platform = mapOf("Chipset" to "Snapdragon 8 Elite (3 nm)"),
            ourTests = mapOf(
                "Performance" to "GeekBench: 9846 (v6)\nAnTuTu: 2207809 (v10)",
                "Display" to "1417 nits",
                "Battery" to "Active use score 14:49h",
            ),
        )
        val mid = phoneSpec(
            slug = "mid", name = "Mid",
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
            ourTests = mapOf(
                "Performance" to "GeekBench: 5530 (v6)\nAnTuTu: 900000 (v10)",
                "Display" to "1200 nits",
                "Battery" to "Active use score 12:30h",
            ),
        )
        val verdict = SpecComparator.buildVerdict(flagship, mid)
        // Performance: 9846 / 20000 = 49.23; 5530 / 20000 = 27.65. (The
        // reference ceiling is well above today's real-world scores so two
        // high scorers never saturate to an indistinguishable tie at 100 -
        // see geekbench6ToScore.)
        assertEquals(49.23, verdict.performance.scoreA, 0.05)
        assertEquals(27.65, verdict.performance.scoreB, 0.05)
        assertEquals(Winner.A, verdict.performance.winner)
        // Summary must embed the GB6 numbers.
        assertTrue(
            "Performance summary A should mention Geekbench 6, got: ${verdict.performance.summaryA}",
            verdict.performance.summaryA.contains("Geekbench 6"),
        )
        assertTrue(
            "Performance summary A should include raw score 9,846, got: ${verdict.performance.summaryA}",
            verdict.performance.summaryA.contains("9,846"),
        )
    }

    @Test
    fun `buildVerdict - regression - two high-end GB6 scores no longer saturate to a tie`() {
        // Regression test for a real-world report: Samsung Galaxy S26 Ultra
        // (GB6 11,566) vs Apple iPhone 17 Pro Max (GB6 10,118) both cleared
        // the old 10,000 reference ceiling, so geekbench6ToScore() clamped
        // both to 100 and the performance category came back a TIE with no
        // "faster silicon" advantage bullet at all, despite an real ~14%
        // gap between the two devices.
        val s26Ultra = phoneSpec(
            slug = "s26-ultra", name = "Samsung Galaxy S26 Ultra",
            platform = mapOf("Chipset" to "Snapdragon 8 Elite Gen 5 (2 nm)"),
            ourTests = mapOf("Performance" to "GeekBench: 11566 (v6)"),
        )
        val iphone17ProMax = phoneSpec(
            slug = "iphone-17-pro-max", name = "Apple iPhone 17 Pro Max",
            platform = mapOf("Chipset" to "Apple A19 Pro (3 nm)"),
            ourTests = mapOf("Performance" to "GeekBench: 10118 (v6)"),
        )
        val verdict = SpecComparator.buildVerdict(s26Ultra, iphone17ProMax)

        assertEquals(Winner.A, verdict.performance.winner)
        assertTrue(
            "Both scores must stay below the 100 ceiling so they remain distinguishable, " +
                "got A=${verdict.performance.scoreA} B=${verdict.performance.scoreB}",
            verdict.performance.scoreA < 100.0 && verdict.performance.scoreB < 100.0,
        )

        val winnerBullet = verdict.advantagesA.first { it.contains("silicon") }
        // 11566 / 10118 ~= 1.143 -> ~14% faster.
        assertTrue("Expected a ~14% faster bullet, got: $winnerBullet", winnerBullet.contains("14%"))
        assertTrue("Expected 'faster' on the winner's bullet: $winnerBullet", winnerBullet.contains("faster"))
    }

    @Test
    fun `buildVerdict - lab mode falls back to AnTuTu when GB6 missing`() {
        val a = phoneSpec(
            slug = "a", name = "A",
            platform = mapOf("Chipset" to "Snapdragon 8 Gen 3 (4 nm)"),
            ourTests = mapOf("Performance" to "AnTuTu: 1500000 (v10)"),
        )
        val b = phoneSpec(
            slug = "b", name = "B",
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
            ourTests = mapOf("Performance" to "AnTuTu: 900000 (v10)"),
        )
        val verdict = SpecComparator.buildVerdict(a, b)
        // Performance uses AnTuTu 10: A = 1500000/4000000 = 37.5; B = 900000/4000000 = 22.5.
        assertEquals(37.5, verdict.performance.scoreA, 0.01)
        assertEquals(22.5, verdict.performance.scoreB, 0.01)
        assertTrue(verdict.performance.summaryA.contains("AnTuTu v10"))
    }

    @Test
    fun `buildVerdict - lab mode falls back to chipset tier when both unreviewed`() {
        val a = phoneSpec(
            slug = "a", name = "A",
            platform = mapOf("Chipset" to "Apple A18 (3 nm)"),
        )
        val b = phoneSpec(
            slug = "b", name = "B",
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
        )
        val verdict = SpecComparator.buildVerdict(a, b)
        // A18 (97) should beat Exynos 1580 (58) via chipset tier.
        assertEquals(97.0, verdict.performance.scoreA, 0.01)
        assertEquals(58.0, verdict.performance.scoreB, 0.01)
        assertEquals(Winner.A, verdict.performance.winner)
        // Summary is the chipset name, not a lab metric.
        assertTrue(
            "Expected chipset-tier summary, got: ${verdict.performance.summaryA}",
            verdict.performance.summaryA.contains("Apple A18", ignoreCase = true),
        )
    }

    @Test
    fun `compare - GPU key returns TIE with empty delta (whitelisted out)`() {
        val c = SpecComparator.compare(
            "Xclipse 550",
            "Apple GPU (5-core)",
            "GPU",
        )
        assertEquals(Winner.TIE, c.winner)
        assertEquals("", c.deltaText)
    }

    @Test
    fun `compare - non-whitelisted text keys (OS, CPU, Resolution) return TIE with no delta`() {
        // None of these keys are in ALLOWED_DELTA_KEYS.
        listOf("OS", "CPU", "Resolution", "Protection", "Type", "Internal").forEach { key ->
            val c = SpecComparator.compare("Android 15", "iOS 18", key)
            assertEquals("Key '$key' should TIE", Winner.TIE, c.winner)
            assertEquals("Key '$key' should have empty delta", "", c.deltaText)
        }
    }

    @Test
    fun `isAllowedDeltaKey - only size, weight, charging, price are allowed`() {
        // Whitelisted keys
        assertTrue(SpecComparator.isAllowedDeltaKey("Size"))
        assertTrue(SpecComparator.isAllowedDeltaKey("weight"))
        assertTrue(SpecComparator.isAllowedDeltaKey("Charging"))
        assertTrue(SpecComparator.isAllowedDeltaKey("Battery Charging")) // substring match
        assertTrue(SpecComparator.isAllowedDeltaKey("Price"))
        // Non-whitelisted keys
        assertFalse(SpecComparator.isAllowedDeltaKey("GPU"))
        assertFalse(SpecComparator.isAllowedDeltaKey("CPU"))
        assertFalse(SpecComparator.isAllowedDeltaKey("Chipset"))
        assertFalse(SpecComparator.isAllowedDeltaKey("OS"))
        assertFalse(SpecComparator.isAllowedDeltaKey("Resolution"))
        assertFalse(SpecComparator.isAllowedDeltaKey("Dimensions"))
        assertFalse(SpecComparator.isAllowedDeltaKey("Internal"))
    }

    // -----------------------------------------------------------------
    //  Scoring Presets
    // -----------------------------------------------------------------

    @Test
    fun `ScoringPreset - BALANCED weights sum to 1_0 and equal the legacy 25 25 20 20 10`() {
        val preset = SpecComparator.ScoringPreset.BALANCED
        val sum = preset.displayWeight + preset.cameraWeight + preset.performanceWeight +
            preset.batteryWeight + preset.buildWeight
        assertEquals(1.0, sum, 0.0001)
        assertEquals(0.25, preset.displayWeight, 0.0001)
        assertEquals(0.25, preset.cameraWeight, 0.0001)
        assertEquals(0.20, preset.performanceWeight, 0.0001)
        assertEquals(0.20, preset.batteryWeight, 0.0001)
        assertEquals(0.10, preset.buildWeight, 0.0001)
    }

    @Test
    fun `ScoringPreset - GAMER_PERFORMANCE re-weights display and performance upward`() {
        val preset = SpecComparator.ScoringPreset.GAMER_PERFORMANCE
        val sum = preset.displayWeight + preset.cameraWeight + preset.performanceWeight +
            preset.batteryWeight + preset.buildWeight
        assertEquals("All preset weights must sum to 1.0", 1.0, sum, 0.0001)
        assertTrue(
            "GAMER_PERFORMANCE must weight display >= 0.30, got ${preset.displayWeight}",
            preset.displayWeight >= 0.30,
        )
        assertTrue(
            "GAMER_PERFORMANCE must weight performance >= 0.35, got ${preset.performanceWeight}",
            preset.performanceWeight >= 0.35,
        )
    }

    @Test
    fun `ScoringPreset - CAMERA_CREATOR re-weights camera upward`() {
        val preset = SpecComparator.ScoringPreset.CAMERA_CREATOR
        val sum = preset.displayWeight + preset.cameraWeight + preset.performanceWeight +
            preset.batteryWeight + preset.buildWeight
        assertEquals(1.0, sum, 0.0001)
        assertTrue(
            "CAMERA_CREATOR must weight camera >= 0.40, got ${preset.cameraWeight}",
            preset.cameraWeight >= 0.40,
        )
    }

    @Test
    fun `ScoringPreset - BATTERY_ROAD_WARRIOR re-weights battery upward`() {
        val preset = SpecComparator.ScoringPreset.BATTERY_ROAD_WARRIOR
        val sum = preset.displayWeight + preset.cameraWeight + preset.performanceWeight +
            preset.batteryWeight + preset.buildWeight
        assertEquals(1.0, sum, 0.0001)
        assertTrue(
            "BATTERY_ROAD_WARRIOR must weight battery >= 0.40, got ${preset.batteryWeight}",
            preset.batteryWeight >= 0.40,
        )
    }

    @Test
    fun `buildVerdict - GAMER_PERFORMANCE shifts composite to favor a high-refresh SoC flagship`() {
        val a = phoneSpec(
            slug = "gamer", name = "Gamer X",
            display = mapOf("Type" to "LTPO AMOLED, 165Hz, 2400 nits"),
            platform = mapOf("Chipset" to "Qualcomm SM8750 Snapdragon 8 Elite (3 nm)"),
            camera = mapOf("Triple" to "50 MP", "Features" to "ultrawide"),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "120W wired"),
            body = mapOf("Dimensions" to "160 x 75 x 8.5 mm", "Weight" to "210 g", "Build" to "IP54"),
        )
        val b = phoneSpec(
            slug = "creator", name = "Creator Y",
            display = mapOf("Type" to "AMOLED, 120Hz, 1500 nits"),
            platform = mapOf("Chipset" to "Exynos 2400e (4 nm)"),
            camera = mapOf(
                "Quad" to "200 MP",
                "Features" to "5x optical zoom, ultrawide, OIS, periscope",
            ),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "45W wired"),
            body = mapOf("Dimensions" to "160 x 75 x 8.0 mm", "Weight" to "195 g", "Build" to "IP68"),
        )

        val balanced = SpecComparator.buildVerdict(a, b, preset = SpecComparator.ScoringPreset.BALANCED)
        val gamer = SpecComparator.buildVerdict(a, b, preset = SpecComparator.ScoringPreset.GAMER_PERFORMANCE)

        // Under GAMER_PERFORMANCE, the Gamer's lead (driven by SoC + Display) widens.
        val balancedGap = balanced.overallScoreA - balanced.overallScoreB
        val gamerGap = gamer.overallScoreA - gamer.overallScoreB
        assertTrue(
            "GAMER preset should *widen* the lead for the SoC+Display device. " +
                "Balanced gap=$balancedGap, Gamer gap=$gamerGap",
            gamerGap > balancedGap,
        )
        assertEquals("Verdict must echo the preset", SpecComparator.ScoringPreset.GAMER_PERFORMANCE, gamer.preset)
    }

    @Test
    fun `buildVerdict - CAMERA_CREATOR prioritizes the multi-camera device`() {
        val a = phoneSpec(
            slug = "mid", name = "Mid A",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits"),
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
            camera = mapOf("Triple" to "50 MP", "Features" to "ultrawide"),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "25W wired"),
            body = mapOf("Dimensions" to "160 x 74 x 7.8 mm", "Weight" to "190 g", "Build" to "IP67"),
        )
        val b = phoneSpec(
            slug = "flagship", name = "Flagship B",
            display = mapOf("Type" to "Dynamic LTPO AMOLED 2X, 120Hz, 2500 nits", "Size" to "6.8 inches"),
            platform = mapOf("Chipset" to "Qualcomm SM8750 Snapdragon 8 Elite (3 nm)"),
            camera = mapOf(
                "Quad" to "200 MP",
                "Features" to "OIS, ultrawide, 5x optical zoom, periscope",
            ),
            battery = mapOf("Type" to "Li-Ion 5000 mAh", "Charging" to "45W wired"),
            body = mapOf("Dimensions" to "162 x 79 x 8.2 mm", "Weight" to "232 g", "Build" to "IP68"),
        )
        val balanced = SpecComparator.buildVerdict(a, b, preset = SpecComparator.ScoringPreset.BALANCED)
        val creator = SpecComparator.buildVerdict(
            a, b, preset = SpecComparator.ScoringPreset.CAMERA_CREATOR,
        )

        assertEquals(Winner.B, balanced.winner)
        assertEquals(Winner.B, creator.winner)
        val balancedGap = balanced.overallScoreB - balanced.overallScoreA
        val creatorGap = creator.overallScoreB - creator.overallScoreA
        assertTrue(
            "CAMERA_CREATOR should widen the lead for the multi-camera device. " +
                "Balanced gap=$balancedGap, Creator gap=$creatorGap",
            creatorGap > balancedGap,
        )
    }

    // -----------------------------------------------------------------
    //  Legacy-depreciation toggle
    // -----------------------------------------------------------------

    @Test
    fun `isLegacySilicon - flags 7nm+ chipsets as legacy`() {
        val legacy = phoneSpec(
            slug = "old", name = "Old Phone",
            platform = mapOf("Chipset" to "Qualcomm SM7125 Snapdragon 720G (8 nm)"),
        )
        val modern = phoneSpec(
            slug = "new", name = "Modern Phone",
            platform = mapOf("Chipset" to "Qualcomm SM8750 Snapdragon 8 Elite (3 nm)"),
        )
        val noNode = phoneSpec(
            slug = "x", name = "Unknown",
            platform = mapOf("Chipset" to "Some unknown chipset"),
        )
        assertTrue(SpecComparator.isLegacySilicon(legacy))
        assertFalse(SpecComparator.isLegacySilicon(modern))
        assertFalse(SpecComparator.isLegacySilicon(noNode))
    }

    @Test
    fun `buildVerdict - excludeLegacy drops the value score for legacy silicon`() {
        val legacy = phoneSpec(
            slug = "old", name = "Galaxy S10 (2019)",
            platform = mapOf("Chipset" to "Exynos 9820 (8 nm)"),
            misc = mapOf("Price" to "\$ 400"),
        )
        val modern = phoneSpec(
            slug = "new", name = "Galaxy A57",
            platform = mapOf("Chipset" to "Exynos 1580 (4 nm)"),
            misc = mapOf("Price" to "\$ 400"),
        )
        val withLegacy = SpecComparator.buildVerdict(legacy, modern, excludeLegacy = true)
        val withoutLegacy = SpecComparator.buildVerdict(legacy, modern, excludeLegacy = false)
        val gapWithout = (withoutLegacy.value?.valueScoreB ?: 0.0) - (withoutLegacy.value?.valueScoreA ?: 0.0)
        val gapWith = (withLegacy.value?.valueScoreB ?: 0.0) - (withLegacy.value?.valueScoreA ?: 0.0)
        assertTrue(
            "Legacy-depreciation must widen the value gap in favor of modern. " +
                "Without=$gapWithout, With=$gapWith",
            gapWith > gapWithout,
        )
    }

    // -----------------------------------------------------------------
    //  Selfie + connectivity / sound feature scoring
    // -----------------------------------------------------------------

    @Test
    fun `scoreCamera - selfie 4K video contributes to the camera versatility score`() {
        // Device A: solid triple rear, no selfie 4K.
        // Device B: weaker rear BUT selfie shoots 4K + 12MP PDAF.
        val a = phoneSpec(
            slug = "a", name = "A",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits"),
            camera = mapOf(
                "Triple" to "50 MP, f/1.8 (wide), ultrawide, OIS",
                "Features" to "ultrawide, OIS",
                "Video" to "4K@60fps, 1080p@30fps",
            ),
            selfie = mapOf("Single" to "16 MP, f/2.4", "Video" to "1080p@30fps"),
        )
        val b = phoneSpec(
            slug = "b", name = "B",
            display = mapOf("Type" to "AMOLED, 120Hz, 1000 nits"),
            camera = mapOf(
                "Triple" to "48 MP, f/1.7 (wide), 8 MP ultrawide",
                "Features" to "ultrawide",
                "Video" to "1080p@60fps",
            ),
            selfie = mapOf(
                "Single" to "12 MP, f/2.2, PDAF",
                "Video" to "4K@30fps, 1080p@120fps",
            ),
        )
        val noBonusA = phoneSpec(
            slug = "na", name = "A (no selfie bonus)",
            display = a.specs["Display"]!!,
            camera = a.specs["Main Camera"]!!,
        )
        val noBonusB = phoneSpec(
            slug = "nb", name = "B (no selfie bonus)",
            display = b.specs["Display"]!!,
            camera = b.specs["Main Camera"]!!,
        )
        val baseline = SpecComparator.buildVerdict(noBonusA, noBonusB)
        val withBonus = SpecComparator.buildVerdict(a, b)
        val baseGap = baseline.camera.scoreA - baseline.camera.scoreB
        val newGap = withBonus.camera.scoreA - withBonus.camera.scoreB
        assertTrue(
            "Selfie 4K + 12MP PDAF should narrow the rear-camera gap. " +
                "Base=$baseGap, With=$newGap",
            newGap < baseGap,
        )
        val bNoBonus = baseline.camera.scoreB
        val bWithBonus = withBonus.camera.scoreB
        assertTrue(
            "Selfie 4K should award B at least +5 pts (got ${bWithBonus - bNoBonus})",
            bWithBonus - bNoBonus >= 5.0,
        )
    }

    @Test
    fun `compare - new metadata rows (USB, WLAN, 3_5mm jack, Sensors) return TIE with empty delta`() {
        // Use raw text values (no parseable numbers) so the comparator
        // cannot accidentally extract a numeric delta; every key here is
        // whitelisted out and must return TIE + empty delta.
        val pairs = listOf(
            "USB" to ("USB Type-C 3.2" to "USB Type-C 2.0"),
            "WLAN" to ("Wi-Fi a/b/g/n/ac/ax (tri-band)" to "Wi-Fi a/b/g/n/ac (dual-band)"),
            "Bluetooth" to ("v5.3" to "v5.2"),
            "NFC" to ("Yes" to "No"),
            "Sensors" to (
                "Fingerprint (under display, ultrasonic), accelerometer" to
                    "Fingerprint (under display, optical), accelerometer"
                ),
            "3.5mm jack" to ("Yes" to "No"),
            "Loudspeaker" to ("Yes, with stereo speakers" to "Yes"),
            "Announced" to ("2024-01" to "2023-09"),
            "Status" to ("Available. Released 2024" to "Available. Released 2023"),
        )
        for ((key, values) in pairs) {
            val (a, b) = values
            val c = SpecComparator.compare(a, b, key)
            assertEquals("Key '$key' must TIE", Winner.TIE, c.winner)
            // Non-whitelisted keys render as clean side-by-side text with
            // NO subtraction badge: either deltaText is "" (whitelist-out
            // path) or the legacy "—" placeholder (no numeric data).
            assertTrue(
                "Key '$key' must have non-subtractive delta (got '${c.deltaText}')",
                c.deltaText.isBlank() || c.deltaText == "—",
            )
        }
    }

    @Test
    fun `isAllowedDeltaKey - new metadata keys (USB, WLAN, Sensors) are whitelisted out`() {
        val blocked = listOf(
            "USB", "WLAN", "Bluetooth", "NFC", "Positioning", "Radio",
            "Sensors", "3.5mm jack", "Loudspeaker",
            "Announced", "Status", "Free fall", "Repairability", "Energy",
            "Models", "Colors", "SAR", "SAR EU", "OS", "CPU", "GPU", "Chipset",
        )
        for (k in blocked) {
            assertFalse(
                "Key '$k' must be whitelisted out (no delta badge)",
                SpecComparator.isAllowedDeltaKey(k),
            )
        }
        for (k in listOf("Size", "Weight", "Charging", "Price")) {
            assertTrue(
                "Key '$k' must be allowed (delta badge permitted)",
                SpecComparator.isAllowedDeltaKey(k),
            )
        }
    }

    @Test
    fun `buildVerdict - USB Type-C 3 boosts the build score by 3 pts`() {
        val fast = phoneSpec(
            slug = "fast", name = "FastUSB",
            body = mapOf("Dimensions" to "160 x 74 x 7.8 mm", "Weight" to "190 g", "Build" to "IP68"),
            comms = mapOf("USB" to "USB Type-C 3.2, DisplayPort"),
        )
        val slow = phoneSpec(
            slug = "slow", name = "SlowUSB",
            body = mapOf("Dimensions" to "160 x 74 x 7.8 mm", "Weight" to "190 g", "Build" to "IP68"),
            comms = mapOf("USB" to "USB Type-C 2.0"),
        )
        val withFast = SpecComparator.buildVerdict(fast, slow)
        val noFast = SpecComparator.buildVerdict(
            fast.copy(specs = fast.specs.toMutableMap().also { it["Comms"] = emptyMap() }),
            slow,
        )
        val delta = withFast.build.scoreA - noFast.build.scoreA
        assertTrue(
            "USB Type-C 3.x should add ~3 pts to build score; got +$delta",
            delta >= 2.99 && delta <= 3.01,
        )
    }

    @Test
    fun `SpecMatrixBuilder - CATEGORY_DEFINITIONS contains all 14 expected sections`() {
        val defs = com.example.specclash.ui.home.components.SpecMatrixBuilder.CATEGORY_DEFINITIONS
        val titles = defs.map { it.title }
        val expected = listOf(
            "Display",
            "Platform & Performance",
            "Memory & Storage",
            "Rear Cameras",
            "Front / Selfie Camera",
            "Sound & Audio",
            "Connectivity & Comms",
            "Sensors & Biometrics",
            "Battery & Charging",
            "Body & Dimensions",
            "Release & Status",
            "Durability & Repairability",
            "Lab Benchmarks",
            "Misc & Pricing",
        )
        assertEquals(expected, titles)
    }

    @Test
    fun `SpecMatrixBuilder - defaultOpen is true for the 5 primary sections and false for Launch + EU Label`() {
        val defs = com.example.specclash.ui.home.components.SpecMatrixBuilder.CATEGORY_DEFINITIONS
        val byTitle = defs.associateBy { it.title }

        // Primary sections default open.
        for (open in listOf("Display", "Platform & Performance", "Rear Cameras", "Battery & Charging", "Misc & Pricing")) {
            assertTrue("'$open' should default open", byTitle.getValue(open).defaultOpen)
        }
        // Secondary metadata sections default collapsed.
        for (closed in listOf("Release & Status", "Durability & Repairability")) {
            assertFalse("'$closed' should default collapsed", byTitle.getValue(closed).defaultOpen)
        }
    }

    // -----------------------------------------------------------------
    //  Test fixtures
    // -----------------------------------------------------------------

    private fun phoneSpec(
        slug: String,
        name: String,
        display: Map<String, String> = emptyMap(),
        platform: Map<String, String> = emptyMap(),
        memory: Map<String, String> = emptyMap(),
        camera: Map<String, String> = emptyMap(),
        battery: Map<String, String> = emptyMap(),
        body: Map<String, String> = emptyMap(),
        misc: Map<String, String> = emptyMap(),
        ourTests: Map<String, String> = emptyMap(),
        comms: Map<String, String> = emptyMap(),
        sound: Map<String, String> = emptyMap(),
        selfie: Map<String, String> = emptyMap(),
    ): PhoneSpec = PhoneSpec(
        slug = slug,
        name = name,
        image = "",
        specs = mapOf(
            "Display" to display,
            "Platform" to platform,
            "Memory" to memory,
            "Main Camera" to camera,
            "Battery" to battery,
            "Body" to body,
            "Misc" to misc,
            "Our Tests" to ourTests,
            "Comms" to comms,
            "Sound" to sound,
            "Selfie camera" to selfie,
        ),
    )
}
