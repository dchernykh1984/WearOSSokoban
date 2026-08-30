package com.dchernykh.sokoban.layout

import kotlin.math.roundToInt
import kotlin.math.sqrt

// Where the window onto the warehouse sits on a round screen.
//
// The window is the largest axis-aligned square that fits inside the circle (side =
// diameter / sqrt 2), shrunk to a whole number of equal cells so no cell is a pixel
// wider than its neighbour. Centring that square leaves a circular cap above and
// below it, which is where the counters and the controls go - and four segments
// around the edges, which is where the arrows go.

/**
 * The corners of the exactly inscribed square touch the glass, so rounding the
 * centring to whole pixels can push one of them a fraction past the bezel. Two
 * pixels off each side costs nothing visible and keeps every corner inside the
 * circle at every round resolution.
 */
const val BEZEL_MARGIN = 2

/** The window onto the warehouse: where it sits, and the cell everything is drawn in. */
data class BoardWindow(
    val x: Int,
    val y: Int,
    val size: Int,
    val cell: Int,
    /** How many cells are on screen at once, which is not how big the warehouse is. */
    val cells: Int,
)

/** The window for a screen of this diameter, showing this many cells across. */
fun boardWindow(
    screenSize: Int,
    cells: Int,
): BoardWindow {
    val columns = maxOf(1, cells)
    val inscribed = maxOf(1, (screenSize / sqrt(2f)).toInt() - BEZEL_MARGIN)
    val cell = maxOf(1, inscribed / columns)
    val size = cell * columns
    val origin = ((screenSize - size) / 2f).roundToInt()
    return BoardWindow(x = origin, y = origin, size = size, cell = cell, cells = columns)
}
