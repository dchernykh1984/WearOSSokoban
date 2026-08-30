package com.dchernykh.sokoban.game

// Random warehouses that are ALWAYS solvable, built by reverse play.
//
// Scattering crates at random and hoping for the best does not work: most random
// Sokoban positions are dead on arrival, because a crate pushed into a corner can
// never come out. So this generator never builds a puzzle - it builds a SOLVED
// warehouse, every crate already on its goal, and then walks the game backwards,
// PULLING crates off their goals. A pull is the exact inverse of a push, so the
// scrambled position it lands on is reachable from the solved one; replaying the
// pulls in reverse is, by construction, a solution.
//
// That solution is handed back with the level, as a full list of keeper moves.
// Nothing on the watch uses it - it is the certificate the unit tests replay
// through the real rule set to prove that what the generator emits can be
// finished.

/** How many independent tries a size gets before the best so far is accepted. */
const val MAX_ATTEMPTS = 24

/**
 * How often the scramble keeps working on the crate it just pulled.
 *
 * Wandering between crates on every step spreads them evenly and makes flat, dull
 * puzzles; dragging one crate a long way and then moving on is what creates the
 * tangles that have to be unpicked in a particular order.
 */
private const val SAME_BOX_BIAS = 0.6

/**
 * How much of the floor a puzzle has to actually use.
 *
 * Goals picked at random cluster, pulls are local, and the result is a warehouse
 * where the whole game happens in one corner while the rest of the map is scenery.
 * A level that never sends the keeper across this much of its own floor is thrown
 * away.
 */
const val MIN_COVERAGE = 0.45f

/** A generated warehouse, with the solution it was built backwards from. */
class Generated(
    val level: Level,
    val solution: List<Direction>,
    val coverage: Float,
)

/** One pull: which crate, where it came from, where it went, and where the keeper ended up. */
private class Pull(
    val slot: Int,
    val from: Int,
    val to: Int,
    val playerTo: Int,
)

/**
 * Every pull available right now.
 *
 * Pulling a crate one cell means the keeper stands on the cell the crate is about
 * to occupy and walks one further, dragging the crate after it - so both cells
 * must be free and the keeper must be able to walk to the first in the first
 * place.
 */
private fun legalPulls(
    level: Level,
    boxes: IntArray,
    player: Int,
): List<Pull> {
    val occupied = BooleanArray(level.cells).also { flags -> boxes.forEach { flags[it] = true } }
    val walkable = walkableFrom(level, occupied, player)

    val pulls = mutableListOf<Pull>()
    for (slot in boxes.indices) {
        for (direction in Direction.entries) {
            pullAt(level, occupied, walkable, boxes[slot], slot, direction)?.let(pulls::add)
        }
    }
    return pulls
}

/**
 * The pull of one crate in one direction, or null when it is not available.
 *
 * The keeper stands on the cell the crate is about to occupy and walks one further,
 * dragging the crate after it - so both cells must be free, and the keeper must be
 * able to walk to the first of them in the first place.
 */
private fun pullAt(
    level: Level,
    occupied: BooleanArray,
    walkable: BooleanArray,
    box: Int,
    slot: Int,
    direction: Direction,
): Pull? {
    val landing = level.step(box, direction)
    if (landing < 0 || !isClear(level, occupied, landing) || !walkable[landing]) return null
    val standing = level.step(landing, direction)
    if (standing < 0 || !isClear(level, occupied, standing)) return null
    return Pull(slot, box, landing, standing)
}

/** Whether a cell is floor with nothing standing on it. */
private fun isClear(
    level: Level,
    occupied: BooleanArray,
    index: Int,
): Boolean = !level.isWall(index) && !occupied[index]

/** Where a scramble ended up, and the pulls that got it there. */
private class Scrambled(
    val boxes: IntArray,
    val player: Int,
    val pulls: List<Pull>,
)

/**
 * The next pull of a scramble, or null when there is nothing left to pull.
 *
 * The pull just made is never simply undone - that would only walk the crate back
 * onto its goal - and the crate just moved is favoured, because wandering between
 * crates on every step spreads them evenly and makes flat, dull puzzles.
 */
private fun choosePull(
    level: Level,
    boxes: IntArray,
    player: Int,
    last: Pull?,
    random: Mulberry32,
): Pull? {
    val options = legalPulls(level, boxes, player)
    val fresh = options.filter { last == null || it.slot != last.slot || it.to != last.from }
    val usable = fresh.ifEmpty { options }
    if (usable.isEmpty()) return null

    val sameBox = if (last == null) emptyList() else usable.filter { it.slot == last.slot }
    return if (sameBox.isNotEmpty() && random.next() < SAME_BOX_BIAS) {
        random.pick(sameBox)
    } else {
        random.pick(usable)
    }
}

