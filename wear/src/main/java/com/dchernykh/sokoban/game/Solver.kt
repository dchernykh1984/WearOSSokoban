package com.dchernykh.sokoban.game

// An optimal Sokoban solver.
//
// The generator already guarantees a level CAN be finished - it built the level
// backwards from a solved one and hands over the certificate. What it cannot tell
// you is whether the puzzle is any good. This answers the one question the
// certificate does not: what is the FEWEST pushes a level can be finished in. A
// warehouse that falls over in four pushes is not worth shipping, however big it
// is.
//
// Sokoban is PSPACE-complete, so this is a breadth-first search with the three
// standard economies and a hard budget:
//
//   * search over PUSHES, not steps - where the keeper walks between pushes does
//     not matter, only which crate it shoves next;
//   * normalise the keeper to the smallest cell it can reach, so two positions
//     that differ only in where the keeper is standing are the same state;
//   * refuse to explore a crate frozen in a corner off-goal, which is dead.
//
// Past the budget it gives up and says so, rather than running for ever. For a
// quality filter that is a perfectly good answer: a level the solver cannot crack
// quickly is not a level that is too easy.

/** How a search ended. */
enum class SolveStatus { SOLVED, UNSOLVABLE, EXHAUSTED }

const val DEFAULT_BUDGET = 200_000

/** What a search found. [pushes] is only meaningful when the status is SOLVED. */
data class SolveResult(
    val status: SolveStatus,
    val pushes: Int,
    val states: Int,
)

/**
 * A crate wedged into a corner it cannot be pushed out of.
 *
 * Cheap, and only catches the obvious case - which is the one that matters: it
 * prunes the huge branch of the search where a crate has been shoved somewhere
 * fatal.
 */
fun isCornerDeadlock(
    level: Level,
    index: Int,
): Boolean {
    if (level.isGoal(index)) return false
    val col = level.columnOf(index)
    val row = level.rowOf(index)
    val vertical = level.isWall(col, row - 1) || level.isWall(col, row + 1)
    val horizontal = level.isWall(col - 1, row) || level.isWall(col + 1, row)
    return vertical && horizontal
}

/** Where the keeper can get to, and the smallest cell in that region. */
private class Region(
    val seen: BooleanArray,
    val smallest: Int,
)

/** The cells one step from here that the keeper may walk onto and has not seen. */
private fun stepsFrom(
    level: Level,
    blocked: BooleanArray,
    seen: BooleanArray,
    index: Int,
): List<Int> =
    Direction.entries.mapNotNull { direction ->
        val next = level.step(index, direction)
        next.takeIf { it >= 0 && !level.isWall(it) && !seen[it] && !blocked[it] }
    }

/**
 * The region the keeper can walk without touching a crate.
 *
 * The smallest cell in it is the state's fingerprint: any keeper position in the
 * same region leads to exactly the same set of pushes, so treating them as one
 * state is what keeps the search from exploring the same puzzle over and over.
 */
private fun reach(
    level: Level,
    blocked: BooleanArray,
    from: Int,
): Region {
    val seen = BooleanArray(level.cells)
    val queue = ArrayDeque<Int>()
    seen[from] = true
    queue.addLast(from)
    var smallest = from

    while (queue.isNotEmpty()) {
        for (next in stepsFrom(level, blocked, seen, queue.removeFirst())) {
            seen[next] = true
            if (next < smallest) smallest = next
            queue.addLast(next)
        }
    }
    return Region(seen, smallest)
}

private fun keyOf(
    boxes: IntArray,
    smallest: Int,
): String = boxes.joinToString(",") + "|" + smallest

private fun allHome(
    boxes: IntArray,
    level: Level,
): Boolean = boxes.all { level.isGoal(it) }

/** One position of the push search: where the crates are, and where the keeper may stand. */
private class Node(
    val boxes: IntArray,
    val reachable: BooleanArray,
)

/**
 * What one round of the search is writing into: the positions already seen, the
 * frontier being built, and the budget.
 *
 * One object rather than three arguments threaded through every helper, because
 * they are only ever passed together and only ever mean one thing.
 */
private class Frontier(
    val seen: HashSet<String>,
    val next: MutableList<Node>,
    /** Called once per newly seen position; true when the budget has just run out. */
    val count: () -> Boolean,
)

