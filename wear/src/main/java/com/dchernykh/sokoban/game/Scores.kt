package com.dchernykh.sokoban.game

// The best result, kept apart from the storage that holds it so the rule is unit
// tested.
//
// Sokoban scores the other way round from most games: the fewest moves wins. So
// zero is not a bad score, it is the absence of one - which is what a fresh install
// has, and what the screen shows as a dash.
//
// A best is kept per size AND per source. Solving a 19x19 warehouse in forty moves
// says something quite different from solving a 9x9 one in forty - and a level the
// watch rolled at random is not the same challenge as one that was put through a
// solver before it shipped, so they do not share a record.

/** No record yet. Named because zero reads as "solved in no moves" otherwise. */
const val NO_BEST = 0

/** The most moves worth storing, so a number can never outgrow the box it is drawn in. */
const val MAX_MOVES = 9999

/** A best result and whether the game just finished is the one that set it. */
data class BestOutcome(
    val best: Int,
    val isRecord: Boolean,
)

/**
 * A stored value coerced to a usable move count. Storage can hand back nothing or
 * leftover junk from an older build; none of that may crash the game or show up on
 * screen, so anything unusable reads as no record.
 */
fun normalizeMoves(value: Int?): Int {
    val moves = value ?: NO_BEST
    if (moves <= NO_BEST) return NO_BEST
    return minOf(MAX_MOVES, moves)
}

fun hasBest(value: Int?): Boolean = normalizeMoves(value) > NO_BEST

/**
 * The best after a solved warehouse, and whether it is a new record. Fewer moves
 * wins, and the first solve always sets the record because there was none.
 */
fun updateBest(
    previousBest: Int?,
    moves: Int?,
): BestOutcome {
    val best = normalizeMoves(previousBest)
    val result = normalizeMoves(moves)
    return if (result > NO_BEST && (best == NO_BEST || result < best)) {
        BestOutcome(result, isRecord = true)
    } else {
        BestOutcome(best, isRecord = false)
    }
}
