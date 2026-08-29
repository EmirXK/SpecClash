package com.example.specclash.ui.home.components

import com.example.specclash.domain.PhoneSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecMatrixBuilderTest {

    private fun spec(name: String, weightG: String): PhoneSpec = PhoneSpec(
        slug = name.lowercase().replace(" ", "-"),
        name = name,
        image = "",
        specs = mapOf("Body" to mapOf("Weight" to weightG)),
    )

    private val bodyCategory = SpecMatrixBuilder.categories.first { it.title == "Body & Dimensions" }

    @Test
    fun `buildRows produces a clean 2-way comparison row per key`() {
        val a = spec("Phone A", "179 g")
        val b = spec("Phone B", "177 g")

        val rows = SpecMatrixBuilder.buildRows(a, b, bodyCategory, differencesOnly = false)
        val weightRow = rows.first { it.key == "Weight" }

        assertEquals("179 g", weightRow.valueA)
        assertEquals("177 g", weightRow.valueB)
    }

    @Test
    fun `buildRows differencesOnly drops rows where both devices match`() {
        val a = spec("Phone A", "177 g")
        val b = spec("Phone B", "177 g")

        val rows = SpecMatrixBuilder.buildRows(a, b, bodyCategory, differencesOnly = true)

        assertEquals(0, rows.count { it.key == "Weight" })
    }

    @Test
    fun `buildRows skips keys missing from both devices`() {
        val a = spec("Phone A", "177 g")
        val b = spec("Phone B", "177 g")

        val rows = SpecMatrixBuilder.buildRows(a, b, bodyCategory, differencesOnly = false)

        // "Dimensions", "Build", and "SIM" are absent from both fixtures, so
        // only the "Weight" row (present on both) should be emitted.
        assertTrue(rows.all { it.key == "Weight" })
    }

    @Test
    fun `buildRows differencesOnly drops rows differing only by case`() {
        val a = bodySpec("Phone A", build = "Glass front, aluminum frame")
        val b = bodySpec("Phone B", build = "GLASS FRONT, ALUMINUM FRAME")

        val rows = SpecMatrixBuilder.buildRows(a, b, bodyCategory, differencesOnly = true)

        assertEquals(0, rows.count { it.key == "Build" })
    }

    @Test
    fun `buildRows differencesOnly treats null and whitespace-only as the same`() {
        val a = bodySpec("Phone A", build = null)
        val b = bodySpec("Phone B", build = "   ")

        val hiddenWhenFiltered = SpecMatrixBuilder.buildRows(a, b, bodyCategory, differencesOnly = true)
        val shownWhenUnfiltered = SpecMatrixBuilder.buildRows(a, b, bodyCategory, differencesOnly = false)

        assertEquals(0, hiddenWhenFiltered.count { it.key == "Build" })
        assertEquals(1, shownWhenUnfiltered.count { it.key == "Build" })
    }

    @Test
    fun `buildRows builds separate Geekbench 6, AnTuTu v10, and 3DMark rows from the Performance blob`() {
        val performanceBlob = "AnTuTu: 2207809 (v10)\n\nGeekBench: 9846 (v6)\n\n3DMark: 6687 (Wild Life Extreme)"
        val a = ourTestsSpec("Phone A", performance = performanceBlob)
        val b = ourTestsSpec("Phone B", performance = null)

        val labCategory = SpecMatrixBuilder.categories.first { it.title == "Lab Benchmarks" }
        val rows = SpecMatrixBuilder.buildRows(a, b, labCategory, differencesOnly = false)
        val byKey = rows.associateBy { it.key }

        assertEquals("9,846 (v6)", byKey.getValue("Geekbench 6").valueA)
        assertEquals(null, byKey.getValue("Geekbench 6").valueB)
        assertEquals("2,207,809 (v10)", byKey.getValue("AnTuTu v10").valueA)
        assertEquals("6,687 (Wild Life Extreme)", byKey.getValue("3DMark").valueA)
    }

    @Test
    fun `buildRows omits Lab Benchmark rows entirely when Our Tests is absent on both sides`() {
        val a = ourTestsSpec("Phone A", performance = null)
        val b = ourTestsSpec("Phone B", performance = null)

        val labCategory = SpecMatrixBuilder.categories.first { it.title == "Lab Benchmarks" }
        val rows = SpecMatrixBuilder.buildRows(a, b, labCategory, differencesOnly = false)

        assertEquals(0, rows.count { it.key == "Geekbench 6" || it.key == "AnTuTu v10" || it.key == "3DMark" })
    }

    @Test
    fun `buildRows differencesOnly hides a Lab Benchmark row when both devices score identically`() {
        val blob = "GeekBench: 9846 (v6)"
        val a = ourTestsSpec("Phone A", performance = blob)
        val b = ourTestsSpec("Phone B", performance = blob)

        val labCategory = SpecMatrixBuilder.categories.first { it.title == "Lab Benchmarks" }
        val rows = SpecMatrixBuilder.buildRows(a, b, labCategory, differencesOnly = true)

        assertEquals(0, rows.count { it.key == "Geekbench 6" })
    }

    @Test
    fun `buildRows unifies mismatched lens-count keys into a single Rear Sensors row`() {
        val quadBlob = "200 MP, f/1.7, 23mm (wide)\n\n10 MP, f/2.4, 67mm (telephoto)\n\n" +
            "50 MP, f/3.4, 111mm (periscope telephoto)\n\n50 MP, f/1.9, 120° (ultrawide)"
        val tripleBlob = "48 MP, f/1.8, 24mm (wide)\n\n12 MP, f/2.8, 52mm (telephoto)\n\n12 MP, f/2.2, 13mm (ultrawide)"
        // A has a Quad rear camera, B has a Triple - different upstream keys
        // that must still land on the same "Rear Sensors" row side-by-side,
        // never as separate rows with a "—" on the other side.
        val a = mainCameraSpec("Phone A", quadKey = "Quad", quadValue = quadBlob)
        val b = mainCameraSpec("Phone B", quadKey = "Triple", quadValue = tripleBlob)

        val rearCategory = SpecMatrixBuilder.categories.first { it.title == "Rear Cameras" }
        val rows = SpecMatrixBuilder.buildRows(a, b, rearCategory, differencesOnly = false)
        val sensorsRow = rows.first { it.key == "Rear Sensors" }

        // Each lens becomes its own labelled bullet, blank-line separated
        // (not clumped together) so multi-lens rows stay readable.
        assertEquals(
            "• Wide: 200 MP, f/1.7, 23mm (wide)\n\n" +
                "• Telephoto: 10 MP, f/2.4, 67mm (telephoto)\n\n" +
                "• Periscope Telephoto: 50 MP, f/3.4, 111mm (periscope telephoto)\n\n" +
                "• Ultrawide: 50 MP, f/1.9, 120° (ultrawide)",
            sensorsRow.valueA,
        )
        assertEquals(
            "• Wide: 48 MP, f/1.8, 24mm (wide)\n\n" +
                "• Telephoto: 12 MP, f/2.8, 52mm (telephoto)\n\n" +
                "• Ultrawide: 12 MP, f/2.2, 13mm (ultrawide)",
            sensorsRow.valueB,
        )
        // No more separate "Single"/"Dual"/"Triple"/"Quad" rows.
        assertEquals(0, rows.count { it.key in setOf("Single", "Dual", "Triple", "Quad") })
    }

    @Test
    fun `buildRows leaves a plain single-lens value untouched with no label prefix`() {
        val a = mainCameraSpec("Phone A", quadKey = "Single", quadValue = "50 MP, f/1.8, OIS")
        val b = mainCameraSpec("Phone B", quadKey = "Single", quadValue = "48 MP, f/1.9, OIS")

        val rearCategory = SpecMatrixBuilder.categories.first { it.title == "Rear Cameras" }
        val rows = SpecMatrixBuilder.buildRows(a, b, rearCategory, differencesOnly = false)
        val sensorsRow = rows.first { it.key == "Rear Sensors" }

        assertEquals("50 MP, f/1.8, OIS", sensorsRow.valueA)
        assertEquals("48 MP, f/1.9, OIS", sensorsRow.valueB)
    }

    @Test
    fun `buildRows Rear Sensors prefers Quad over Triple, Dual, and Single when multiple keys exist`() {
        val a = PhoneSpec(
            slug = "phone-a",
            name = "Phone A",
            image = "",
            specs = mapOf(
                "Main Camera" to mapOf(
                    "Single" to "should not be picked",
                    "Dual" to "should not be picked",
                    "Triple" to "should not be picked",
                    "Quad" to "200 MP wide, f/1.7",
                ),
            ),
        )
        val b = PhoneSpec(slug = "phone-b", name = "Phone B", image = "", specs = emptyMap())

        val rearCategory = SpecMatrixBuilder.categories.first { it.title == "Rear Cameras" }
        val rows = SpecMatrixBuilder.buildRows(a, b, rearCategory, differencesOnly = false)
        val sensorsRow = rows.first { it.key == "Rear Sensors" }

        assertEquals("200 MP wide, f/1.7", sensorsRow.valueA)
    }

    @Test
    fun `buildRows resolves Front Sensor, Front Video, and Front Features from Selfie Camera`() {
        val a = selfieCameraSpec(
            "Phone A",
            selfieKey = "Selfie Camera",
            single = "12 MP, f/2.2",
            video = "4K@60fps",
            features = "HDR, Panorama",
        )
        val b = selfieCameraSpec(
            "Phone B",
            // Upstream casing has drifted before; the alias fallback should
            // still resolve this to the same "Front Sensor" row.
            selfieKey = "Selfie camera",
            single = "32 MP, f/2.4",
            video = "1080p@30fps",
            features = null,
        )

        val frontCategory = SpecMatrixBuilder.categories.first { it.title == "Front / Selfie Camera" }
        val rows = SpecMatrixBuilder.buildRows(a, b, frontCategory, differencesOnly = false)
        val byKey = rows.associateBy { it.key }

        assertEquals("12 MP, f/2.2", byKey.getValue("Front Sensor").valueA)
        assertEquals("32 MP, f/2.4", byKey.getValue("Front Sensor").valueB)
        assertEquals("4K@60fps", byKey.getValue("Front Video").valueA)
        assertEquals("HDR, Panorama", byKey.getValue("Front Features").valueA)
        assertEquals(null, byKey.getValue("Front Features").valueB)
    }

    @Test
    fun `buildRows resolves the four EU Label metrics under Durability & Repairability`() {
        val a = euLabelSpec(
            "Phone A",
            battery = "2,000 cycles",
            freeFall = "Class A (270 falls)",
            repairability = "Class C",
            energy = "Class A",
        )
        val b = euLabelSpec("Phone B", battery = null, freeFall = null, repairability = null, energy = null)

        val durabilityCategory = SpecMatrixBuilder.categories.first { it.title == "Durability & Repairability" }
        val rows = SpecMatrixBuilder.buildRows(a, b, durabilityCategory, differencesOnly = false)
        val byKey = rows.associateBy { it.key }

        assertEquals("2,000 cycles", byKey.getValue("Battery Longevity").valueA)
        assertEquals("Class A (270 falls)", byKey.getValue("Drop Resistance (Free Fall)").valueA)
        assertEquals("Class C", byKey.getValue("Repairability Index").valueA)
        assertEquals("Class A", byKey.getValue("Energy Efficiency").valueA)
    }

    @Test
    fun `buildRows omits EU Label rows entirely when neither device has an EU Label`() {
        val a = PhoneSpec(slug = "phone-a", name = "Phone A", image = "", specs = emptyMap())
        val b = PhoneSpec(slug = "phone-b", name = "Phone B", image = "", specs = emptyMap())

        val durabilityCategory = SpecMatrixBuilder.categories.first { it.title == "Durability & Repairability" }
        val rows = SpecMatrixBuilder.buildRows(a, b, durabilityCategory, differencesOnly = false)

        assertEquals(0, rows.size)
    }

    private fun mainCameraSpec(name: String, quadKey: String, quadValue: String): PhoneSpec = PhoneSpec(
        slug = name.lowercase().replace(" ", "-"),
        name = name,
        image = "",
        specs = mapOf("Main Camera" to mapOf(quadKey to quadValue)),
    )

    private fun selfieCameraSpec(
        name: String,
        selfieKey: String,
        single: String?,
        video: String?,
        features: String?,
    ): PhoneSpec {
        val selfie = buildMap {
            single?.let { put("Single", it) }
            video?.let { put("Video", it) }
            features?.let { put("Features", it) }
        }
        return PhoneSpec(
            slug = name.lowercase().replace(" ", "-"),
            name = name,
            image = "",
            specs = mapOf(selfieKey to selfie),
        )
    }

    private fun euLabelSpec(
        name: String,
        battery: String?,
        freeFall: String?,
        repairability: String?,
        energy: String?,
    ): PhoneSpec {
        val euLabel = buildMap {
            battery?.let { put("Battery", it) }
            freeFall?.let { put("Free fall", it) }
            repairability?.let { put("Repairability", it) }
            energy?.let { put("Energy", it) }
        }
        return PhoneSpec(
            slug = name.lowercase().replace(" ", "-"),
            name = name,
            image = "",
            specs = mapOf("EU LABEL" to euLabel),
        )
    }

    private fun bodySpec(name: String, build: String?): PhoneSpec = PhoneSpec(
        slug = name.lowercase().replace(" ", "-"),
        name = name,
        image = "",
        specs = mapOf("Body" to (build?.let { mapOf("Build" to it) } ?: emptyMap())),
    )

    private fun ourTestsSpec(name: String, performance: String?): PhoneSpec = PhoneSpec(
        slug = name.lowercase().replace(" ", "-"),
        name = name,
        image = "",
        specs = mapOf("Our Tests" to (performance?.let { mapOf("Performance" to it) } ?: emptyMap())),
    )
}
