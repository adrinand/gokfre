package com.adrinand.kofre.core.screen

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.awt.Rectangle

@Suppress("TooManyFunctions")
class ArrangementHelperTest {
    @Test
    fun `findKeyPositions returns positions for selected keys`() {
        val keyLabels =
            listOf(
                listOf("Q", "W", "E", "R"),
                listOf("A", "S", "D", "F"),
            )

        val positions = findKeyPositions(keyLabels, listOf("Q", "S"))

        positions shouldBe listOf(TilePosition(0, 0), TilePosition(1, 1))
    }

    @Test
    fun `findKeyPositions is case insensitive`() {
        val keyLabels = listOf(listOf("Q", "W"))

        val positions = findKeyPositions(keyLabels, listOf("q", "W"))

        positions shouldBe listOf(TilePosition(0, 0), TilePosition(0, 1))
    }

    @Test
    fun `computeSelectedBounds returns fullscreen for opposite corners`() {
        val workArea = Rectangle(0, 0, 1000, 800)
        val rowWeights = listOf(1, 1)
        val columnWeights = listOf(1, 1, 1, 1)
        val positions = listOf(TilePosition(0, 0), TilePosition(1, 3))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        bounds shouldBe Rectangle(0, 0, 1000, 800)
    }

    @Test
    fun `computeSelectedBounds returns left half for diagonal keys in first two columns`() {
        val workArea = Rectangle(0, 0, 1000, 800)
        val rowWeights = listOf(1, 1)
        val columnWeights = listOf(1, 1, 1, 1)
        val positions = listOf(TilePosition(0, 0), TilePosition(1, 1))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        bounds shouldBe Rectangle(0, 0, 500, 800)
    }

    @Test
    fun `computeSelectedBounds returns top half for adjacent keys in first row`() {
        val workArea = Rectangle(0, 0, 1000, 800)
        val rowWeights = listOf(1, 1)
        val columnWeights = listOf(1, 1, 1, 1)
        val positions = listOf(TilePosition(0, 0), TilePosition(0, 1))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        bounds shouldBe Rectangle(0, 0, 500, 400)
    }

    @Test
    fun `computeSelectedBounds returns single cell for same key pressed twice`() {
        val workArea = Rectangle(0, 0, 1000, 800)
        val rowWeights = listOf(1, 1)
        val columnWeights = listOf(1, 1, 1, 1)
        val positions = listOf(TilePosition(0, 0), TilePosition(0, 0))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        bounds shouldBe Rectangle(0, 0, 250, 400)
    }

    @Test
    fun `computeSelectedBounds ignores hidden rows and columns`() {
        val workArea = Rectangle(0, 0, 1000, 800)
        val rowWeights = listOf(0, 1, 1)
        val columnWeights = listOf(0, 1, 1, 1)
        // Visible grid is rows 1-2, cols 1-3 (3x2).
        // Key "A" is at raw (1,0) which is hidden col -> ignored.
        val keyLabels =
            listOf(
                listOf("Q", "W", "E", "R"),
                listOf("A", "S", "D", "F"),
                listOf("Z", "X", "C", "V"),
            )
        val positions = findKeyPositions(keyLabels, listOf("S", "V"))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        // S is at raw (1,1) -> visual (0,0); V is at raw (2,3) -> visual (1,2).
        // Bounding box covers full width and full visible height.
        bounds shouldBe Rectangle(0, 0, 1000, 800)
    }

    @Test
    fun `computeSelectedBounds respects custom weights`() {
        val workArea = Rectangle(0, 0, 1000, 800)
        val rowWeights = listOf(1, 3)
        val columnWeights = listOf(1, 3)
        val positions = listOf(TilePosition(0, 0), TilePosition(1, 1))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        bounds shouldBe Rectangle(0, 0, 1000, 800)
    }

    @Test
    fun `computeSelectedBounds handles three rows dynamically`() {
        val workArea = Rectangle(0, 0, 1200, 900)
        val rowWeights = listOf(1, 1, 1)
        val columnWeights = listOf(1, 1, 1, 1)
        val positions = listOf(TilePosition(0, 0), TilePosition(2, 1))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        // Rows 0-2 -> full height. Cols 0-1 -> half width.
        bounds shouldBe Rectangle(0, 0, 600, 900)
    }

    @Test
    fun `computeSelectedBounds returns null for empty positions`() {
        val bounds = computeSelectedBounds(listOf(1), listOf(1), emptyList(), Rectangle(0, 0, 100, 100))

        bounds shouldBe null
    }

    @Test
    fun `computeSelectedBounds returns null when selection is hidden`() {
        val bounds =
            computeSelectedBounds(
                rowWeights = listOf(0, 1),
                columnWeights = listOf(1),
                positions = listOf(TilePosition(0, 0)),
                workArea = Rectangle(0, 0, 100, 100),
            )

        bounds shouldBe null
    }

    @Test
    fun `computeSelectedBounds offsets by work area origin`() {
        val workArea = Rectangle(100, 50, 1000, 800)
        val rowWeights = listOf(1, 1)
        val columnWeights = listOf(1, 1)
        val positions = listOf(TilePosition(0, 0))

        val bounds = computeSelectedBounds(rowWeights, columnWeights, positions, workArea)

        bounds shouldNotBe null
        bounds shouldBe Rectangle(100, 50, 500, 400)
    }
}
