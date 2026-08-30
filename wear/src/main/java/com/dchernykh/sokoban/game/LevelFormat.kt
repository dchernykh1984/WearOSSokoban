package com.dchernykh.sokoban.game

// The text a warehouse is written down in.
//
// One character per cell, the same alphabet every Sokoban collection uses, with
// one change carried over from the Zepp OS app: floor is a DASH, not a space. The
// repository's trailing-whitespace hook would quietly eat the right-hand edge of
// every picture and corrupt the collection without anyone noticing.
//
//   #  wall      -  floor     .  goal
//   $  crate     *  crate on a goal
//   @  keeper    +  keeper on a goal
//
// A collection is levels separated by blank lines; lines starting with ';' are
// comments. The six files in assets/levels are the Zepp OS app's own, byte for
// byte, so a warehouse played on an Amazfit watch is the same warehouse here.

const val WALL = '#'
const val FLOOR = '-'
const val GOAL = '.'
const val BOX = '$'
const val BOX_ON_GOAL = '*'
const val KEEPER = '@'
const val KEEPER_ON_GOAL = '+'
const val COMMENT = ';'

private val GLYPHS = charArrayOf(WALL, FLOOR, GOAL, BOX, BOX_ON_GOAL, KEEPER, KEEPER_ON_GOAL)

/**
 * One warehouse from its picture, or null when the text is not one.
 *
 * Returning null rather than throwing keeps a corrupt block in a data file from
 * taking the whole collection down with it; the caller decides what to do. The
 * original threw, which suits a build script that must fail loudly - but on a
 * watch the only useful answer is to deal another warehouse.
 */
fun parseLevel(text: String): Level? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty() || lines[0].isEmpty()) return null

    val cols = lines[0].length
    if (lines.any { it.length != cols }) return null

    val reading = Reading(cols, lines.size)
    for (row in lines.indices) {
        for (col in 0 until cols) {
            if (!reading.read(lines[row][col], row * cols + col)) return null
        }
    }
    return reading.level()
}

/**
 * A warehouse as it is being read, one glyph at a time.
 *
 * The alternative is a `when` inside two loops inside a function that also does the
 * validation, which is exactly the shape that hides a mistake: this way each glyph
 * has one line and each rule that can reject the picture has one place.
 */
private class Reading(
    val cols: Int,
    val rows: Int,
) {
    val walls = BooleanArray(cols * rows)
    val goals = mutableListOf<Int>()
    val boxes = mutableListOf<Int>()
    var player = -1

    /** One glyph, or false when it is not one this format has. */
    fun read(
        glyph: Char,
        index: Int,
    ): Boolean {
        when (glyph) {
            WALL -> walls[index] = true
            FLOOR -> Unit
            GOAL -> goals.add(index)
            BOX -> boxes.add(index)
            BOX_ON_GOAL -> {
                goals.add(index)
                boxes.add(index)
            }
            KEEPER, KEEPER_ON_GOAL -> return keeper(glyph, index)
            else -> return false
        }
        return true
    }

    private fun keeper(
        glyph: Char,
        index: Int,
    ): Boolean {
        // A second keeper is not a warehouse with an extra keeper in it, it is a
        // picture that means two different things.
        if (player != -1) return false
        if (glyph == KEEPER_ON_GOAL) goals.add(index)
        player = index
        return true
    }

    /** The warehouse, or null when the picture does not add up to one. */
    fun level(): Level? {
        if (player == -1 || boxes.size != goals.size) return null
        return Level(
            cols = cols,
            rows = rows,
            walls = walls,
            goals = goals.toIntArray(),
            boxes = boxes.toIntArray(),
            player = player,
        )
    }
}

/** The picture of a warehouse, which is what a round-trip test compares. */
fun formatLevel(level: Level): String =
    (0 until level.rows).joinToString("\n") { row ->
        buildString {
            for (col in 0 until level.cols) {
                val index = level.indexOf(col, row)
                val goal = level.isGoal(index)
                append(
                    when {
                        level.isWall(index) -> WALL
                        level.player == index -> if (goal) KEEPER_ON_GOAL else KEEPER
                        level.boxes.contains(index) -> if (goal) BOX_ON_GOAL else BOX
                        goal -> GOAL
                        else -> FLOOR
                    },
                )
            }
        }
    }

/**
 * A collection file split into the blocks that each hold one warehouse.
 *
 * The files carry a header of comment lines and separate levels with a blank one,
 * which is the shape a person reading them expects and the shape a diff shows.
 */
fun splitLevels(text: String): List<String> {
    val blocks = mutableListOf<String>()
    val block = StringBuilder()

    for (raw in text.lines()) {
        val line = raw.trimEnd('\r')
        when {
            line.startsWith(COMMENT) -> Unit
            line.isEmpty() -> {
                if (block.isNotEmpty()) {
                    blocks.add(block.toString())
                    block.clear()
                }
            }
            else -> {
                if (block.isNotEmpty()) block.append('\n')
                block.append(line)
            }
        }
    }
    if (block.isNotEmpty()) blocks.add(block.toString())
    return blocks
}

/** Every glyph the format uses, for a test that wants to check one is rejected. */
fun isGlyph(character: Char): Boolean = GLYPHS.contains(character)
