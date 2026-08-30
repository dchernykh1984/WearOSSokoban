package com.dchernykh.sokoban.game

// Which warehouses of the built-in collection have already been finished.
//
// Handing out levels at random means playing the same one twice long before seeing
// half of them - the birthday problem bites hard - so every level dealt is marked
// and the picker only ever offers one that has not been played. When the last one
// falls, the slate is wiped and the collection starts again.
//
// One bit per level: a thousand levels cost 125 bytes, all six sizes together well
// under a kilobyte, which is nothing to keep in watch storage. Stored as hex, two
// characters a byte, exactly as the Zepp OS app stored it.

private const val HEX = "0123456789abcdef"

/** What a deal produced: the level, the record it came from, and whether the slate was wiped. */
data class Deal(
    val index: Int,
    val played: BooleanArray,
    val wrapped: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Deal && index == other.index && wrapped == other.wrapped && played.contentEquals(other.played))

    override fun hashCode(): Int = 31 * (31 * index + wrapped.hashCode()) + played.contentHashCode()
}

fun emptyPlayed(count: Int): BooleanArray = BooleanArray(maxOf(0, count))

fun playedCount(played: BooleanArray): Int = played.count { it }

fun allPlayed(played: BooleanArray): Boolean = played.isNotEmpty() && playedCount(played) == played.size

/** Mark a level as played, as a new array so the caller can keep the old one. */
fun markPlayed(
    played: BooleanArray,
    index: Int,
): BooleanArray = played.copyOf().also { if (index in it.indices) it[index] = true }

fun encodePlayed(played: BooleanArray): String {
    val text = StringBuilder()
    var start = 0
    while (start < played.size) {
        var byte = 0
        for (bit in 0 until 8) {
            if (played.getOrElse(start + bit) { false }) byte = byte or (1 shl bit)
        }
        text.append(HEX[byte shr 4]).append(HEX[byte and 15])
        start += 8
    }
    return text.toString()
}

/**
 * A stored hex string back into one flag per level.
 *
 * The count comes from the collection, not from the string: a collection that grew
 * between versions must not be read as if it were still the old size. Anything
 * unreadable reads as "nothing played yet", because losing the history is a far
 * smaller harm than refusing to deal.
 */
fun decodePlayed(
    text: String?,
    count: Int,
): BooleanArray {
    val played = emptyPlayed(count)
    if (text == null) return played

    for (index in played.indices) {
        val byte = byteAt(text, index shr 3) ?: break
        played[index] = byte and (1 shl (index and 7)) != 0
    }
    return played
}

/** One byte of a hex string, or null when it is not there or is not hex. */
private fun byteAt(
    text: String,
    at: Int,
): Int? {
    if (at * 2 + 1 >= text.length) return null
    val high = HEX.indexOf(text[at * 2])
    val low = HEX.indexOf(text[at * 2 + 1])
    if (high < 0 || low < 0) return null
    return (high shl 4) or low
}

/**
 * Deal the next warehouse.
 *
 * Rolls a random starting point and walks forward to the first level that has not
 * been played, which spreads the choice over the whole collection rather than
 * favouring the front of it. When every level has been played the slate is wiped
 * and the deal starts again - and the level just finished is skipped in that fresh
 * round, so the reward for completing a collection is never the very same
 * warehouse again.
 *
 * [Deal.index] is -1 only when there is nothing to deal at all.
 */
fun dealLevel(
    played: BooleanArray,
    random: Mulberry32,
    avoid: Int,
): Deal {
    val count = played.size
    if (count == 0) return Deal(-1, played, wrapped = false)

    var pool = played
    var wrapped = false
    if (allPlayed(pool)) {
        pool = emptyPlayed(count)
        wrapped = true
    }

    val skip = if (wrapped && count > 1) avoid else -1
    val start = random.nextInt(count)

    for (step in 0 until count) {
        val index = (start + step) % count
        if (!pool[index] && index != skip) return Deal(index, pool, wrapped)
    }

    // Only reachable when the one level left is the one we were told to skip.
    for (index in 0 until count) {
        if (!pool[index]) return Deal(index, pool, wrapped)
    }
    return Deal(-1, pool, wrapped)
}
