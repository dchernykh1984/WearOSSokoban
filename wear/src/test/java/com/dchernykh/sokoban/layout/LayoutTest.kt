package com.dchernykh.sokoban.layout

import com.dchernykh.sokoban.game.Direction
import com.dchernykh.sokoban.game.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** The round sizes the game is built for, and a small one for good measure. */
private val SCREENS = listOf(384, 450, 454, 466, 480)

private const val WATCH = 466
private const val PADDING = 8

private fun assertCornersOnScreen(
    screenSize: Int,
    box: Box,
    what: String,
) {
    val radius = screenSize / 2f
    for (
    (x, y) in
    listOf(
        box.x to box.y,
        box.x + box.w to box.y,
        box.x to box.y + box.h,
        box.x + box.w to box.y + box.h,
    )
    ) {
        assertTrue("corner ($x, $y) of $what escapes a $screenSize screen", hypot(x - radius, y - radius) <= radius)
    }
}

class SafeWidthTest {
    @Test
    fun `is widest on the centre line and narrows towards the edge`() {
        assertEquals(100f, safeHalfWidth(100f, 0f), 0.001f)
        assertEquals(80f, safeHalfWidth(100f, 60f), 0.001f)
        assertEquals(0f, safeHalfWidth(100f, 100f), 0.001f)
    }

    @Test
    fun `has no width at all past the edge of the circle`() {
        assertEquals(0f, safeHalfWidth(100f, 140f), 0.001f)
    }

    @Test
    fun `binds a line by whichever of its edges is further out`() {
        val below = safeLineWidth(WATCH, 300f, 40f, PADDING)
        val above = safeLineWidth(WATCH, 166f, 40f, PADDING)

        assertEquals(above, below, 0.001f)
        assertTrue(below < safeLineWidth(WATCH, 233f, 40f, PADDING))
    }

    @Test
    fun `gives nothing to a line pushed off the screen`() {
        assertEquals(0f, safeLineWidth(WATCH, -50f, 40f, PADDING), 0.001f)
        assertEquals(0f, safeLineWidth(WATCH, 520f, 40f, PADDING), 0.001f)
    }

    @Test
    fun `keeps every corner of a centred box on the screen`() {
        for (screen in SCREENS) {
            var top = 0
            while (top < screen - 30) {
                val box = centeredBox(screen, top, 30, screen.toFloat(), PADDING)
                if (box.w > 0) assertCornersOnScreen(screen, box, "a box at $top")
                top += 7
            }
        }
    }

    @Test
    fun `knows what a box contains`() {
        val box = Box(10, 20, 30, 40)

        assertTrue((10 to 20) in box)
        assertTrue((39 to 59) in box)
        assertTrue((9 to 20) !in box)
        assertTrue((40 to 20) !in box)
        assertTrue((10 to 60) !in box)
    }
}

class BoardWindowTest {
    @Test
    fun `fits inside the square inscribed in the round screen`() {
        for (screen in SCREENS) {
            for (size in Size.entries) {
                val window = boardWindow(screen, size.visible)
                assertCornersOnScreen(screen, Box(window.x, window.y, window.size, window.size), "the $size window")
            }
        }
    }

    @Test
    fun `holds a whole number of equal cells`() {
        for (screen in SCREENS) {
            for (size in Size.entries) {
                val window = boardWindow(screen, size.visible)
                assertEquals("$screen $size", window.cell * window.cells, window.size)
                assertEquals("$screen $size", size.visible, window.cells)
            }
        }
    }

    @Test
    fun `is centred on the screen`() {
        for (screen in SCREENS) {
            val window = boardWindow(screen, 11)
            assertEquals(window.x, window.y)
            // Within a pixel: the window holds a whole number of equal cells, so
            // what is left over cannot always be split evenly between the two sides.
            assertTrue("$screen", kotlin.math.abs(screen - window.x - window.size - window.x) <= 1)
        }
    }

