package com.dchernykh.sokoban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.dchernykh.sokoban.game.Build
import com.dchernykh.sokoban.game.Direction
import com.dchernykh.sokoban.game.Game
import com.dchernykh.sokoban.game.Level
import com.dchernykh.sokoban.game.Mulberry32
import com.dchernykh.sokoban.game.Size
import com.dchernykh.sokoban.game.Source
import com.dchernykh.sokoban.game.dealLevel
import com.dchernykh.sokoban.game.decodePlayed
import com.dchernykh.sokoban.game.decodeSave
import com.dchernykh.sokoban.game.encodePlayed
import com.dchernykh.sokoban.game.encodeSave
import com.dchernykh.sokoban.game.markPlayed
import com.dchernykh.sokoban.game.stepBuild
import com.dchernykh.sokoban.game.updateBest
import com.dchernykh.sokoban.layout.Camera
import com.dchernykh.sokoban.store.LevelSource
import com.dchernykh.sokoban.store.ProgressStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** Which of the four screens is in front. */
enum class Screen { START, BUILDING, PLAYING, PAUSED, SOLVED }

/** Everything the screen draws. */
data class SokobanUiState(
    val screen: Screen = Screen.START,
    val size: Size = Size.DEFAULT,
    val source: Source = Source.DEFAULT,
    val best: Int = 0,
    /** Whether a half-finished warehouse is waiting to be picked up. */
    val canContinue: Boolean = false,
    val game: Game? = null,
    /** How far the build has got, from 0 to 1, while the Building screen is up. */
    val building: Float = 0f,
    val isRecord: Boolean = false,
    val cameraX: Int = 0,
    val cameraY: Int = 0,
    /** Set when the camera has to be put back where the game says, rather than where a finger left it. */
    val recenter: Long = 0,
)

/**
 * The game as the screen sees it.
 *
 * [workDispatcher] is where a warehouse is built. One round of the generator is a
 * visible pause on a watch CPU and the largest size may take two dozen of them,
 * which is a frozen screen rather than a progress bar if it runs on the main
 * thread.
 */
