package com.dchernykh.sokoban.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.dchernykh.sokoban.game.Direction
import com.dchernykh.sokoban.game.Game
import com.dchernykh.sokoban.layout.BOARD_EDGE
import com.dchernykh.sokoban.layout.BoardWindow
import com.dchernykh.sokoban.layout.Box
import com.dchernykh.sokoban.layout.Camera
import com.dchernykh.sokoban.layout.Grid
import com.dchernykh.sokoban.layout.cellBox
import com.dchernykh.sokoban.layout.visibleCells
import kotlin.math.roundToInt

// The warehouse, drawn cell by cell, exactly as the Zepp OS original drew it.
//
// Every cell is a tile with an edge a shade off its face, because a wall of one
// flat colour reads as one shape rather than as a wall of blocks. On top of that
// go the three things that carry the game: a ring where a crate has to end up, a
// braced crate so it reads as a crate and not a coloured square, and the keeper
// with a bright mark on the edge it last pushed with.

/** How far inside its cell a crate is drawn, and how thick its border is. */
private const val BOX_INSET = 0.12f
private const val BOX_BORDER = 0.1f

/** The keeper's disc, and the mark that shows which way it faces. */
private const val KEEPER_RADIUS = 0.3f
private const val KEEPER_EYE = 0.1f

/** The goal ring: how wide it is drawn, and how thick. */
private const val GOAL_RADIUS = 0.26f
private const val GOAL_RING = 0.07f

/**
 * The warehouse on one canvas, clipped to the square window the round screen leaves
 * in the middle.
 *
 * Only the cells the window can actually see are drawn. The largest warehouse is 361
 * cells and the window shows at most 121 of them, so drawing the rest would be two
 * thirds wasted work every frame - and, on a canvas that covers the whole screen,
 * would paint straight over the arrows in the segments around the board.
 */
@Composable
fun BoardCanvas(
    game: Game,
    window: BoardWindow,
    camera: Camera,
    modifier: Modifier = Modifier,
) {
    // Which way the keeper is drawn facing: the way it last pushed. On a fresh
    // warehouse it has pushed nothing yet and simply has no facing.
    val facing = game.history.lastOrNull()?.direction

    Canvas(modifier = modifier) {
        val level = game.level
        val range = visibleCells(camera, window, Grid(level.cols, level.rows))

        insideWindow(window) {
            for (row in range.fromY..range.toY) {
                for (col in range.fromX..range.toX) {
                    drawCell(game, level.indexOf(col, row), cellBox(camera, window, col, row), facing)
                }
            }
        }

        // A frame round the window, so the map reads as something seen through a
        // hole rather than as a board that happens to stop.
        drawRect(
            color = ColorFrame,
            topLeft = Offset(window.x.toFloat(), window.y.toFloat()),
            size = Size(window.size.toFloat(), window.size.toFloat()),
            style = Stroke(width = BOARD_EDGE.toFloat()),
        )
    }
}

/** Everything drawn inside the window, and nothing outside it. */
private fun DrawScope.insideWindow(
    window: BoardWindow,
    block: DrawScope.() -> Unit,
) {
    clipRect(
        left = window.x.toFloat(),
        top = window.y.toFloat(),
        right = (window.x + window.size).toFloat(),
        bottom = (window.y + window.size).toFloat(),
    ) { block() }
}

/** One cell, back to front: the tile, then whatever is standing on it. */
private fun DrawScope.drawCell(
    game: Game,
    index: Int,
    box: Box,
    facing: Direction?,
) {
    val level = game.level
    if (level.isWall(index)) {
        tile(box, face = ColorWall, edge = ColorWallEdge)
        return
    }

    tile(box, face = ColorFloor, edge = ColorFloorEdge)
    if (level.isGoal(index)) goalRing(box)
    if (game.hasBox(index)) {
        val home = level.isGoal(index)
        crate(box, face = if (home) ColorBoxHome else ColorBox, edge = if (home) ColorBoxHomeEdge else ColorBoxEdge)
    }
    if (game.player == index) keeper(box, facing)
}

/** A tile: the edge, then the face inset by a pixel, so the grid reads as cells. */
private fun DrawScope.tile(
    box: Box,
    face: Color,
    edge: Color,
) {
    fill(box.x, box.y, box.w, box.h, edge)
    fill(box.x + 1, box.y + 1, box.w - 2, box.h - 2, face)
}

/**
 * A crate: a filled square with a darker border and the diagonal bracing that makes
 * it read as a crate rather than a coloured block.
 */
private fun DrawScope.crate(
    box: Box,
    face: Color,
    edge: Color,
) {
    val inset = maxOf(1, (box.w * BOX_INSET).roundToInt())
    val border = maxOf(1, (box.w * BOX_BORDER).roundToInt())
    val outer = box.w - 2 * inset
    val inner = outer - 2 * border
    if (outer <= 0) return

    fill(box.x + inset, box.y + inset, outer, outer, edge)
    if (inner <= 0) return
    fill(box.x + inset + border, box.y + inset + border, inner, inner, face)

    val left = (box.x + inset + border).toFloat()
    val top = (box.y + inset + border).toFloat()
    val right = left + inner
    val bottom = top + inner
    drawLine(color = edge, start = Offset(left, top), end = Offset(right, bottom))
    drawLine(color = edge, start = Offset(right, top), end = Offset(left, bottom))
}

/**
 * The keeper, facing the way it last pushed: a disc with a bright mark on the
 * leading edge, which is enough to read at 25px and costs two shapes.
 */
private fun DrawScope.keeper(
    box: Box,
    facing: Direction?,
) {
    val centre = Offset(box.x + box.w / 2f, box.y + box.h / 2f)
    val radius = maxOf(2f, box.w * KEEPER_RADIUS)
    val eye = maxOf(1f, box.w * KEEPER_EYE)
    val reach = radius - eye
    val mark =
        if (facing == null) {
            centre
        } else {
            Offset(centre.x + facing.dx * reach, centre.y + facing.dy * reach)
        }

    drawCircle(color = ColorKeeper, radius = radius, center = centre)
    drawCircle(color = ColorKeeperFace, radius = eye, center = mark)
}

/**
 * The goal marker: a ring, like a painted circle on a warehouse floor. Not a pit -
 * the crate does not fall into it, it just has to end up there, and a pit would
 * promise something the rules do not do.
 */
private fun DrawScope.goalRing(box: Box) {
    val width = maxOf(1f, box.w * GOAL_RING)
    drawCircle(
        color = ColorGoal,
        radius = maxOf(2f, box.w * GOAL_RADIUS),
        center = Offset(box.x + box.w / 2f, box.y + box.h / 2f),
        style = Stroke(width = width),
    )
}

/** One filled rectangle in whole screen pixels. */
private fun DrawScope.fill(
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    colour: Color,
) {
    if (w <= 0 || h <= 0) return
    drawRect(color = colour, topLeft = Offset(x.toFloat(), y.toFloat()), size = Size(w.toFloat(), h.toFloat()))
}
