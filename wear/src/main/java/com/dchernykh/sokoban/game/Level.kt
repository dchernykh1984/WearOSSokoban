package com.dchernykh.sokoban.game

// A warehouse, and the rules of walking round one.
//
// The whole rule set is plain Kotlin with nothing Android in it, so every rule is
// exercised by a unit test rather than by squinting at a watch.
//
// A level is immutable: the walls, the goals, and where the crates and the keeper
// began. Cells are flat indexes into a cols x rows grid, which is what lets a
// crate be a single number rather than a pair.
//
// The one rule that makes Sokoban Sokoban: a crate can only ever be PUSHED. There
// is no pull, which is why a crate shoved into a corner is lost - hence undo,
// which is part of the game rather than a convenience.

/** A warehouse as it starts: walls, goals, and where everything stood. */
class Level(
    val cols: Int,
    val rows: Int,
    /** One flag per cell, indexed by `row * cols + col`. */
    val walls: BooleanArray,
    val goals: IntArray,
    val boxes: IntArray,
    val player: Int,
) {
    val cells: Int get() = cols * rows

    /** One flag per cell, so "is this a goal" is a lookup rather than a search. */
    val goalFlags: BooleanArray = BooleanArray(cells).also { flags -> goals.forEach { flags[it] = true } }

    fun columnOf(index: Int): Int = index % cols

    fun rowOf(index: Int): Int = index / cols

    fun indexOf(
        col: Int,
        row: Int,
    ): Int = row * cols + col

    fun inside(
        col: Int,
        row: Int,
    ): Boolean = col in 0 until cols && row in 0 until rows

    /**
     * Anything off the grid counts as wall. Generated levels always carry a solid
     * border so this cannot happen in play, but a rule that answers safely off the
     * edge keeps every caller free of its own bounds check.
     */
    fun isWall(
        col: Int,
        row: Int,
    ): Boolean = !inside(col, row) || walls[indexOf(col, row)]

    fun isWall(index: Int): Boolean = walls[index]

    fun isGoal(index: Int): Boolean = index in 0 until cells && goalFlags[index]

    /** Where a step in this direction lands, or -1 when it leaves the grid. */
    fun step(
        index: Int,
        direction: Direction,
    ): Int {
        val col = columnOf(index) + direction.dx
        val row = rowOf(index) + direction.dy
        return if (inside(col, row)) indexOf(col, row) else -1
    }

    fun floorCells(): IntArray = (0 until cells).filter { !walls[it] }.toIntArray()

    fun floorCount(): Int = walls.count { !it }
}

/** One step of the game as the undo stack remembers it. */
data class Step(
    val direction: Direction,
    val pushed: Boolean,
)

/**
 * A warehouse being played: where the crates and the keeper are now, what it took
 * to get there, and how to take it back.
 *
 * Immutable, so undo is a matter of keeping the state before the move rather than
 * unpicking the one after it - and so Compose can tell one position from the next.
 */
class Game(
    val level: Level,
    val boxes: IntArray,
    val player: Int,
    val moves: Int,
    val pushes: Int,
    val history: List<Step>,
) {
    /** Which crate sits on the cell, as its slot in [boxes], or -1. */
    fun boxSlot(index: Int): Int = boxes.indexOf(index)

    fun hasBox(index: Int): Boolean = index >= 0 && boxes.contains(index)

    /** A cell the keeper may stand on: on the grid, not a wall and not a crate. */
    fun isFree(index: Int): Boolean = index >= 0 && !level.isWall(index) && !hasBox(index)

    val boxesOnGoals: Int get() = boxes.count { level.isGoal(it) }

    /**
     * Solved when every goal carries a crate. Levels are generated with as many
     * crates as goals, so counting the covered ones is enough.
     */
    val isSolved: Boolean get() = level.goals.isNotEmpty() && boxesOnGoals == level.goals.size

    /**
     * Step the keeper one cell, pushing a single crate if one is in the way, or
     * null when nothing happens.
     *
     * A move into a wall, into a crate backed by a wall, or into a crate backed by
     * another crate does nothing at all - Sokoban never shoves two crates at once.
     */
    fun moved(direction: Direction): Game? {
        val to = level.step(player, direction)
        if (to < 0 || level.isWall(to)) return null

        val slot = boxSlot(to)
        var nextBoxes = boxes
        if (slot >= 0) {
            val beyond = level.step(to, direction)
            if (!isFree(beyond)) return null
            nextBoxes = boxes.copyOf().also { it[slot] = beyond }
        }
        return Game(
            level = level,
            boxes = nextBoxes,
            player = to,
            moves = moves + 1,
            pushes = pushes + if (slot >= 0) 1 else 0,
            history = history + Step(direction, pushed = slot >= 0),
        )
    }

    /**
     * Take back the last step, dragging the crate back with it when that step was a
     * push, or null when there is nothing to take back.
     */
    fun undone(): Game? {
        val last = history.lastOrNull() ?: return null
        val back = level.step(player, last.direction.opposite)
        if (back < 0) return null

        var nextBoxes = boxes
        var nextPushes = pushes
        if (last.pushed) {
            // The crate that was pushed is the one directly ahead of the keeper. The
            // counter only comes down if it was actually found and dragged back, so
            // a history that has somehow come adrift from the board cannot leave the
            // game claiming pushes that are not on it.
            val slot = boxSlot(level.step(player, last.direction))
            if (slot >= 0) {
                nextBoxes = boxes.copyOf().also { it[slot] = player }
                nextPushes -= 1
            }
        }
        return Game(
            level = level,
            boxes = nextBoxes,
            player = back,
            moves = moves - 1,
            pushes = nextPushes,
            history = history.dropLast(1),
        )
    }

    /** Back to how the warehouse started, with the history thrown away. */
    fun restarted(): Game = newGame(level)

    companion object {
        fun newGame(level: Level): Game =
            Game(
                level = level,
                boxes = level.boxes.copyOf(),
                player = level.player,
                moves = 0,
                pushes = 0,
                history = emptyList(),
            )
    }
}