/**
 * The fewest pushes the level can be finished in, or how the search gave up.
 *
 * Breadth-first over pushes, so the first solution found is the shortest one.
 */
fun solve(
    level: Level,
    budget: Int = DEFAULT_BUDGET,
): SolveResult {
    val limit = if (budget > 0) budget else DEFAULT_BUDGET
    val start = level.boxes.sortedArray()
    if (allHome(start, level)) return SolveResult(SolveStatus.SOLVED, 0, 0)

    val blocked = BooleanArray(level.cells).also { flags -> start.forEach { flags[it] = true } }
    val first = reach(level, blocked, level.player)
    val seen = HashSet<String>()
    seen.add(keyOf(start, first.smallest))

    var frontier = listOf(Node(start, first.seen))
    var pushes = 0
    var states = 1

    while (frontier.isNotEmpty()) {
        val next = mutableListOf<Node>()
        pushes += 1

        val round =
            Frontier(seen, next) {
                states += 1
                states > limit
            }
        for (node in frontier) {
            val outcome = expand(level, node, round)
            if (outcome == Expansion.SOLVED) return SolveResult(SolveStatus.SOLVED, pushes, states)
            if (outcome == Expansion.EXHAUSTED) return SolveResult(SolveStatus.EXHAUSTED, -1, states)
        }
        frontier = next
    }
    return SolveResult(SolveStatus.UNSOLVABLE, -1, states)
}

/** How expanding one position ended. */
private enum class Expansion { CONTINUE, SOLVED, EXHAUSTED }

/**
 * Every push available from one position, pushed onto the next frontier.
 *
 * [count] is called once per newly seen state and says whether the budget has just
 * run out, which keeps the accounting in one place rather than threaded through
 * three nested loops.
 */
private fun expand(
    level: Level,
    node: Node,
    frontier: Frontier,
): Expansion {
    val occupied = BooleanArray(level.cells).also { flags -> node.boxes.forEach { flags[it] = true } }

    for (slot in node.boxes.indices) {
        val outcome = expandCrate(level, node, occupied, slot, frontier)
        if (outcome != Expansion.CONTINUE) return outcome
    }
    return Expansion.CONTINUE
}

/** The four ways one crate could be pushed from here. */
private fun expandCrate(
    level: Level,
    node: Node,
    occupied: BooleanArray,
    slot: Int,
    frontier: Frontier,
): Expansion {
    for (direction in Direction.entries) {
        val landing = pushTarget(level, node, occupied, node.boxes[slot], direction)
        if (landing >= 0) {
            val outcome = visit(level, node, slot, landing, frontier)
            if (outcome != Expansion.CONTINUE) return outcome
        }
    }
    return Expansion.CONTINUE
}

/**
 * Where pushing this crate in this direction would land it, or -1 when the push is
 * not available.
 *
 * To push a crate the keeper must be able to reach the cell behind it, and the cell
 * in front of it must be free - and a crate shoved into a corner off-goal is dead,
 * so that push is not worth exploring at all.
 */
private fun pushTarget(
    level: Level,
    node: Node,
    occupied: BooleanArray,
    box: Int,
    direction: Direction,
): Int {
    val standing = level.step(box, direction.opposite)
    val landing = level.step(box, direction)
    if (standing < 0 || landing < 0) return -1

    val canStand = !level.isWall(standing) && !occupied[standing] && node.reachable[standing]
    val canLand = !level.isWall(landing) && !occupied[landing] && !isCornerDeadlock(level, landing)
    return if (canStand && canLand) landing else -1
}

/** Take one push, and say whether that ended the search. */
private fun visit(
    level: Level,
    node: Node,
    slot: Int,
    landing: Int,
    frontier: Frontier,
): Expansion {
    val moved =
        node.boxes
            .copyOf()
            .also { it[slot] = landing }
            .also { it.sort() }
    val blocked = BooleanArray(level.cells).also { flags -> moved.forEach { flags[it] = true } }
    val region = reach(level, blocked, node.boxes[slot])
    if (!frontier.seen.add(keyOf(moved, region.smallest))) return Expansion.CONTINUE

    val overBudget = frontier.count()
    if (allHome(moved, level)) return Expansion.SOLVED
    if (overBudget) return Expansion.EXHAUSTED
    frontier.next.add(Node(moved, region.seen))
    return Expansion.CONTINUE
}