/** Drag the crates off their goals with a run of random pulls. */
private fun scramble(
    level: Level,
    goals: IntArray,
    player: Int,
    steps: Int,
    random: Mulberry32,
): Scrambled {
    val current = goals.copyOf()
    var at = player
    val made = mutableListOf<Pull>()

    // One exit: the scramble runs until a step has nothing to pull, which is the
    // only way it can stop early.
    var pulling = true
    for (step in 0 until steps) {
        if (!pulling) break
        val chosen = choosePull(level, current, at, made.lastOrNull(), random)
        pulling = chosen != null
        if (chosen != null) {
            current[chosen.slot] = chosen.to
            at = chosen.playerTo
            made.add(chosen)
        }
    }
    return Scrambled(current, at, made)
}

/**
 * Replay the pulls backwards as pushes, walking the keeper between them, to get
 * the move list that finishes the puzzle. Null when a walk is impossible, which
 * would mean the scramble broke its own invariant.
 */
private fun buildSolution(
    level: Level,
    boxes: IntArray,
    player: Int,
    pulls: List<Pull>,
): List<Direction>? {
    val current = boxes.copyOf()
    val moves = mutableListOf<Direction>()
    var at = player

    for (pull in pulls.reversed()) {
        val walk = findPath(level, current, at, pull.playerTo) ?: return null
        moves.addAll(walk)
        val direction = directionBetween(level, pull.to, pull.from) ?: return null
        val slot = current.indexOf(pull.to)
        if (slot < 0) return null
        current[slot] = pull.from
        at = pull.to
        moves.add(direction)
    }
    return moves
}

/** One try at a warehouse, with everything the chooser compares. */
private class Attempt(
    val generated: Generated,
    val displaced: Int,
    val pulls: Int,
)

/**
 * How good a try is, as one number: crates off their goals first, then how much of
 * the floor the solution uses, then how far the crates were dragged.
 */
private fun score(attempt: Attempt): Float =
    attempt.displaced * 1000f + attempt.generated.coverage * 100f + attempt.pulls

private fun attempt(
    size: Size,
    random: Mulberry32,
): Attempt? {
    val walls = carveWalls(size.cols, size.rows, size.blocks, random)
    val room = Level(size.cols, size.rows, walls, IntArray(0), IntArray(0), 0)

    val candidates = pullableCells(room)
    val goals =
        if (candidates.size > size.boxes) spreadGoals(room, candidates, size.boxes, random) else null
    if (goals == null) return null

    // The keeper starts anywhere that is not already under a crate; the scramble
    // moves it around from there.
    val free = room.floorCells().filter { it !in goals.toList() }
    val start = random.pick(free) ?: return null
    val scrambled = scramble(room, goals, start, size.pulls, random)

    // Put the keeper on a random cell it could have walked to anyway. Leaving it
    // where the last pull dropped it would point straight at the first push.
    val occupied = BooleanArray(room.cells).also { flags -> scrambled.boxes.forEach { flags[it] = true } }
    val spots = walkableFrom(room, occupied, scrambled.player).withIndex().filter { it.value }.map { it.index }
    val player = random.pick(spots) ?: scrambled.player

    val solution = buildSolution(room, scrambled.boxes, player, scrambled.pulls) ?: return null
    val level =
        Level(
            cols = size.cols,
            rows = size.rows,
            walls = walls,
            goals = goals.sortedArray(),
            boxes = scrambled.boxes,
            player = player,
        )
    return Attempt(
        generated = Generated(level, solution, coverage(level, solution)),
        displaced = scrambled.boxes.count { !level.isGoal(it) },
        pulls = scrambled.pulls.size,
    )
}

/**
 * A random warehouse of this size, or null when nothing playable came out.
 *
 * The loop keeps the best try even when none of them clear every bar, so a cramped
 * size still gets a playable warehouse rather than an exception. What it must never
 * hand back is a warehouse with every crate already home: that is not a hard
 * puzzle, it is a finished one.
 */
fun generateLevel(
    size: Size,
    seed: Int,
): Generated? {
    val random = Mulberry32(seed)
    var best: Attempt? = null

    repeat(MAX_ATTEMPTS) {
        val candidate = attempt(size, random)
        if (candidate != null) {
            if (best == null || score(candidate) > score(best)) best = candidate
            if (clearsTheBar(candidate, size)) return candidate.generated
        }
    }
    return best?.takeIf { it.displaced > 0 }?.generated
}

/**
 * Whether a try is good enough to stop looking.
 *
 * A puzzle that opens with a crate already parked on a goal gives away part of the
 * answer, one scrambled only a step or two is not a puzzle at all, and one that only
 * ever uses a corner of its own floor is a big map pretending to be a big puzzle.
 * All three have to be right; otherwise the loop keeps looking and settles for the
 * best it saw.
 */
private fun clearsTheBar(
    candidate: Attempt,
    size: Size,
): Boolean =
    candidate.displaced == size.boxes &&
        candidate.pulls >= size.minPulls &&
        candidate.generated.coverage >= MIN_COVERAGE
