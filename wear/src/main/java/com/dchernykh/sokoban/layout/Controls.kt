package com.dchernykh.sokoban.layout

import com.dchernykh.sokoban.game.Direction
import kotlin.math.roundToInt

// Where the controls sit on a round screen, and what is under a finger.
//
// The board is the square inscribed in the circle, which leaves four segments
// around it - one at each edge, about 70px thick in the middle and tapering to
// nothing at the corners. Those segments are dead space on a round watch, and they
// are exactly where the four direction arrows go: the map keeps the whole board to
// itself, and steering never fights with dragging.
//
// The bottom segment carries three controls rather than one, because undo and the
// menu have to live somewhere: [undo] [down] [menu] in a row.
//
// All of it is pure geometry, so a test can ask what a tap at a point would do
// without a screen in the room.

/** How much of a segment a control fills. The arrows are generous - they are aimed at constantly. */
private const val ARROW_SPAN = 0.72f
private const val ARROW_HEIGHT = 0.34f
private const val SEGMENT_FILL = 0.55f
private const val COUNTER_FILL = 0.35f
private const val ARROW_FILL = 0.5f

/**
 * How far down the top segment the counters start.
 *
 * Not at the very top: a round screen is barely 70px across up there, and a line
 * like "0/7  123" needs 110. A few pixels lower the chord has opened up enough to
 * hold it, and the up arrow still has its full height underneath.
 */
private const val COUNTER_TOP = 0.14f

const val EDGE_PADDING = 8

/** The frame drawn round the window, and the gap that keeps the bottom row clear of it. */
const val BOARD_EDGE = 2
private const val ROW_TOP_GAP = BOARD_EDGE + 1

/**
 * The bottom row is not three equal buttons.
 *
 * The down arrow is steering, pressed hundreds of times a game; undo and the menu
 * are pressed a handful of times and both are unwelcome surprises mid-solve - undo
 * silently takes a move back, the menu covers the board. So the arrow is the widest
 * of the three, the other two are pushed out to the ends of the row, and what is
 * left between them is dead space that belongs to nobody: a thumb landing slightly
 * wide of the arrow does nothing at all rather than undoing the move it just made.
 */
private const val DOWN_SHARE = 0.32f
private const val SIDE_SHARE = 0.22f

/** How thick an arrow is drawn, and how far it reaches, as shares of the tightest button. */
private const val ARROW_WEIGHT = 0.14f
private const val ARROW_REACH = 0.42f

/**
 * How big to draw an arrow, given every button that has to hold one.
 *
 * One size for all four, not one per button. The buttons are nowhere near the same
 * shape - a wide shallow strip along the top, a tall narrow one down each side, a
 * short wide one at the bottom - and sizing each arrow to its own box draws four
 * different symbols: the bottom one half the span of the sides, at three different
 * stroke weights. Four arrows that are the same size and weight read as one set of
 * controls; four that are not read as clutter.
 *
 * The smallest button decides, so the shared size always fits every one of them,
 * stroke included.
 */
data class ArrowMetrics(
    val reach: Int,
    val width: Int,
)

/** How far the arrow reaches from the middle of its box, and how thick it is drawn. */
fun arrowMetrics(boxes: List<Box>): ArrowMetrics {
    var shortest = Int.MAX_VALUE
    // How far the centre can travel before it leaves the tightest button. Measured
    // from the centre the arrow is actually drawn around, which is a whole pixel and
    // so is not exactly half way across an odd-sized button.
    var room = Int.MAX_VALUE

    for (box in boxes) {
        shortest = minOf(shortest, box.w, box.h)
        val midX = box.x + box.w / 2
        val midY = box.y + box.h / 2
        room = minOf(room, midX - box.x, box.x + box.w - midX, midY - box.y, box.y + box.h - midY)
    }
    if (boxes.isEmpty() || shortest <= 0 || room <= 0) return ArrowMetrics(0, 0)

    val width = maxOf(2, (shortest * ARROW_WEIGHT).roundToInt())
    // Half the stroke hangs outside the endpoint it is drawn from, so the reach has
    // to leave room for it or the arrow overhangs its button.
    val reach = maxOf(0, minOf((shortest * ARROW_REACH).toInt(), room - width / 2))
    return ArrowMetrics(reach, width)
}