class SokobanViewModel(
    private val store: ProgressStore,
    private val levels: LevelSource,
    private val seedOf: () -> Int = { System.currentTimeMillis().toInt() },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SokobanUiState())
    val uiState: StateFlow<SokobanUiState> = _uiState.asStateFlow()

    /** The level of the collection currently in play, so it is not dealt again at once. */
    private var dealtIndex = -1

    // Every touch of storage goes through this, each waiting on the one before.
    private var settings: Job = Job().apply { complete() }

    init {
        settings =
            viewModelScope.launch {
                val saved = decodeSave(store.readSave())
                val size = saved?.size ?: store.readSize()
                val source = saved?.source ?: store.readSource()
                _uiState.update {
                    it.copy(
                        size = size,
                        source = source,
                        best = store.readBest(size, source),
                        canContinue = saved != null,
                    )
                }
            }
    }

    fun cycleSize() = chooseSetup(_uiState.value.size.next, _uiState.value.source)

    fun cycleSource() = chooseSetup(_uiState.value.size, _uiState.value.source.next)

    private fun chooseSetup(
        size: Size,
        source: Source,
    ) {
        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                store.writeSize(size)
                store.writeSource(source)
                // The level to keep out of the next fresh round is a level of the
                // size just played; it means nothing in another size's collection.
                dealtIndex = -1
                _uiState.update { it.copy(size = size, source = source, best = store.readBest(size, source)) }
            }
    }

    /** Pick the half-finished warehouse back up, exactly where it was left. */
    fun continueGame() {
        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                val saved = decodeSave(store.readSave())
                if (saved == null) {
                    _uiState.update { it.copy(canContinue = false) }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        screen = Screen.PLAYING,
                        size = saved.size,
                        source = saved.source,
                        best = store.readBest(saved.size, saved.source),
                        game = saved.game,
                        isRecord = false,
                        recenter = it.recenter + 1,
                    )
                }
            }
    }

    /**
     * Deal or build a warehouse.
     *
     * Both paths go off the main thread: reading a size out of assets is cheap but
     * not free, and building one is two dozen rounds of search. The screen says
     * "Building..." with a bar while that happens, which is the whole reason it is
     * not done inline.
     */
    fun startGame() {
        val setup = _uiState.value
        _uiState.update { it.copy(screen = Screen.BUILDING, game = null, building = 0f) }

        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                // Somebody may have left while this was queued - a second tap on
                // Play whose warehouse has already arrived, or a press of back.
                if (_uiState.value.screen != Screen.BUILDING) return@launch

                val level =
                    if (setup.source == Source.BUILT_IN) {
                        dealFromCollection(setup.size)
                    } else {
                        buildOne(setup.size)
                    }
                // And the same again, because building one takes a visible moment
                // and back is the obvious thing to press during it.
                if (_uiState.value.screen != Screen.BUILDING) return@launch
                if (level == null) {
                    // Nothing to play. Better to go back to the menu than to sit on
                    // a screen that says it is building something it never will.
                    _uiState.update { it.copy(screen = Screen.START) }
                    return@launch
                }

                val game = Game.newGame(level)
                store.writeSave(encodeSave(setup.size, setup.source, game))
                _uiState.update {
                    it.copy(
                        screen = Screen.PLAYING,
                        game = game,
                        canContinue = true,
                        isRecord = false,
                        recenter = it.recenter + 1,
                    )
                }
            }
    }

    /** One warehouse out of the collection, never repeating until the pool is spent. */
    private suspend fun dealFromCollection(size: Size): Level? {
        val count = levels.count(size)
        if (count == 0) return null
        val played = decodePlayed(store.readPlayed(size), count)
        val deal = dealLevel(played, Mulberry32(seedOf()), dealtIndex)
        if (deal.index < 0) return null
        dealtIndex = deal.index
        // Marked as dealt rather than as solved: a warehouse abandoned half way is
        // still one the player has seen, and dealing it again would be worse than
        // losing it from the collection.
        store.writePlayed(size, encodePlayed(markPlayed(deal.played, deal.index)))
        return levels.levelAt(size, deal.index)
    }

    /**
     * One warehouse built on the wrist, a round at a time.
     *
     * The rounds run off the main thread, and the state flow is updated between
     * them so the bar actually moves - a single blocking call would leave it at zero
     * until the whole run had finished.
     */
    private suspend fun buildOne(size: Size): Level? {
        dealtIndex = -1
        var build = Build(size, seedOf())
        while (!build.done) {
            build = withContext(workDispatcher) { stepBuild(build) }
            _uiState.update { it.copy(building = build.progress) }
            yield()
            if (_uiState.value.screen != Screen.BUILDING) return null
        }
        return build.result?.level
    }

    /** Step the keeper, and save where that leaves the warehouse. */
    fun step(direction: Direction) {
        val state = _uiState.value
        if (state.screen != Screen.PLAYING) return
        val next = state.game?.moved(direction) ?: return
        commit(next)
    }

    /** Take back the last step, crate and all. */
    fun undo() {
        val state = _uiState.value
        if (state.screen != Screen.PLAYING) return
        val next = state.game?.undone() ?: return
        commit(next)
    }

    /** Clear the warehouse without dealing a new one, so the same one can be tried again. */
    fun restart() {
        val state = _uiState.value
        if (state.screen != Screen.PLAYING && state.screen != Screen.PAUSED) return
        val fresh = state.game?.restarted() ?: return
        _uiState.update { it.copy(screen = Screen.PLAYING, game = fresh, recenter = it.recenter + 1) }
        save(fresh)
    }

    private fun commit(game: Game) {
        _uiState.update { it.copy(game = game) }
        if (game.isSolved) finish(game) else save(game)
    }

    /**
     * The position after every step, so a warehouse can be finished over several
     * sittings. Queued behind whatever storage is already doing, like every other
     * write, so a fast run of steps cannot overtake itself.
     */
    private fun save(game: Game) {
        val setup = _uiState.value
        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                store.writeSave(encodeSave(setup.size, setup.source, game))
            }
    }

    fun pauseGame() {
        if (_uiState.value.screen != Screen.PLAYING) return
        _uiState.update { it.copy(screen = Screen.PAUSED) }
    }

    fun resumeGame() {
        if (_uiState.value.screen != Screen.PAUSED) return
        _uiState.update { it.copy(screen = Screen.PLAYING) }
    }

    fun showStart() {
        _uiState.update { it.copy(screen = Screen.START, isRecord = false) }
    }

    /** Drag the map. The caller has already clamped the camera to what the board allows. */
    fun moveCamera(
        x: Int,
        y: Int,
    ) {
        _uiState.update { it.copy(cameraX = x, cameraY = y) }
    }

    fun camera(): Camera = Camera(_uiState.value.cameraX, _uiState.value.cameraY)

    private fun finish(game: Game) {
        val setup = _uiState.value
        val outcome = updateBest(setup.best, game.moves)
        _uiState.update {
            it.copy(
                screen = Screen.SOLVED,
                game = game,
                best = outcome.best,
                isRecord = outcome.isRecord,
                canContinue = false,
            )
        }

        val previous = settings
        settings =
            viewModelScope.launch {
                previous.join()
                // Not cancellable. The app being closed the instant a warehouse falls
                // is exactly when the record is worth keeping.
                withContext(NonCancellable) {
                    // The finished warehouse is not something to be picked up again.
                    store.writeSave(null)
                    if (outcome.isRecord) store.writeBest(setup.size, setup.source, outcome.best)
                }
            }
    }

    companion object {
        fun factory(
            store: ProgressStore,
            levels: LevelSource,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    @Suppress("UNCHECKED_CAST")
                    return SokobanViewModel(store, levels) as T
                }
            }
    }
}
