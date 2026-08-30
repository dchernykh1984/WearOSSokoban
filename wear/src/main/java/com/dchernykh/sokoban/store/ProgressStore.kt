package com.dchernykh.sokoban.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dchernykh.sokoban.game.Size
import com.dchernykh.sokoban.game.Source
import com.dchernykh.sokoban.game.normalizeMoves
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * What survives closing the app: the size and source last played, the best result
 * for each pair of them, which warehouses of each collection have been dealt, and
 * the game left half-finished.
 *
 * An interface, because everything interesting happens above it: a JVM test drives
 * the view model against an in-memory implementation instead of an emulator.
 */
interface ProgressStore {
    suspend fun readSize(): Size

    suspend fun writeSize(size: Size)

    suspend fun readSource(): Source

    suspend fun writeSource(source: Source)

    suspend fun readBest(
        size: Size,
        source: Source,
    ): Int

    suspend fun writeBest(
        size: Size,
        source: Source,
        moves: Int,
    )

    /** The played-level record for a size, as the hex string the collection encodes. */
    suspend fun readPlayed(size: Size): String?

    suspend fun writePlayed(
        size: Size,
        played: String,
    )

    /** The game in progress, as the hex string the save encodes, or null. */
    suspend fun readSave(): String?

    suspend fun writeSave(save: String?)
}

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

private val SIZE_KEY = stringPreferencesKey("size")
private val SOURCE_KEY = stringPreferencesKey("source")
private val SAVE_KEY = stringPreferencesKey("game")

private fun bestKey(
    size: Size,
    source: Source,
) = intPreferencesKey("best_${source.name}_${size.name}")

private fun playedKey(size: Size) = stringPreferencesKey("played_${size.name}")

/**
 * The real store, on top of Preferences DataStore.
 *
 * Storage that has gone wrong must not stop anyone playing: a failed read reads as
 * nothing stored and a failed write is dropped, so a corrupt preferences file costs
 * a record rather than the app.
 */
class DataStoreProgressStore(
    context: Context,
) : ProgressStore {
    // The application context, not the activity's: a DataStore outlives any one
    // screen, and holding the activity here would leak it for the life of the app.
    private val dataStore = context.applicationContext.progressDataStore

    private suspend fun read(): Preferences =
        dataStore.data
            .catch { cause ->
                // Only I/O. Anything else is a bug in this file rather than a broken
                // disk, and swallowing it would hide it.
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }.first()

    private suspend fun write(change: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(change)
        } catch (_: IOException) {
            // Nothing to do and nothing worth saying: the game carries on.
        }
    }

    override suspend fun readSize(): Size = Size.fromStoredName(read()[SIZE_KEY])

    override suspend fun writeSize(size: Size) = write { it[SIZE_KEY] = size.name }

    override suspend fun readSource(): Source = Source.fromStoredName(read()[SOURCE_KEY])

    override suspend fun writeSource(source: Source) = write { it[SOURCE_KEY] = source.name }

    override suspend fun readBest(
        size: Size,
        source: Source,
    ): Int = normalizeMoves(read()[bestKey(size, source)])

    override suspend fun writeBest(
        size: Size,
        source: Source,
        moves: Int,
    ) = write { it[bestKey(size, source)] = normalizeMoves(moves) }

    override suspend fun readPlayed(size: Size): String? = read()[playedKey(size)]

    override suspend fun writePlayed(
        size: Size,
        played: String,
    ) = write { it[playedKey(size)] = played }

    override suspend fun readSave(): String? = read()[SAVE_KEY]

    override suspend fun writeSave(save: String?) =
        write { preferences ->
            if (save == null) preferences.remove(SAVE_KEY) else preferences[SAVE_KEY] = save
        }
}
