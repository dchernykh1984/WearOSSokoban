package com.dchernykh.sokoban.game

// Getting about a warehouse: where the keeper can walk, how it gets from one cell
// to another, and what a run of moves touches on the way.
//
// Kept apart from both the rules and the generator because all three need it and
// none of them should have to depend on the others.

/** Whether a step lands somewhere walkable that has not been reached already. */
private fun canWalk(
    level: Level,
    blocked: BooleanArray,
    seen: BooleanArray,
    index: Int,
): Boolean = index >= 0 && !level.isWall(index) && !seen[index] && !blocked[index]

/**
 * Every cell the keeper can walk to from a starting point without touching a crate,
 * as a flat array of flags so membership is a single lookup.
 */
fun walkableFrom(
    level: Level,
    blocked: BooleanArray,
    start: Int,
): BooleanArray {
    val seen = BooleanArray(level.cells)
    if (start < 0 || level.isWall(start) || blocked[start]) return seen

    val queue = ArrayDeque<Int>()
    seen[start] = true
    queue.addLast(start)
    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        for (direction in Direction.entries) {
            val next = level.step(index, direction)
            if (canWalk(level, blocked, seen, next)) {
                seen[next] = true
                queue.addLast(next)
            }
        }
    }
    return seen
}

/** Whether every floor cell of a shape can be walked to from every other one. */
fun isFloorConnected(
    cols: Int,
    rows: Int,
    walls: BooleanArray,
): Boolean {
    val floor = (0 until cols * rows).filter { !walls[it] }
    if (floor.isEmpty()) return false
    val level = Level(cols, rows, walls, IntArray(0), IntArray(0), floor[0])
    val seen = walkableFrom(level, BooleanArray(cols * rows), floor[0])
    return floor.all { seen[it] }
}

/** The direction that steps from one cell to a neighbouring one, or null. */
fun directionBetween(
    level: Level,
    from: Int,
    to: Int,
): Direction? {
    val dx = level.columnOf(to) - level.columnOf(from)
    val dy = level.rowOf(to) - level.rowOf(from)
    return Direction.entries.firstOrNull { it.dx == dx && it.dy == dy }
}

/** The walk a breadth-first search found, read back from where it came. */
private fun walkBack(
    cameFrom: IntArray,
    cameBy: Array<Direction?>,
    from: Int,
    to: Int,
): List<Direction> {
    val path = ArrayDeque<Direction>()
    var cursor = to
    while (cursor != from) {
        path.addFirst(cameBy[cursor] ?: return path.toList())
        cursor = cameFrom[cursor]
    }
    return path.toList()
}

/**
 * The shortest walk from one cell to another as a list of directions, or null when
 * there is no way through. Used only to stitch a generated level's certificate
 * together: between two pushes the keeper still has to walk to the next crate.
 */
fun findPath(
    level: Level,
    boxes: IntArray,
    from: Int,
    to: Int,
): List<Direction>? {
    if (from == to) return emptyList()
    val blocked = BooleanArray(level.cells).also { flags -> boxes.forEach { flags[it] = true } }
    val cameFrom = IntArray(level.cells) { -1 }
    val cameBy = arrayOfNulls<Direction>(level.cells)
    val seen = BooleanArray(level.cells)

    val queue = ArrayDeque<Int>()
    seen[from] = true
    queue.addLast(from)
    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        for (direction in Direction.entries) {
            val next = level.step(index, direction)
            if (!canWalk(level, blocked, seen, next)) continue
            seen[next] = true
            cameFrom[next] = index
            cameBy[next] = direction
            if (next == to) return walkBack(cameFrom, cameBy, from, to)
            queue.addLast(next)
        }
    }
    return null
}

/**
 * Every cell a run of moves touches: where the keeper walks and where the crates
 * travel. This is the honest measure of how much of a warehouse is in play, as
 * opposed to how big it is.
 */
fun workingArea(
    level: Level,
    solution: List<Direction>,
): Int {
    val used = BooleanArray(level.cells)
    val boxes = level.boxes.copyOf()
    var at = level.player
    used[at] = true
    boxes.forEach { used[it] = true }

    // One exit: the walk runs while every step is still on the grid, so a solution
    // that has come adrift from its level stops rather than reading off the end.
    var walking = true
    for (direction in solution) {
        if (!walking) break
        val next = level.step(at, direction)
        val slot = if (next < 0) -1 else boxes.indexOf(next)
        val beyond = if (slot >= 0) level.step(next, direction) else 0
        walking = next >= 0 && beyond >= 0
        if (walking) {
            if (slot >= 0) {
                boxes[slot] = beyond
                used[beyond] = true
            }
            at = next
            used[at] = true
        }
    }
    return used.count { it }
}

/** The share of the floor a run of moves puts to use. */
fun coverage(
    level: Level,
    solution: List<Direction>,
): Float {
    val floor = level.floorCount()
    if (floor == 0) return 0f
    return workingArea(level, solution) / floor.toFloat()
}
