package com.dchernykh.sokoban.game

/**
 * The four grid directions, clockwise from up.
 *
 * Shared by the rule set, the generator and the touch handling. An enum rather
 * than the original's indexes into a vector table: the compiler then knows a
 * direction is one of four, so nothing has to check.
 */
enum class Direction(
    val dx: Int,
    val dy: Int,
) {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0),
    ;

    val opposite: Direction get() = entries[(ordinal + 2) % entries.size]
}
