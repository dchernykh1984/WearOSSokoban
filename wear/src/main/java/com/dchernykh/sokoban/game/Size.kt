package com.dchernykh.sokoban.game

/**
 * The six warehouse sizes.
 *
 * Each one is a spec for the generator plus the size of the window the watch shows
 * it through. [cols] x [rows] counts the solid border, so a 9x9 warehouse has a
 * 7x7 floor. [visible] is how many cells fit across the round screen at once: XS
 * and S are shown whole, and from M upwards the warehouse is deliberately bigger
 * than the window, so the map has to be dragged around to see the rest of it.
 *
 * [blocks] rises much faster than the area on purpose. A big open hall is a dull
 * Sokoban and an easy one - a crate can be pushed anywhere - so the bigger sizes
 * are made deliberately tight, all rooms and corridors.
 *
 * The name is the storage key, so a size must never be renamed: a best score and a
 * played-level record are kept under it.
 */
enum class Size(
    val label: String,
    val cols: Int,
    val rows: Int,
    val boxes: Int,
    val blocks: Int,
    val pulls: Int,
    val minPulls: Int,
    val visible: Int,
) {
    XS("XS", cols = 9, rows = 9, boxes = 2, blocks = 9, pulls = 18, minPulls = 10, visible = 9),
    S("S", cols = 11, rows = 11, boxes = 3, blocks = 15, pulls = 27, minPulls = 15, visible = 11),
    M("M", cols = 13, rows = 13, boxes = 4, blocks = 22, pulls = 36, minPulls = 20, visible = 11),
    L("L", cols = 15, rows = 15, boxes = 5, blocks = 30, pulls = 45, minPulls = 25, visible = 11),
    XL("XL", cols = 17, rows = 17, boxes = 6, blocks = 41, pulls = 54, minPulls = 30, visible = 11),
    XXL("XXL", cols = 19, rows = 19, boxes = 7, blocks = 52, pulls = 63, minPulls = 35, visible = 11),
    ;

    /** The file in assets that holds this size's collection. */
    val assetName: String get() = "levels/${name.lowercase()}.sok"

    val next: Size get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The smallest warehouse fits the round screen whole, which is where to start. */
        val DEFAULT = XS

        fun fromStoredName(name: String?): Size = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Where a warehouse comes from.
 *
 * The app ships four thousand levels generated on a computer and put through a
 * real solver, which is the only way to know a puzzle is not trivially easy. It can also
 * build one on the wrist, which nobody needs for quality - but a warehouse nobody
 * has ever seen is worth something on its own, so the choice stays with the player.
 *
 * The two are kept apart for scoring: a vetted level and one the watch happened to
 * roll are not the same challenge, and one best score covering both would mean
 * nothing.
 */
enum class Source {
    /** Built-in first: it is the better experience, so a fresh install gets it. */
    BUILT_IN,
    GENERATED,
    ;

    val next: Source get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = BUILT_IN

        fun fromStoredName(name: String?): Source = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