    @Test
    fun `shows more of a small warehouse and no more of a big one`() {
        // XS and S are shown whole; from M upwards the window is deliberately
        // smaller than the warehouse and the map has to be dragged.
        assertTrue(boardWindow(WATCH, Size.XS.visible).cells >= Size.XS.cols)
        assertTrue(boardWindow(WATCH, Size.S.visible).cells >= Size.S.cols)
        for (size in listOf(Size.M, Size.L, Size.XL, Size.XXL)) {
            assertTrue("$size fits the window whole", boardWindow(WATCH, size.visible).cells < size.cols)
        }
    }

    @Test
    fun `never draws a cell away to nothing`() {
        val window = boardWindow(1, 11)

        assertTrue(window.cell >= 1)
        assertTrue(window.cells >= 1)
    }
}

class CameraTest {
    private val window = boardWindow(WATCH, Size.M.visible)
    private val grid = Grid(Size.M.cols, Size.M.rows)

    @Test
    fun `pins a warehouse that fits the window in place`() {
        assertEquals(0, maxOffset(span = 9, visible = 11, cell = 40))
        assertEquals(0, clampOffset(500, span = 9, visible = 11, cell = 40))
    }

    @Test
    fun `stops at the far edge of a warehouse that does not fit`() {
        assertEquals(2 * 40, maxOffset(span = 13, visible = 11, cell = 40))
        assertEquals(80, clampOffset(9999, span = 13, visible = 11, cell = 40))
        assertEquals(0, clampOffset(-9999, span = 13, visible = 11, cell = 40))
    }

    @Test
    fun `puts a cell in the middle of the window`() {
        val camera = centerCamera(window, grid, col = 6, row = 6)
        val screenX = window.x + 6 * window.cell - camera.x

        // Within half a cell of the middle, which is as close as whole cells get.
        assertTrue(kotlin.math.abs(screenX + window.cell / 2 - WATCH / 2) <= window.cell / 2 + 1)
    }

    @Test
    fun `moves the map with the finger, not against it`() {
        // Started in the middle and dragged a few pixels, which is well inside what
        // the warehouse allows: the clamp is what the next test is about.
        val start = centerCamera(window, grid, 6, 6)
        val dragged = panCamera(start, dx = 5, dy = -5, window = window, grid = grid)

        assertEquals(start.x - 5, dragged.x)
        assertEquals(start.y + 5, dragged.y)
    }

    @Test
    fun `never lets the map run off into empty space`() {
        val far = panCamera(Camera(0, 0), dx = -99999, dy = -99999, window = window, grid = grid)

        assertEquals(maxOffset(grid.cols, window.cells, window.cell), far.x)
        assertEquals(maxOffset(grid.rows, window.cells, window.cell), far.y)
    }

    @Test
    fun `leaves the map alone while the keeper is well inside the window`() {
        val camera = centerCamera(window, grid, 6, 6)

        assertEquals(camera, followCamera(camera, window, grid, 6, 6))
    }

    @Test
    fun `drags the map along when the keeper walks towards the edge`() {
        val camera = Camera(0, 0)
        val followed = followCamera(camera, window, grid, col = grid.cols - 1, row = 0)

        assertTrue("the map did not follow", followed.x > camera.x)
        assertEquals("and did not move on the axis that did not need it", camera.y, followed.y)
    }

    @Test
    fun `keeps the keeper on screen wherever it walks`() {
        var camera = Camera(0, 0)
        for (col in 0 until grid.cols) {
            camera = followCamera(camera, window, grid, col, 6)
            val screenX = col * window.cell - camera.x
            assertTrue("column $col is off the window", screenX >= 0 && screenX < window.cells * window.cell)
        }
    }

    @Test
    fun `asks for no more margin than the window can give`() {
        // A margin wider than half the window would leave the keeper nowhere legal
        // to stand, so it is clamped rather than obeyed.
        val followed = followOffset(0, coord = 5, span = 13, visible = 3, cell = 40, margin = 99)

        assertTrue(followed in 0..maxOffset(13, 3, 40))
    }

