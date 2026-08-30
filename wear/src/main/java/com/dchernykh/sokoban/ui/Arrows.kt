package com.dchernykh.sokoban.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dchernykh.sokoban.game.Direction
import com.dchernykh.sokoban.layout.ArrowMetrics
import com.dchernykh.sokoban.layout.Box as LayoutBox

// The controls, drawn rather than lettered.
//
// An arrow reads as a direction in any of the eleven languages, needs no glyph a
// font might not carry, and scales with the screen like everything else here. The
// two buttons beside the down arrow are icons for the same reason - and because a
// word in a 60px pill on a round watch is a word nobody can read anyway.
//
// Every one of them is a chevron or a bar of the same weight, because they sit in
// one row and a hairline icon beside a thick arrow looks like a different app drew
// it.

/**
 * One direction arrow: a chevron in a box the finger can miss the middle of.
 *
 * The chevron points the way it steps, which is the whole of the instruction. It
 * lights up while pressed, because on a watch there is no cursor and no hover - the
 * only feedback a finger gets is the thing under it changing.
 */
@Composable
fun ArrowButton(
    box: LayoutBox,
    direction: Direction,
    metrics: ArrowMetrics,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(box, label, onClick) { colour ->
        drawArrow(direction, metrics, colour)
    }
}

/** Undo: an arrow curving back on itself, drawn as a shaft with a head. */
@Composable
fun UndoButton(
    box: LayoutBox,
    metrics: ArrowMetrics,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(box, label, onClick) { colour ->
        val midY = size.height / 2f
        val left = size.width * ICON_INSET
        val right = size.width * (1f - ICON_INSET)
        val head = maxOf(2f, size.width * ICON_HEAD)

        stroke(Offset(left, midY), Offset(right, midY), metrics.width, colour)
        stroke(Offset(left, midY), Offset(left + head, midY - head), metrics.width, colour)
        stroke(Offset(left, midY), Offset(left + head, midY + head), metrics.width, colour)
        stroke(Offset(right, midY), Offset(right, midY - head), metrics.width, colour)
    }
}

/** The menu: three stacked bars, at the same weight as everything else in the row. */
@Composable
fun MenuButton(
    box: LayoutBox,
    metrics: ArrowMetrics,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(box, label, onClick) { colour ->
        val left = size.width * ICON_INSET
        val right = size.width * (1f - ICON_INSET)
        val midY = size.height / 2f
        // The bars have to clear each other once they are drawn thick, so the gap is
        // measured from the stroke and not only from the button.
        val gap = maxOf(2f, size.height * ICON_HEAD, metrics.width * BAR_CLEARANCE)

        for (y in listOf(midY - gap, midY, midY + gap)) {
            stroke(Offset(left, y), Offset(right, y), metrics.width, colour)
        }
    }
}

/** How far in from the edges of its button an icon is drawn, and how big its head is. */
private const val ICON_INSET = 0.28f
private const val ICON_HEAD = 0.16f

/** How far apart the menu's bars are, measured in strokes, so they never merge. */
private const val BAR_CLEARANCE = 1.6f

/** A control that is drawn rather than lettered, and lights up under a finger. */
@Composable
private fun IconButton(
    box: LayoutBox,
    label: String,
    onClick: () -> Unit,
    draw: DrawScope.(Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier =
            Modifier
                .absoluteBox(box)
                .pressable(interactionSource, label, onClick = onClick),
    ) {
        Canvas(modifier = Modifier.absoluteBox(LayoutBox(0, 0, box.w, box.h))) {
            draw(if (pressed) ColorArrowPressed else ColorArrow)
        }
    }
}

/**
 * A chevron: two thick strokes meeting at the tip, drawn round the middle of the
 * box it is given.
 */
private fun DrawScope.drawArrow(
    direction: Direction,
    metrics: ArrowMetrics,
    colour: Color,
) {
    val midX = size.width / 2f
    val midY = size.height / 2f
    val reach = metrics.reach.toFloat()

    val tip = Offset(midX + direction.dx * reach, midY + direction.dy * reach)
    val across = Offset(direction.dy * reach, direction.dx * reach)
    val back = Offset(midX - direction.dx * reach, midY - direction.dy * reach)

    stroke(back + across, tip, metrics.width, colour)
    stroke(tip, back - across, metrics.width, colour)
}

/** One stroke of an icon, with the round cap that keeps two of them meeting cleanly. */
private fun DrawScope.stroke(
    from: Offset,
    to: Offset,
    width: Int,
    colour: Color,
) {
    drawLine(color = colour, start = from, end = to, strokeWidth = width.toFloat(), cap = StrokeCap.Round)
}
