package com.dchernykh.sokoban.game

// Laying a warehouse out: the room, the blocks dropped in it, and where the crates
// have to end up.
//
// This is the half of generation that happens before the game is played backwards -
// a shape and a set of goals - and it is kept apart from the reverse play because
// the two answer different questions. This one decides what the warehouse looks
// like; Generator.kt decides what the puzzle is.

/** The least floor a layout may keep after the random blocks are dropped in. */
private const val MIN_FLOOR_FRACTION = 0.6f

/** How many random block placements are attempted per block actually wanted. */
private const val BLOCK_TRIES = 20

/** The walls of a room: a solid border with an open interior. */
private fun emptyRoom(
    cols: Int,
    rows: Int,
): BooleanArray {
    val walls = BooleanArray(cols * rows) { true }
    for (row in 1 until rows - 1) {
        for (col in 1 until cols - 1) walls[row * cols + col] = false
    }
    return walls
}

/**
 * A room with random blocks dropped inside it.
 *
 * A block is kept only if the floor stays in one piece, so the keeper can always
 * walk the whole warehouse. Bounded rather than looping until the blocks land: on
 * a small room most candidates cut the floor in two and it would spin forever.
 */
fun carveWalls(
    cols: Int,
    rows: Int,
    blocks: Int,
    random: Mulberry32,
): BooleanArray {
    val walls = emptyRoom(cols, rows)
    val interior = (cols - 2) * (rows - 2)
    val floor = (interior * MIN_FLOOR_FRACTION).toInt()
    val wanted = maxOf(0, minOf(blocks, interior - floor))
    var placed = 0

    var tries = 0
    while (tries < wanted * BLOCK_TRIES && placed < wanted) {
        tries += 1
        val index = (1 + random.nextInt(rows - 2)) * cols + 1 + random.nextInt(cols - 2)
        if (!walls[index]) {
            walls[index] = true
            if (isFloorConnected(cols, rows, walls)) placed += 1 else walls[index] = false
        }
    }
    return walls
}

/**
 * The sector grid the goals are spread over.
 *
 * Taking each goal from a different cell of this grid is what stops them all
 * landing in the same corner in the first place; the coverage filter is the safety
 * net behind it.
 */
private fun sectorGrid(cols: Int): Int = if (cols <= 11) 2 else 3

fun sectorOf(
    level: Level,
    index: Int,
    divisions: Int,
): Int {
    val col = minOf(divisions - 1, level.columnOf(index) * divisions / level.cols)
    val row = minOf(divisions - 1, level.rowOf(index) * divisions / level.rows)
    return row * divisions + col
}

/**
 * Goals taken from as many different sectors as possible.
 *
 * The sectors are walked in a random order and contribute one cell each, round
 * after round, so a warehouse gets its targets spread across it rather than piled
 * in one place. Null when there are not enough cells to go round.
 */
fun spreadGoals(
    level: Level,
    candidates: IntArray,
    count: Int,
    random: Mulberry32,
): IntArray? {
    val divisions = sectorGrid(level.cols)
    val buckets = LinkedHashMap<Int, MutableList<Int>>()
    for (cell in candidates) buckets.getOrPut(sectorOf(level, cell, divisions)) { mutableListOf() }.add(cell)

    val order = buckets.keys.toMutableList()
    for (i in order.indices.reversed()) {
        if (i == 0) break
        val j = random.nextInt(i + 1)
        val swap = order[i]
        order[i] = order[j]
        order[j] = swap
    }

    val goals = mutableListOf<Int>()
    while (goals.size < count) {
        var took = 0
        for (key in order) {
            val bucket = buckets.getValue(key)
            if (goals.size < count && bucket.isNotEmpty()) {
                goals.add(bucket.removeAt(random.nextInt(bucket.size)))
                took += 1
            }
        }
        // A whole round that could take nothing means the sectors are spent, and no
        // number of further rounds will find the goals that are still wanted.
        if (took == 0) return null
    }
    return goals.toIntArray()
}

/**
 * Cells a crate could ever be pulled out of: there has to be a direction with two
 * free cells behind it, one for the crate to move into and one for the keeper to
 * back into. Goals are drawn from these, so the scramble is not handed crates it
 * can never shift.
 */
fun pullableCells(level: Level): IntArray =
    level
        .floorCells()
        .filter { cell ->
            Direction.entries.any { direction ->
                val col = level.columnOf(cell)
                val row = level.rowOf(cell)
                !level.isWall(col + direction.dx, row + direction.dy) &&
                    !level.isWall(col + 2 * direction.dx, row + 2 * direction.dy)
            }
        }.toIntArray()
