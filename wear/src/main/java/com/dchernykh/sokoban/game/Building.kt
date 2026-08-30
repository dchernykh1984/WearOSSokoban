package com.dchernykh.sokoban.game

// Building a warehouse a round at a time, so the watch can show progress.
//
// One attempt at the largest size is a visible pause even on a phone-class CPU, and
// a run of them with nothing on screen looks like the app has stopped. So the work
// is handed out in rounds: the caller asks for one, is told how far along it is,
// draws a bar, and comes round again. A level is accepted as soon as one clears the
// bar; otherwise the best round so far is kept and the machine tries again.

/** How many rounds a size gets before the best one so far is accepted. */
const val MAX_ROUNDS = 24

/**
 * The least of the floor a warehouse has to put to use before a round is accepted
 * outright. Below it the round is kept as a fallback but the machine keeps looking.
 */
private const val GOOD_ENOUGH_COVERAGE = 0.45f

/** A run of build rounds: what it has tried, and the best it has seen. */
class Build(
    val size: Size,
    val seed: Int,
    val rounds: Int = MAX_ROUNDS,
    val round: Int = 0,
    val best: Generated? = null,
    val done: Boolean = false,
) {
    /** How far along, from 0 to 1. Shown as a bar, so it moves on every round. */
    val progress: Float get() = if (done) 1f else minOf(1f, round / rounds.toFloat())

    /** The warehouse the run produced, or null when nothing playable came out. */
    val result: Generated? get() = if (done) best else null
}

/**
 * A warehouse worth stopping for: every crate off its goal, and enough of the floor
 * actually in play.
 */
private fun isGoodEnough(
    generated: Generated,
    size: Size,
): Boolean {
    if (generated.coverage < GOOD_ENOUGH_COVERAGE) return false
    if (generated.level.boxes.any { generated.level.isGoal(it) }) return false
    return generated.solution.isNotEmpty() && size.boxes > 0
}

/** How good a round is, used only to keep the better of two that both fell short. */
private fun quality(generated: Generated): Float {
    val displaced = generated.level.boxes.count { !generated.level.isGoal(it) }
    return displaced * 1000f + generated.coverage * 100f
}

/**
 * Do one round and report back.
 *
 * Each round is a whole [generateLevel] from its own seed, derived from the run's,
 * so a run is reproducible from the seed it started with and no two rounds ever
 * build the same warehouse.
 */
fun stepBuild(build: Build): Build {
    if (build.done) return build

    val generated = generateLevel(build.size, build.seed + build.round * 0x9E3779B1.toInt())
    val accepted = generated != null && isGoodEnough(generated, build.size)
    val round = build.round + 1

    return Build(
        size = build.size,
        seed = build.seed,
        rounds = build.rounds,
        round = round,
        best = keep(build.best, generated, accepted),
        // Stop the moment a round clears the bar; otherwise keep going until the
        // rounds run out and settle for the best that was seen.
        done = accepted || round >= build.rounds,
    )
}

/** Which of the two warehouses the run carries forward. */
private fun keep(
    best: Generated?,
    generated: Generated?,
    accepted: Boolean,
): Generated? =
    when {
        accepted -> generated
        generated == null -> best
        best == null || quality(generated) > quality(best) -> generated
        else -> best
    }
