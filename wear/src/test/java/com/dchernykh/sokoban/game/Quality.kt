package com.dchernykh.sokoban.game

// Measuring whether a warehouse is a good PUZZLE, as opposed to a valid one.
//
// This is a vetting tool, not game code, which is why it lives in the test source
// set: the shipped collection was measured by it on a computer before it shipped,
// and the tests here measure it again. Nothing on the watch asks any of these
// questions - by the time a warehouse reaches a player it has already been judged.
//
// The generator already guarantees a level can be finished, and the solver already
// rejects levels that fall over in a couple of pushes. Neither catches the three
// ways a technically-correct level can still be a poor one, all of which turned up
// when a sample of the shipped collection was read by hand:
//
//   1. A quarter of the map holds no crate and no goal, so the player pans into
//      empty rooms and learns nothing.
//   2. Every crate starts one push from a goal. The map is big, the puzzle is not:
//      it decomposes into N independent nudges instead of one problem.
//   3. The keeper starts sealed in a pocket where the only legal move is a forced
//      push, so the opening carries no decision at all.
//
// All three are cheap to measure and none of them need a solver, which matters: on
// the big sizes the solver cannot finish, so these are the only quality signals
// available there.

/** Distances from one cell to every other across the floor, ignoring crates. */
fun floorDistances(
    level: Level,
    from: Int,
): IntArray {
    val distance = IntArray(level.cells) { -1 }
    distance[from] = 0
    val queue = ArrayDeque<Int>()
    queue.addLast(from)

    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        for (direction in Direction.entries) {
            val next = level.step(index, direction)
            if (next >= 0 && !level.isWall(next) && distance[next] == -1) {
                distance[next] = distance[index] + 1
                queue.addLast(next)
            }
        }
    }
    return distance
}

/** Everything worth knowing about a warehouse's shape, in one pass. */
data class Shape(
    val emptyBlock: Float,
    val lowerBound: Int,
    val freedom: Int,
)

/**
 * The largest block of the warehouse that holds neither a crate nor a goal, as a
 * share of the floor. A big empty block is the dead scenery the player pans
 * through for nothing.
 */
fun emptyBlockShare(level: Level): Float {
    val interesting = BooleanArray(level.cells)
    level.goals.forEach { interesting[it] = true }
    level.boxes.forEach { interesting[it] = true }

    val floor = level.floorCount()
    if (floor == 0) return 1f

    // Largest rectangle of cells that are floor and hold nothing, by the standard
    // histogram sweep: heights per column, then the widest bar under each.
    val heights = IntArray(level.cols)
    var best = 0
    for (row in 0 until level.rows) {
        for (col in 0 until level.cols) {
            val index = level.indexOf(col, row)
            heights[col] = if (!level.isWall(index) && !interesting[index]) heights[col] + 1 else 0
        }
        best = maxOf(best, widestBar(heights))
    }
    return best / floor.toFloat()
}

/** The largest rectangle under a histogram, by the usual stack sweep. */
private fun widestBar(heights: IntArray): Int {
    var best = 0
    val stack = ArrayDeque<Int>()
    for (i in 0..heights.size) {
        val height = if (i == heights.size) 0 else heights[i]
        while (stack.isNotEmpty() && heights[stack.last()] >= height) {
            val top = stack.removeLast()
            val left = if (stack.isEmpty()) -1 else stack.last()
            best = maxOf(best, heights[top] * (i - left - 1))
        }
        stack.addLast(i)
    }
    return best
}

/**
 * How far every crate has to travel, at the very least: the cheapest way of
 * pairing each crate with a goal, measured as distance across the floor.
 *
 * It is a LOWER bound on the number of pushes - a crate cannot reach its goal in
 * fewer moves than the distance - and it is the honest way to spot a level where
 * every crate is already sitting next to its goal.
 */
fun pushLowerBound(level: Level): Int {
    if (level.boxes.isEmpty()) return 0
    val table = level.goals.map { floorDistances(level, it) }
    return cheapestPairing(table, level.boxes, BooleanArray(level.goals.size), 0)
}

/**
 * The cheapest crate-to-goal pairing, by trying them all. With at most seven
 * crates that is 5040 combinations of a handful of lookups - nothing, and exact.
 */
private fun cheapestPairing(
    table: List<IntArray>,
    boxes: IntArray,
    used: BooleanArray,
    at: Int,
): Int {
    if (at >= boxes.size) return 0
    var best = -1
    for (goal in table.indices) {
        val step = if (used[goal]) -1 else table[goal][boxes[at]]
        if (step >= 0) {
            used[goal] = true
            val rest = cheapestPairing(table, boxes, used, at + 1)
            used[goal] = false
            val total = if (rest < 0) -1 else step + rest
            if (total >= 0 && (best == -1 || total < best)) best = total
        }
    }
    return best
}

/**
 * How many cells the keeper can walk to before touching anything. A keeper sealed
 * into a pocket has no opening move worth making - its first move is forced, and a
 * forced move is not a decision.
 */
fun keeperFreedom(level: Level): Int {
    val blocked = BooleanArray(level.cells).also { flags -> level.boxes.forEach { flags[it] = true } }
    return walkableFrom(level, blocked, level.player).count { it }
}

fun measure(level: Level): Shape =
    Shape(
        emptyBlock = emptyBlockShare(level),
        lowerBound = pushLowerBound(level),
        freedom = keeperFreedom(level),
    )
