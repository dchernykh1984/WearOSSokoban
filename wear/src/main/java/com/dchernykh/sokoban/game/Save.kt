package com.dchernykh.sokoban.game

// The game in progress, written to watch storage and read back.
//
// A big warehouse is not finished in one sitting, so closing the app must not throw
// the position away. What is saved is the WHOLE position, not a pointer into the
// shipped collection: a level number would go stale the moment the collection
// changed in a later version, and the player would come back to find themselves
// standing in a wall with the crates outside the map.
//
// The starting positions are kept alongside the current ones, because Restart has
// to work after a reload and the undo history is capped - it cannot always be
// rewound all the way to the beginning.
//
// Stored as hex, two characters a byte, exactly as the Zepp OS app stored it: a
// save written on an Amazfit watch describes the same position here.

const val SAVE_VERSION = 1

/**
 * How much of the undo history is worth keeping across a restart. Undo is for
 * taking back a mistake you just made; anything older is what Restart is for, and a
 * few hundred moves is already more than anybody rewinds.
 */
const val HISTORY_LIMIT = 800

private const val HEX = "0123456789abcdef"

/** A saved game, and which size and source it was being played on. */
class SavedGame(
    val size: Size,
    val source: Source,
    val game: Game,
)

private fun StringBuilder.appendByte(value: Int) {
    val byte = value and 0xFF
    append(HEX[byte shr 4]).append(HEX[byte and 15])
}

private fun StringBuilder.appendCell(
    index: Int,
    cols: Int,
) {
    appendByte(index % cols)
    appendByte(index / cols)
}

private fun StringBuilder.appendUint16(value: Int) {
    appendByte((value shr 8) and 0xFF)
    appendByte(value and 0xFF)
}

/** Everything needed to carry on: the size, the source, the warehouse and how it got here. */
fun encodeSave(
    size: Size,
    source: Source,
    game: Game,
): String {
    val level = game.level
    val cols = level.cols
    val boxes = game.boxes.size
    val text = StringBuilder()

    text.appendByte(SAVE_VERSION)
    text.appendByte(size.ordinal)
    text.appendByte(source.ordinal)
    text.appendByte(cols)
    text.appendByte(level.rows)
    text.appendByte(boxes)

    val mask = IntArray((level.cells + 7) / 8)
    for (index in 0 until level.cells) {
        if (level.isWall(index)) mask[index shr 3] = mask[index shr 3] or (1 shl (index and 7))
    }
    mask.forEach { text.appendByte(it) }

    for (i in 0 until boxes) text.appendCell(level.goals[i], cols)
    for (i in 0 until boxes) text.appendCell(level.boxes[i], cols)
    text.appendCell(level.player, cols)
    for (i in 0 until boxes) text.appendCell(game.boxes[i], cols)
    text.appendCell(game.player, cols)

    text.appendUint16(game.moves)
    text.appendUint16(game.pushes)

    // Only the tail of the history is kept, newest last, so undo still works for
    // the moves that are worth taking back.
    val history = game.history.takeLast(HISTORY_LIMIT)
    text.appendUint16(history.size)
    for (step in history) text.appendByte(step.direction.ordinal or if (step.pushed) 4 else 0)

    return text.toString()
}

/** A hex string back into bytes, or null when it is not one. */
private fun fromHex(text: String?): IntArray? {
    if (text == null || text.length < 2 || text.length % 2 != 0) return null
    val bytes = IntArray(text.length / 2)
    for (i in bytes.indices) {
        val high = HEX.indexOf(text[i * 2])
        val low = HEX.indexOf(text[i * 2 + 1])
        if (high < 0 || low < 0) return null
        bytes[i] = (high shl 4) or low
    }
    return bytes
}

/** A cursor over the saved bytes, so reading one is a call rather than index arithmetic. */
private class Reader(
    val bytes: IntArray,
) {
    var at = 0

    fun byte(): Int = bytes[at++]

    fun uint16(): Int {
        val value = (bytes[at] shl 8) or bytes[at + 1]
        at += 2
        return value
    }

    fun cell(cols: Int): Int {
        val col = bytes[at]
        val row = bytes[at + 1]
        at += 2
        return row * cols + col
    }

    fun has(count: Int): Boolean = at + count <= bytes.size
}

/** The header of a save: the sizes everything after it is measured in. */
private class Header(
    val size: Size,
    val source: Source,
    val cols: Int,
    val rows: Int,
    val boxes: Int,
)

private fun readHeader(reader: Reader): Header? {
    val version = reader.byte()
    val size = Size.entries.getOrNull(reader.byte())
    val source = Source.entries.getOrNull(reader.byte())
    val cols = reader.byte()
    val rows = reader.byte()
    val boxes = reader.byte()

    // A version from the future, a size or a source this build has never heard of,
    // or a warehouse too small to be one: all of it reads as no save at all.
    val sound = version == SAVE_VERSION && size != null && source != null && cols >= 3 && rows >= 3 && boxes >= 1
    return if (sound) Header(size!!, source!!, cols, rows, boxes) else null
}

private fun readHistory(
    reader: Reader,
    count: Int,
): List<Step>? {
    if (count > HISTORY_LIMIT || !reader.has(count)) return null
    return List(count) {
        val packed = reader.byte()
        Step(Direction.entries[packed and 3], pushed = packed and 4 != 0)
    }
}

/**
 * The saved game, or null when there is nothing usable stored.
 *
 * Anything that does not add up - a version from the future, a truncated string, a
 * warehouse whose numbers contradict each other - reads as "no save" rather than as
 * a broken game, because a wrong board is worse than a fresh one.
 */
@Suppress("ReturnCount")
fun decodeSave(text: String?): SavedGame? {
    val bytes = fromHex(text) ?: return null
    if (bytes.size < 6) return null
    val reader = Reader(bytes)
    val header = readHeader(reader) ?: return null

    val cells = header.cols * header.rows
    val maskBytes = (cells + 7) / 8
    if (!reader.has(maskBytes + header.boxes * 6 + 4 + 4 + 2)) return null

    val walls = BooleanArray(cells)
    val maskAt = reader.at
    for (index in 0 until cells) {
        if (bytes[maskAt + (index shr 3)] and (1 shl (index and 7)) != 0) walls[index] = true
    }
    reader.at += maskBytes

    val goals = IntArray(header.boxes) { reader.cell(header.cols) }
    val startBoxes = IntArray(header.boxes) { reader.cell(header.cols) }
    val startPlayer = reader.cell(header.cols)
    val boxes = IntArray(header.boxes) { reader.cell(header.cols) }
    val player = reader.cell(header.cols)
    val moves = reader.uint16()
    val pushes = reader.uint16()
    val history = readHistory(reader, reader.uint16()) ?: return null

    val sane = { index: Int -> index in 0 until cells }
    if (!sane(player) || !sane(startPlayer)) return null
    if (!goals.all(sane) || !startBoxes.all(sane) || !boxes.all(sane)) return null

    val level = Level(header.cols, header.rows, walls, goals, startBoxes, startPlayer)
    return SavedGame(
        size = header.size,
        source = header.source,
        game = Game(level, boxes, player, moves, pushes, history),
    )
}