/** What a touch landed on. */
sealed interface Hit {
    data class Step(
        val direction: Direction,
    ) : Hit

    data object Undo : Hit

    data object Menu : Hit

    data object Board : Hit
}

/** Every control, for a screen of this size with the window in this place. */
data class ControlLayout(
    val board: Box,
    val counter: Box,
    val up: Box,
    val down: Box,
    val left: Box,
    val right: Box,
    val undo: Box,
    val menu: Box,
)

private fun box(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
) = Box(x.roundToInt(), y.roundToInt(), w.roundToInt(), h.roundToInt())

/**
 * The top and bottom controls are sized through the chord of the circle at their
 * own height, not the full width: near the top and bottom of a round screen a
 * centred row is much narrower than the screen, and a row laid out as if it were
 * square has its ends sliced off by the bezel.
 *
 * The bottom row sits just under the board rather than centred in its segment. A
 * round screen narrows fast towards the bottom: centred, the row could only be as
 * wide as the chord at its lowest edge, which is 131px on a 466 screen - three
 * buttons in that are shoulder to shoulder. Moved up against the board it gets over
 * 200px to share, and that width buys the gaps.
 */
fun controlLayout(
    screenSize: Int,
    window: BoardWindow,
): ControlLayout {
    val top = window.y
    val bottom = screenSize - (window.y + window.size)
    val side = window.x
    val middle = window.y + window.size / 2f

    val armWidth = (side * ARROW_SPAN).roundToInt()
    val armHeight = (window.size * ARROW_HEIGHT).roundToInt()

    // The top segment carries two things: the counters above and the up arrow below
    // them. Splitting it is what keeps the arrows symmetrical - putting the counters
    // anywhere else would leave one segment doing nothing.
    val counterHeight = (top * COUNTER_FILL).roundToInt()
    val counterTop = (top * COUNTER_TOP).roundToInt()
    val counter = centeredBox(screenSize, counterTop, counterHeight, window.size.toFloat(), EDGE_PADDING)

    val upHeight = (top * ARROW_FILL).roundToInt()
    val up = centeredBox(screenSize, counterTop + counterHeight, upHeight, window.size * 0.5f, EDGE_PADDING)

    val rowHeight = (bottom * SEGMENT_FILL).roundToInt()
    val row =
        centeredBox(screenSize, window.y + window.size + ROW_TOP_GAP, rowHeight, window.size.toFloat(), EDGE_PADDING)
    val downWidth = (row.w * DOWN_SHARE).roundToInt()
    val sideWidth = (row.w * SIDE_SHARE).roundToInt()

    return ControlLayout(
        board = Box(window.x, window.y, window.size, window.size),
        counter = counter,
        up = up,
        down = box(row.x + (row.w - downWidth) / 2f, row.y.toFloat(), downWidth.toFloat(), row.h.toFloat()),
        left = box((side - armWidth) / 2f, middle - armHeight / 2f, armWidth.toFloat(), armHeight.toFloat()),
        right =
            box(
                (window.x + window.size + (side - armWidth) / 2f),
                middle - armHeight / 2f,
                armWidth.toFloat(),
                armHeight.toFloat(),
            ),
        undo = Box(row.x, row.y, sideWidth, row.h),
        menu = Box(row.x + row.w - sideWidth, row.y, sideWidth, row.h),
    )
}

/**
 * What a touch at this point is aimed at, or null for the dead space between
 * controls. The arrows are checked before the board so a stray pixel of overlap can
 * never steal a step.
 */
fun hitTest(
    layout: ControlLayout,
    x: Int,
    y: Int,
): Hit? {
    val point = x to y
    return when {
        point in layout.up -> Hit.Step(Direction.UP)
        point in layout.down -> Hit.Step(Direction.DOWN)
        point in layout.left -> Hit.Step(Direction.LEFT)
        point in layout.right -> Hit.Step(Direction.RIGHT)
        point in layout.undo -> Hit.Undo
        point in layout.menu -> Hit.Menu
        point in layout.board -> Hit.Board
        else -> null
    }
}
