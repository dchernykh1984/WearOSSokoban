package com.dchernykh.sokoban.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.dchernykh.sokoban.game.Game
import com.dchernykh.sokoban.layout.BOARD_EDGE
import com.dchernykh.sokoban.layout.BoardWindow
import com.dchernykh.sokoban.layout.Camera
import com.dchernykh.sokoban.layout.Grid
import com.dchernykh.sokoban.layout.cellBox
import com.dchernykh.sokoban.layout.visibleCells

/**
 * The warehouse on one canvas: the floor, the walls, the goals, the crates and the
 * keeper, clipped to the square window the round screen leaves in the middle.
 *
 * Only the cells the window can actually see are drawn. The largest warehouse is
 * 361 cells and the window shows at most 121 of them, so drawing the rest would be
 * two thirds wasted work every frame - and, on a canvas that covers the whole
 * screen, would paint straight over the arrows in the segments around the board.
 */
@Composable
fun BoardCanvas(
    game: Game,
    window: BoardWindow,
    camera: Camera,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val level = game.level
        val range = visibleCells(camera, window, Grid(level.cols, level.rows))
        val cell = window.cell.toFloat()

        insideWindow(window) {
            for (row in range.fromY..range.toY) {
                for (col in range.fromX..range.toX) {
                    val index = level.indexOf(col, row)
                    val box = cellBox(camera, window, col, row)
                    val at = Offset(box.x.toFloat(), box.y.toFloat())
                    drawRect(
                        color = groundOf(level.isWall(index), level.isGoal(index)),
                        topLeft = at,
                        size = Size(cell, cell),
                    )
                    drawContents(game, index, at, cell)
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

private fun groundOf(
    wall: Boolean,
    goal: Boolean,
) = when {
    wall -> ColorWall
    goal -> ColorGoal
    else -> ColorFloor
}

/** The goal mark, the crate or the keeper standing on one cell. */
private fun DrawScope.drawContents(
    game: Game,
    index: Int,
    at: Offset,
    cell: Float,
) {
    val level = game.level
    val centre = Offset(at.x + cell / 2f, at.y + cell / 2f)

    // The goal mark is drawn under everything, so a goal still reads as one with a
    // crate parked on it - the crate covers the middle, not the ring.
    if (level.isGoal(index)) {
        drawCircle(
            color = ColorGoalMark,
            radius = cell * 0.16f,
            center = centre,
            style =
                Stroke(
                    width =
                        maxOf(
                            1f,
                            cell * 0.07f,
                        ),
                ),
        )
    }

    if (game.hasBox(index)) {
        val inset = cell * 0.14f
        drawRect(
            color = if (level.isGoal(index)) ColorBoxHome else ColorBox,
            topLeft = Offset(at.x + inset, at.y + inset),
            size = Size(cell - 2 * inset, cell - 2 * inset),
        )
    }

    if (game.player == index) {
        drawCircle(color = ColorKeeper, radius = cell * 0.33f, center = centre)
    }
}
