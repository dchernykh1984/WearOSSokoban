package com.dchernykh.sokoban.store

import android.content.Context
import com.dchernykh.sokoban.game.Level
import com.dchernykh.sokoban.game.Size
import com.dchernykh.sokoban.game.parseLevel
import com.dchernykh.sokoban.game.splitLevels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The shipped collection, read from the files the Zepp OS app shipped.
 *
 * An interface, so a JVM test can hand the view model a couple of warehouses
 * instead of an emulator and 680KB of assets.
 */
interface LevelSource {
    /** How many warehouses this size holds. */
    suspend fun count(size: Size): Int

    /** One warehouse, or null when the index is past the end of the collection. */
    suspend fun levelAt(
        size: Size,
        index: Int,
    ): Level?
}

/**
 * The real one, on top of the APK's assets.
 *
 * A whole size is read and split once and then kept, because the alternative is
 * re-reading up to 180KB of text every time a level is dealt. Only the size being
 * played is ever held, and only as the block of text each warehouse is written as -
 * parsing happens for the one that is dealt and no other.
 *
 * The read is real file I/O and the split walks the whole file, so both happen off
 * the main thread: a level dealt on it is a screen that stops drawing while the
 * collection loads. The cache is written there too, which is safe because the view
 * model serialises every call to this behind one job chain - two sizes are never
 * being loaded at once.
 */
class AssetLevelSource(
    context: Context,
) : LevelSource {
    private val assets = context.applicationContext.assets
    private var cachedSize: Size? = null
    private var cachedBlocks: List<String> = emptyList()

    private suspend fun blocksFor(size: Size): List<String> {
        if (cachedSize == size) return cachedBlocks
        cachedBlocks =
            withContext(Dispatchers.IO) {
                splitLevels(assets.open(size.assetName).bufferedReader().use { it.readText() })
            }
        cachedSize = size
        return cachedBlocks
    }

    override suspend fun count(size: Size): Int = blocksFor(size).size

    override suspend fun levelAt(
        size: Size,
        index: Int,
    ): Level? =
        blocksFor(size).getOrNull(index)?.let { block ->
            withContext(Dispatchers.Default) { parseLevel(block) }
        }
}