    @Test
    fun `lists the cells with any pixel inside the window`() {
        val range = visibleCells(Camera(0, 0), window, grid)

        assertEquals(0, range.fromX)
        assertEquals(window.cells - 1, range.toX)
        assertEquals(0, range.fromY)
    }

    @Test
    fun `counts a row that is only half on screen`() {
        val range = visibleCells(Camera(window.cell / 2, 0), window, grid)

        assertEquals("the row the window starts inside is still drawn", 0, range.fromX)
        assertEquals(window.cells, range.toX)
    }

    @Test
    fun `never lists a cell the warehouse does not have`() {
        val camera = panCamera(Camera(0, 0), -9999, -9999, window, grid)
        val range = visibleCells(camera, window, grid)

        assertEquals(grid.cols - 1, range.toX)
        assertEquals(grid.rows - 1, range.toY)
    }

    @Test
    fun `says where a cell lands on screen`() {
        val box = cellBox(Camera(0, 0), window, col = 2, row = 3)

        assertEquals(window.x + 2 * window.cell, box.x)
        assertEquals(window.y + 3 * window.cell, box.y)
        assertEquals(window.cell, box.w)
    }
}

class ControlsTest {
    private val window = boardWindow(WATCH, Size.M.visible)
    private val layout = controlLayout(WATCH, window)

    @Test
    fun `keeps every control on the glass`() {
        for (screen in SCREENS) {
            val here = controlLayout(screen, boardWindow(screen, Size.M.visible))
            for (
            (name, box) in
            listOf(
                "counter" to here.counter,
                "up" to here.up,
                "down" to here.down,
                "left" to here.left,
                "right" to here.right,
                "undo" to here.undo,
                "menu" to here.menu,
            )
            ) {
                assertCornersOnScreen(screen, box, "$name on a $screen screen")
            }
        }
    }

    @Test
    fun `keeps every control clear of the board`() {
        // The window belongs to the map: a control overlapping it would steal a drag.
        for (screen in SCREENS) {
            val here = controlLayout(screen, boardWindow(screen, Size.M.visible))
            for (
            (name, box) in
            listOf(
                "counter" to here.counter,
                "up" to here.up,
                "down" to here.down,
                "left" to here.left,
                "right" to here.right,
                "undo" to here.undo,
                "menu" to here.menu,
            )
            ) {
                assertFalse("$name overlaps the board on a $screen screen", overlaps(box, here.board))
            }
        }
    }

    @Test
    fun `keeps the three controls of the bottom row apart`() {
        // The gaps are the point: a thumb landing wide of the down arrow does
        // nothing at all rather than undoing the move it just made.
        assertTrue(layout.undo.x + layout.undo.w < layout.down.x)
        assertTrue(layout.down.x + layout.down.w < layout.menu.x)
        assertTrue("the arrow is not the widest of the three", layout.down.w > layout.undo.w)
    }

    @Test
    fun `puts the two side arrows opposite each other`() {
        assertEquals(layout.left.y, layout.right.y)
        assertEquals(layout.left.w, layout.right.w)
        // Within a couple of pixels: the window itself is centred to whole pixels
        // and its cells do not always divide the screen evenly, so the segment on
        // one side can be a pixel wider than the other.
        val mirrored = WATCH - layout.right.x - layout.right.w
        assertTrue("left ${layout.left.x} against $mirrored", kotlin.math.abs(mirrored - layout.left.x) <= 2)
    }

    @Test
    fun `finds the arrow under a finger`() {
        assertEquals(Hit.Step(Direction.UP), hitTest(layout, layout.up.x + 1, layout.up.y + 1))
        assertEquals(Hit.Step(Direction.DOWN), hitTest(layout, layout.down.x + 1, layout.down.y + 1))
        assertEquals(Hit.Step(Direction.LEFT), hitTest(layout, layout.left.x + 1, layout.left.y + 1))
        assertEquals(Hit.Step(Direction.RIGHT), hitTest(layout, layout.right.x + 1, layout.right.y + 1))
    }

