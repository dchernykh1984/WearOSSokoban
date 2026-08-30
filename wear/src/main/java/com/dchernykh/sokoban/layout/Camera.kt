package com.dchernykh.sokoban.layout

import kotlin.math.roundToInt

// The camera: which part of the warehouse the round window is showing.
//
// The offset is in PIXELS, not cells. With the board drawn on a canvas the map can
// follow a finger exactly instead of jumping a whole cell at a time, and a pixel
// offset is the only thing that makes that possible. Cells only come back at the
// edges, where the camera has to be told to keep the keeper in view.
//
// (x, y) is how many pixels of the warehouse are hidden off the left and the top of
// the window, so everything here keeps that inside the board: the map can never be
// dragged off into empty space.

/** How many cells ahead of the keeper the map is kept, when it has to move at all. */
const val FOLLOW_MARGIN = 2

data class Camera(
    val x: Int,
    val y: Int,
)

/** How big the warehouse is, in cells. One thing, so it travels as one. */
data class Grid(
    val cols: Int,
    val rows: Int,
)

/** The furthest the map can be pushed before the far edge would come into view. */
fun maxOffset(
    span: Int,
    visible: Int,
    cell: Int,
): Int = maxOf(0, (span - visible) * cell)

fun clampOffset(
    value: Int,
    span: Int,
    visible: Int,
    cell: Int,
): Int = value.coerceIn(0, maxOffset(span, visible, cell))

/** The offset that puts a cell in the middle of the window. */
fun centerOffset(
    coord: Int,
    span: Int,
    visible: Int,
    cell: Int,
): Int = clampOffset(((coord + 0.5f) * cell - visible * cell / 2f).roundToInt(), span, visible, cell)

/**
 * The smallest change that keeps a cell at least [margin] cells from both edges of
 * the window.
 *
 * Used after every step, so the keeper walking towards the edge drags the map along
 * the way a navigator scrolls ahead of you - while a map deliberately dragged
 * elsewhere is left where it was put.
 */
fun followOffset(
    current: Int,
    coord: Int,
    span: Int,
    visible: Int,
    cell: Int,
    margin: Int,
): Int {
    val room = margin.coerceIn(0, (visible - 1) / 2)
    val offset = clampOffset(current, span, visible, cell)
    val cellAt = coord * cell

    val nearest = offset + room * cell
    val furthest = offset + (visible - 1 - room) * cell
    return when {
        cellAt < nearest -> clampOffset(cellAt - room * cell, span, visible, cell)
        cellAt > furthest -> clampOffset(cellAt - (visible - 1 - room) * cell, span, visible, cell)
        else -> offset
    }
}

/** The camera with the given cell in the middle of the window. */
fun centerCamera(
    window: BoardWindow,
    grid: Grid,
    col: Int,
    row: Int,
): Camera =
    Camera(
        x = centerOffset(col, grid.cols, window.cells, window.cell),
        y = centerOffset(row, grid.rows, window.cells, window.cell),
    )

/** The camera moved just enough to keep a cell away from the edges of the window. */
fun followCamera(
    camera: Camera,
    window: BoardWindow,
    grid: Grid,
    col: Int,
    row: Int,
    margin: Int = FOLLOW_MARGIN,
): Camera =
    Camera(
        x = followOffset(camera.x, col, grid.cols, window.cells, window.cell, margin),
        y = followOffset(camera.y, row, grid.rows, window.cells, window.cell, margin),
    )

/**
 * The camera after dragging the map by a finger movement. The map follows the
 * finger, so dragging right shows what was off to the left: the offset moves the
 * other way.
 */
fun panCamera(
    start: Camera,
    dx: Int,
    dy: Int,
    window: BoardWindow,
    grid: Grid,
): Camera =
    Camera(
        x = clampOffset(start.x - dx, grid.cols, window.cells, window.cell),
        y = clampOffset(start.y - dy, grid.rows, window.cells, window.cell),
    )

/** Which cells have any pixel inside the window. */
data class CellRange(
    val fromX: Int,
    val toX: Int,
    val fromY: Int,
    val toY: Int,
)

/**
 * The cells with any pixel inside the window.
 *
 * The last pixel of the window is what the range is measured against, not the one
 * past it: measuring past the end adds a whole column that is entirely outside the
 * window, and nothing clips it - the canvas is the whole screen, so that column
 * lands on the arrows and over the edge of a round watch face.
 */
fun visibleCells(
    camera: Camera,
    window: BoardWindow,
    grid: Grid,
): CellRange =
    CellRange(
        fromX = maxOf(0, camera.x / window.cell),
        toX = minOf(grid.cols - 1, (camera.x + window.cells * window.cell - 1) / window.cell),
        fromY = maxOf(0, camera.y / window.cell),
        toY = minOf(grid.rows - 1, (camera.y + window.cells * window.cell - 1) / window.cell),
    )

/** Where a warehouse cell lands on screen, given where the camera is. */
fun cellBox(
    camera: Camera,
    window: BoardWindow,
    col: Int,
    row: Int,
): Box =
    Box(
        x = window.x + col * window.cell - camera.x,
        y = window.y + row * window.cell - camera.y,
        w = window.cell,
        h = window.cell,
    )
