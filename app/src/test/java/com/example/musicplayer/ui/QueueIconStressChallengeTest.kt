package com.example.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.InflaterInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Empirical Adversarial Stress Test Suite for Milestone 2 (R6 Custom Queue Vector Icon and R1 Cover Asset).
 * Authored by Challenger M2-2.
 * Pure Kotlin implementation without java.awt to guarantee full Android SDK compile compatibility.
 */
class QueueIconStressChallengeTest {

    private val projectRoot: File by lazy {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (!File(current, "app").exists() && current.parentFile != null) {
            current = current.parentFile!!
        }
        current
    }

    private val vectorIconFile: File by lazy {
        File(projectRoot, "app/src/main/res/drawable/ic_queue_music_custom.xml")
    }

    private val favoritesCoverFile: File by lazy {
        File(projectRoot, "app/src/main/res/drawable-nodpi/cover_favorites.png")
    }

    // ---------------------------------------------------------------------------------------------
    // 1. AAPT2 & Vector XML Schema Integrity
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testVectorXmlSchemaAndAaptConformance() {
        assertTrue("ic_queue_music_custom.xml must exist", vectorIconFile.exists())
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(vectorIconFile)

        val root = doc.documentElement
        assertEquals("Root element must be 'vector'", "vector", root.tagName)
        assertEquals("xmlns:android must be valid schema", "http://schemas.android.com/apk/res/android", root.getAttribute("xmlns:android"))
        assertEquals("Width must be 24dp", "24dp", root.getAttribute("android:width"))
        assertEquals("Height must be 24dp", "24dp", root.getAttribute("android:height"))
        assertEquals("ViewportWidth must be 24", "24", root.getAttribute("android:viewportWidth"))
        assertEquals("ViewportHeight must be 24", "24", root.getAttribute("android:viewportHeight"))

        val pathList = root.getElementsByTagName("path")
        assertTrue("Vector must contain at least 4 distinct paths (3 bars + 1 plus symbol)", pathList.length >= 4)

        for (i in 0 until pathList.length) {
            val path = pathList.item(i) as org.w3c.dom.Element
            val fillColor = path.getAttribute("android:fillColor")
            assertTrue(
                "Path $i must use pure white fill (#FFFFFFFF or #FFFFFF) to support SrcIn tinting, found: '$fillColor'",
                fillColor.equals("#FFFFFFFF", ignoreCase = true) || fillColor.equals("#FFFFFF", ignoreCase = true)
            )
            val pathData = path.getAttribute("android:pathData")
            assertTrue("Path $i must contain non-empty pathData", pathData.isNotBlank())
            assertTrue("Path $i must be properly closed with 'z'", pathData.trim().endsWith("z", ignoreCase = true))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Geometry Bounding Box & Zero-Clipping Stress Test
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testVectorGeometryBoundsZeroClipping() {
        val paths = parseAllVectorPaths(vectorIconFile)
        assertEquals("Should parse exactly 4 paths from vector", 4, paths.size)

        var globalMinX = Double.MAX_VALUE
        var globalMaxX = Double.MIN_VALUE
        var globalMinY = Double.MAX_VALUE
        var globalMaxY = Double.MIN_VALUE

        paths.forEachIndexed { index, path ->
            val bounds = path.bounds
            assertFalse("Path $index bounds must not be empty", bounds.isEmpty)
            globalMinX = min(globalMinX, bounds.minX)
            globalMaxX = max(globalMaxX, bounds.maxX)
            globalMinY = min(globalMinY, bounds.minY)
            globalMaxY = max(globalMaxY, bounds.maxY)
        }

        // Viewport is [0, 24] x [0, 24]
        assertTrue("Global minX ($globalMinX) must be >= 0.0 (inside viewport)", globalMinX >= 0.0)
        assertTrue("Global maxX ($globalMaxX) must be <= 24.0 (inside viewport)", globalMaxX <= 24.0)
        assertTrue("Global minY ($globalMinY) must be >= 0.0 (inside viewport)", globalMinY >= 0.0)
        assertTrue("Global maxY ($globalMaxY) must be <= 24.0 (inside viewport)", globalMaxY <= 24.0)

        // Safety margins (minimum 1.5dp margin on all sides prevents subpixel clipping)
        val leftMargin = globalMinX
        val rightMargin = 24.0 - globalMaxX
        val topMargin = globalMinY
        val bottomMargin = 24.0 - globalMaxY

        assertTrue("Left margin ($leftMargin dp) must be >= 1.5dp", leftMargin >= 1.5)
        assertTrue("Right margin ($rightMargin dp) must be >= 1.5dp", rightMargin >= 1.5)
        assertTrue("Top margin ($topMargin dp) must be >= 1.5dp", topMargin >= 1.5)
        assertTrue("Bottom margin ($bottomMargin dp) must be >= 1.5dp", bottomMargin >= 1.5)
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Stroke Uniformity, Proportions & Symmetry
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testVectorUniformThicknessAndSymmetry() {
        val paths = parseAllVectorPaths(vectorIconFile)
        val bar1Bounds = paths[0].bounds
        val bar2Bounds = paths[1].bounds
        val bar3Bounds = paths[2].bounds
        val plusBounds = paths[3].bounds

        // Bar heights (vertical thickness)
        assertEquals("Bar 1 height must be 2.0dp", 2.0, bar1Bounds.height, 0.1)
        assertEquals("Bar 2 height must be 2.0dp", 2.0, bar2Bounds.height, 0.1)
        assertEquals("Bar 3 height must be 2.0dp", 2.0, bar3Bounds.height, 0.1)

        // Spacing between bars: (Bar2.minY - Bar1.maxY) and (Bar3.minY - Bar2.maxY)
        val gap1 = bar2Bounds.minY - bar1Bounds.maxY
        val gap2 = bar3Bounds.minY - bar2Bounds.maxY
        assertEquals("Gap between Bar 1 and Bar 2 must be 3.0dp", 3.0, gap1, 0.1)
        assertEquals("Gap between Bar 2 and Bar 3 must be 3.0dp", 3.0, gap2, 0.1)

        // Left alignment of all 3 horizontal bars
        assertEquals("Bar 1 minX", 3.0, bar1Bounds.minX, 0.1)
        assertEquals("Bar 2 minX", 3.0, bar2Bounds.minX, 0.1)
        assertEquals("Bar 3 minX", 3.0, bar3Bounds.minX, 0.1)

        // Relative lengths: Bar 2 (longest) > Bar 1 (medium) > Bar 3 (short)
        assertTrue("Bar 2 must be longer than Bar 1", bar2Bounds.width > bar1Bounds.width)
        assertTrue("Bar 1 must be longer than Bar 3", bar1Bounds.width > bar3Bounds.width)

        // Plus symbol symmetry around center (18, 18)
        assertEquals("Plus symbol width", 8.0, plusBounds.width, 0.1)
        assertEquals("Plus symbol height", 8.0, plusBounds.height, 0.1)
        val plusCenterX = plusBounds.centerX
        val plusCenterY = plusBounds.centerY
        assertEquals("Plus center X must be 18.0", 18.0, plusCenterX, 0.1)
        assertEquals("Plus center Y must be 18.0", 18.0, plusCenterY, 0.1)

        // Clearance between Bar 3 and Plus symbol (no overlap)
        val clearance = plusBounds.minX - bar3Bounds.maxX
        assertTrue("Clearance between Bar 3 and Plus ($clearance dp) must be >= 1.5dp", clearance >= 1.5)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Multi-Density Rasterization Stress Test (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testMultiDensityRasterization() {
        val densities = mapOf(
            "mdpi" to 1.0,     // 24x24 px
            "hdpi" to 1.5,     // 36x36 px
            "xhdpi" to 2.0,    // 48x48 px
            "xxhdpi" to 3.0,   // 72x72 px
            "xxxhdpi" to 4.0   // 96x96 px
        )

        val paths = parseAllVectorPaths(vectorIconFile)

        densities.forEach { (densityName, scale) ->
            val targetPx = (24 * scale).toInt()
            val grid = Array(targetPx) { BooleanArray(targetPx) }

            // Sample each path onto discrete grid using point-in-polygon
            for (y in 0 until targetPx) {
                val sampleY = (y + 0.5) / scale
                for (x in 0 until targetPx) {
                    val sampleX = (x + 0.5) / scale
                    for (path in paths) {
                        if (path.contains(sampleX, sampleY)) {
                            grid[y][x] = true
                            break
                        }
                    }
                }
            }

            var drawnPixels = 0
            var edgeTouched = false

            for (y in 0 until targetPx) {
                for (x in 0 until targetPx) {
                    if (grid[y][x]) {
                        drawnPixels++
                        if (x == 0 || x == targetPx - 1 || y == 0 || y == targetPx - 1) {
                            edgeTouched = true
                        }
                    }
                }
            }

            assertTrue("At density $densityName ($targetPx px), drawn pixels must be > 0", drawnPixels > 0)
            assertFalse(
                "At density $densityName ($targetPx px), no drawn pixels may touch the extreme boundary (0 or $targetPx-1)",
                edgeTouched
            )

            val fillRatio = drawnPixels.toDouble() / (targetPx * targetPx)
            assertTrue(
                "Fill ratio at $densityName must be reasonable for an icon (0.15 .. 0.35), got: $fillRatio",
                fillRatio in 0.15..0.35
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Color Tinting and WCAG Contrast under Extreme Palettes
    // ---------------------------------------------------------------------------------------------
    data class SimpleRgb(val r: Int, val g: Int, val b: Int) {
        companion object {
            fun fromHex(hex: String): SimpleRgb {
                val clean = hex.removePrefix("#")
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                return SimpleRgb(r, g, b)
            }
        }
    }

    data class ColorPaletteCase(
        val name: String,
        val bgHex: String,
        val tintHex: String,
        val alpha: Float = 1.0f,
        val minRequiredContrast: Double = 3.0 // WCAG 2.1 Graphical Objects threshold
    )

    @Test
    fun testColorTintingAndContrastExtremePalettes() {
        val testCases = listOf(
            ColorPaletteCase("Dark Theme Default (onSurface on surface)", "#121212", "#FFFFFF", minRequiredContrast = 7.0),
            ColorPaletteCase("Dark Theme Primary (primary on surfaceContainer)", "#1C1B1F", "#D0BCFF", minRequiredContrast = 4.5),
            ColorPaletteCase("Light Theme Default (onSurface on surface)", "#FFFFFF", "#1C1B1F", minRequiredContrast = 7.0),
            ColorPaletteCase("Light Theme Primary (primary on surface)", "#FEF7FF", "#6750A4", minRequiredContrast = 4.0),
            ColorPaletteCase("High Contrast Dark (pure yellow on black)", "#000000", "#FFFF00", minRequiredContrast = 7.0),
            ColorPaletteCase("High Contrast Light (pure black on white)", "#FFFFFF", "#000000", minRequiredContrast = 7.0),
            ColorPaletteCase("Dynamic Color Monet Dark", "#2E312E", "#A8EBA2", minRequiredContrast = 4.5),
            ColorPaletteCase("Dynamic Color Monet Light", "#F8FAF0", "#3E6837", minRequiredContrast = 4.5),
            ColorPaletteCase("Disabled State (38% alpha on dark)", "#121212", "#FFFFFF", alpha = 0.38f, minRequiredContrast = 2.0)
        )

        testCases.forEach { tc ->
            val bg = SimpleRgb.fromHex(tc.bgHex)
            val tint = SimpleRgb.fromHex(tc.tintHex)

            // Emulate BlendMode.SrcIn with background blending:
            // Output icon pixel = tint blended over background with tc.alpha
            val blendedR = (tint.r * tc.alpha + bg.r * (1.0f - tc.alpha)).toInt()
            val blendedG = (tint.g * tc.alpha + bg.g * (1.0f - tc.alpha)).toInt()
            val blendedB = (tint.b * tc.alpha + bg.b * (1.0f - tc.alpha)).toInt()
            val effectiveIconColor = SimpleRgb(blendedR, blendedG, blendedB)

            val contrast = calculateContrastRatio(bg, effectiveIconColor)
            assertTrue(
                "Palette '${tc.name}' contrast ratio ($contrast:1) must be >= ${tc.minRequiredContrast}:1",
                contrast >= tc.minRequiredContrast
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 6. Static Audit of All 7 Touchpoints
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testAllSevenTouchpointsIntegrity() {
        val uiDir = File(projectRoot, "app/src/main/java/com/example/musicplayer/ui/components")

        // 1. FullScreenPlayerSheet.kt
        val playerSheet = File(uiDir, "FullScreenPlayerSheet.kt").readText()
        assertTrue("FullScreenPlayerSheet must import/reference R.drawable.ic_queue_music_custom",
            playerSheet.contains("R.drawable.ic_queue_music_custom"))
        assertTrue("FullScreenPlayerSheet queue button must use tint = Color.White",
            playerSheet.contains("painter = painterResource(R.drawable.ic_queue_music_custom)") &&
                    playerSheet.contains("tint = Color.White"))

        // 2. QueueBottomSheet.kt
        val queueSheet = File(uiDir, "QueueBottomSheet.kt").readText()
        assertTrue("QueueBottomSheet must reference R.drawable.ic_queue_music_custom",
            queueSheet.contains("R.drawable.ic_queue_music_custom"))
        assertTrue("QueueBottomSheet header must use MaterialTheme.colorScheme.primary tint",
            queueSheet.contains("painter = painterResource(R.drawable.ic_queue_music_custom)") &&
                    queueSheet.contains("tint = MaterialTheme.colorScheme.primary"))

        // 3. TrackListItem.kt
        val trackItem = File(uiDir, "TrackListItem.kt").readText()
        assertTrue("TrackListItem must reference R.drawable.ic_queue_music_custom for Aggiungi alla coda",
            trackItem.contains("painter = painterResource(R.drawable.ic_queue_music_custom)"))

        // 4. AlbumDetailView.kt
        val albumDetail = File(uiDir, "AlbumDetailView.kt").readText()
        assertTrue("AlbumDetailView must reference R.drawable.ic_queue_music_custom for Aggiungi alla coda",
            albumDetail.contains("painter = painterResource(R.drawable.ic_queue_music_custom)"))

        // 5. AlbumsGridView.kt
        val albumsGrid = File(uiDir, "AlbumsGridView.kt").readText()
        assertTrue("AlbumsGridView must reference R.drawable.ic_queue_music_custom for Aggiungi alla coda",
            albumsGrid.contains("painter = painterResource(R.drawable.ic_queue_music_custom)"))

        // 6 & 7. PlaylistsView.kt
        val playlistsView = File(uiDir, "PlaylistsView.kt").readText()
        val occurrences = Regex("""painterResource\(R\.drawable\.ic_queue_music_custom\)""").findAll(playlistsView).count()
        assertEquals("PlaylistsView must reference ic_queue_music_custom in exactly 2 menu actions", 2, occurrences)

        // Verify that playlist entity icons (the 4 places) are NOT corrupted to ic_queue_music_custom
        val playlistEntityIconOccurrences = Regex("""Icons\.AutoMirrored\.Filled\.QueueMusic""").findAll(playlistsView).count()
        assertEquals("PlaylistsView must retain 4 playlist-entity icons as QueueMusic", 4, playlistEntityIconOccurrences)
    }

    // ---------------------------------------------------------------------------------------------
    // 7. Favorites Cover Binary Inspection & Boundary Validation
    // ---------------------------------------------------------------------------------------------
    @Test
    fun testFavoritesCoverBinaryAndDimensions() {
        assertTrue("cover_favorites.png must exist in drawable-nodpi", favoritesCoverFile.exists())
        val bytes = favoritesCoverFile.readBytes()

        // Verify PNG magic header: 89 50 4E 47 0D 0A 1A 0A
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
        for (i in 0 until 8) {
            assertEquals("PNG signature byte $i", pngMagic[i], bytes[i])
        }

        // IHDR chunk begins at offset 12: chunk length (4 bytes), "IHDR" (4 bytes), width (4 bytes), height (4 bytes)
        val dataIn = DataInputStream(ByteArrayInputStream(bytes, 16, 8))
        val width = dataIn.readInt()
        val height = dataIn.readInt()
        assertEquals("Cover width must be exactly 800px", 800, width)
        assertEquals("Cover height must be exactly 800px", 800, height)
    }

    // ---------------------------------------------------------------------------------------------
    // Helper Classes: Custom Lightweight Vector Path Engine
    // ---------------------------------------------------------------------------------------------
    data class Bounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
        val centerX: Double get() = (minX + maxX) / 2.0
        val centerY: Double get() = (minY + maxY) / 2.0
        val isEmpty: Boolean get() = maxX <= minX || maxY <= minY
    }

    class SimplePolygonPath(val points: List<Pair<Double, Double>>) {
        val bounds: Bounds by lazy {
            var minX = Double.MAX_VALUE
            var maxX = Double.MIN_VALUE
            var minY = Double.MAX_VALUE
            var maxY = Double.MIN_VALUE
            points.forEach { (x, y) ->
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
            }
            Bounds(minX, maxX, minY, maxY)
        }

        // Standard Ray-casting algorithm for point-in-polygon
        fun contains(x: Double, y: Double): Boolean {
            if (x < bounds.minX || x > bounds.maxX || y < bounds.minY || y > bounds.maxY) return false
            var inside = false
            var j = points.size - 1
            for (i in points.indices) {
                val (xi, yi) = points[i]
                val (xj, yj) = points[j]
                val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }
    }

    private fun parseAllVectorPaths(xmlFile: File): List<SimplePolygonPath> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val pathNodes = doc.getElementsByTagName("path")
        val result = mutableListOf<SimplePolygonPath>()
        for (i in 0 until pathNodes.length) {
            val element = pathNodes.item(i) as org.w3c.dom.Element
            val pathData = element.getAttribute("android:pathData")
            result.add(parseSvgPathToPolygon(pathData))
        }
        return result
    }

    private fun parseSvgPathToPolygon(d: String): SimplePolygonPath {
        val points = mutableListOf<Pair<Double, Double>>()
        val tokens = tokenizePathData(d)
        var i = 0
        var curX = 0.0
        var curY = 0.0
        var lastCubicControlX = 0.0
        var lastCubicControlY = 0.0
        var lastCmd = ' '

        while (i < tokens.size) {
            val token = tokens[i++]
            val cmd = token[0]
            when (cmd) {
                'M' -> {
                    curX = tokens[i++].toDouble()
                    curY = tokens[i++].toDouble()
                    points.add(curX to curY)
                    lastCmd = 'M'
                }
                'h' -> {
                    val dx = tokens[i++].toDouble()
                    curX += dx
                    points.add(curX to curY)
                    lastCmd = 'h'
                }
                'H' -> {
                    curX = tokens[i++].toDouble()
                    points.add(curX to curY)
                    lastCmd = 'H'
                }
                'v' -> {
                    val dy = tokens[i++].toDouble()
                    curY += dy
                    points.add(curX to curY)
                    lastCmd = 'v'
                }
                'V' -> {
                    curY = tokens[i++].toDouble()
                    points.add(curX to curY)
                    lastCmd = 'V'
                }
                'c' -> {
                    val dx1 = tokens[i++].toDouble()
                    val dy1 = tokens[i++].toDouble()
                    val dx2 = tokens[i++].toDouble()
                    val dy2 = tokens[i++].toDouble()
                    val dx = tokens[i++].toDouble()
                    val dy = tokens[i++].toDouble()
                    val cp1x = curX + dx1
                    val cp1y = curY + dy1
                    val cp2x = curX + dx2
                    val cp2y = curY + dy2
                    val endX = curX + dx
                    val endY = curY + dy

                    // Sample cubic bezier curve into 8 discrete segments
                    for (step in 1..8) {
                        val t = step / 8.0
                        val oneMinusT = 1.0 - t
                        val bx = oneMinusT.pow(3) * curX + 3 * oneMinusT.pow(2) * t * cp1x + 3 * oneMinusT * t.pow(2) * cp2x + t.pow(3) * endX
                        val by = oneMinusT.pow(3) * curY + 3 * oneMinusT.pow(2) * t * cp1y + 3 * oneMinusT * t.pow(2) * cp2y + t.pow(3) * endY
                        points.add(bx to by)
                    }

                    lastCubicControlX = cp2x
                    lastCubicControlY = cp2y
                    curX = endX
                    curY = endY
                    lastCmd = 'c'
                }
                's' -> {
                    val dx2 = tokens[i++].toDouble()
                    val dy2 = tokens[i++].toDouble()
                    val dx = tokens[i++].toDouble()
                    val dy = tokens[i++].toDouble()
                    val cp1x = if (lastCmd == 'c' || lastCmd == 's') (2 * curX - lastCubicControlX) else curX
                    val cp1y = if (lastCmd == 'c' || lastCmd == 's') (2 * curY - lastCubicControlY) else curY
                    val cp2x = curX + dx2
                    val cp2y = curY + dy2
                    val endX = curX + dx
                    val endY = curY + dy

                    for (step in 1..8) {
                        val t = step / 8.0
                        val oneMinusT = 1.0 - t
                        val bx = oneMinusT.pow(3) * curX + 3 * oneMinusT.pow(2) * t * cp1x + 3 * oneMinusT * t.pow(2) * cp2x + t.pow(3) * endX
                        val by = oneMinusT.pow(3) * curY + 3 * oneMinusT.pow(2) * t * cp1y + 3 * oneMinusT * t.pow(2) * cp2y + t.pow(3) * endY
                        points.add(bx to by)
                    }

                    lastCubicControlX = cp2x
                    lastCubicControlY = cp2y
                    curX = endX
                    curY = endY
                    lastCmd = 's'
                }
                'z', 'Z' -> {
                    lastCmd = 'z'
                }
            }
        }
        return SimplePolygonPath(points)
    }

    private fun tokenizePathData(d: String): List<String> {
        val result = mutableListOf<String>()
        val regex = Regex("""([a-zA-Z])|([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)""")
        regex.findAll(d).forEach { match ->
            result.add(match.value)
        }
        return result
    }

    private fun calculateContrastRatio(c1: SimpleRgb, c2: SimpleRgb): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: SimpleRgb): Double {
        fun linearize(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = linearize(color.r)
        val g = linearize(color.g)
        val b = linearize(color.b)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