    @Test
    fun `finds the two buttons and the board`() {
        assertEquals(Hit.Undo, hitTest(layout, layout.undo.x + 1, layout.undo.y + 1))
        assertEquals(Hit.Menu, hitTest(layout, layout.menu.x + 1, layout.menu.y + 1))
        assertEquals(Hit.Board, hitTest(layout, WATCH / 2, WATCH / 2))
    }

    @Test
    fun `finds nothing in the dead space between the bottom controls`() {
        val between = (layout.undo.x + layout.undo.w + layout.down.x) / 2

        assertNull(hitTest(layout, between, layout.down.y + layout.down.h / 2))
    }

    private fun overlaps(
        one: Box,
        other: Box,
    ): Boolean =
        one.x < other.x + other.w &&
            other.x < one.x + one.w &&
            one.y < other.y + other.h &&
            other.y < one.y + one.h
}

class WindowBoxTest {
    @Test
    fun `knows whether a finger landed on the map`() {
        val window = boardWindow(WATCH, Size.M.visible)
        val controls = controlLayout(WATCH, window)

        assertTrue((WATCH / 2 to WATCH / 2) in window.box)
        // Every control is outside it, which is what keeps a drag that starts on an
        // arrow from moving the warehouse out from under the step it was aiming at.
        for (box in listOf(controls.up, controls.down, controls.left, controls.right, controls.undo, controls.menu)) {
            assertFalse("(${box.x}, ${box.y}) is on the map", (box.x + box.w / 2 to box.y + box.h / 2) in window.box)
        }
    }
}

class ArrowMetricsTest {
    private val window = boardWindow(WATCH, Size.M.visible)
    private val layout = controlLayout(WATCH, window)
    private val boxes = listOf(layout.up, layout.down, layout.left, layout.right, layout.undo, layout.menu)

    @Test
    fun `gives every control the same arrow, whatever shape its button is`() {
        // The whole point of one shared size: four arrows that are the same read as
        // one set of controls, and four that are not read as clutter.
        val metrics = arrowMetrics(boxes)

        assertTrue(metrics.reach > 0)
        assertTrue(metrics.width >= 2)
    }

    @Test
    fun `keeps the arrow inside the tightest button it has to fit`() {
        for (screen in SCREENS) {
            val here = controlLayout(screen, boardWindow(screen, Size.M.visible))
            val row = listOf(here.up, here.down, here.left, here.right, here.undo, here.menu)
            val metrics = arrowMetrics(row)

            for (box in row) {
                val midX = box.x + box.w / 2
                val midY = box.y + box.h / 2
                val bleed = metrics.reach + metrics.width / 2
                assertTrue(
                    "an arrow overhangs a $screen button ${box.w}x${box.h}",
                    midX - bleed >= box.x &&
                        midX + bleed <= box.x + box.w &&
                        midY - bleed >= box.y &&
                        midY + bleed <= box.y + box.h,
                )
            }
        }
    }

    @Test
    fun `is decided by the smallest button, not the largest`() {
        val alone = arrowMetrics(listOf(layout.left))
        val together = arrowMetrics(boxes)

        assertTrue("the shared arrow is no bigger than the tightest button allows", together.reach <= alone.reach)
    }

    @Test
    fun `has nothing to draw without a button to draw it in`() {
        assertEquals(ArrowMetrics(0, 0), arrowMetrics(emptyList()))
        assertEquals(ArrowMetrics(0, 0), arrowMetrics(listOf(Box(0, 0, 0, 0))))
        assertEquals(ArrowMetrics(0, 0), arrowMetrics(listOf(Box(0, 0, 1, 1))))
    }
}
