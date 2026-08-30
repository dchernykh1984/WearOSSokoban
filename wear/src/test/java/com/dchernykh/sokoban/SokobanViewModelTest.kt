package com.dchernykh.sokoban

import com.dchernykh.sokoban.game.Direction
import com.dchernykh.sokoban.game.Game
import com.dchernykh.sokoban.game.Level
import com.dchernykh.sokoban.game.Size
import com.dchernykh.sokoban.game.Source
import com.dchernykh.sokoban.game.decodePlayed
import com.dchernykh.sokoban.game.decodeSave
import com.dchernykh.sokoban.game.encodePlayed
import com.dchernykh.sokoban.game.encodeSave
import com.dchernykh.sokoban.game.parseLevel
import com.dchernykh.sokoban.store.LevelSource
import com.dchernykh.sokoban.store.ProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** An in-memory stand-in for the watch's storage. */
private class FakeStore : ProgressStore {
    var size = Size.DEFAULT
    var source = Source.DEFAULT
    val bests = mutableMapOf<String, Int>()
    val played = mutableMapOf<Size, String>()
    var save: String? = null

    private fun key(
        size: Size,
        source: Source,
    ) = "${source.name}_${size.name}"

    override suspend fun readSize(): Size = size

    override suspend fun writeSize(size: Size) {
        this.size = size
    }

    override suspend fun readSource(): Source = source

    override suspend fun writeSource(source: Source) {
        this.source = source
    }

    override suspend fun readBest(
        size: Size,
        source: Source,
    ): Int = bests[key(size, source)] ?: 0

    override suspend fun writeBest(
        size: Size,
        source: Source,
        moves: Int,
    ) {
        bests[key(size, source)] = moves
    }

    override suspend fun readPlayed(size: Size): String? = played[size]

    override suspend fun writePlayed(
        size: Size,
        played: String,
    ) {
        this.played[size] = played
    }

    override suspend fun readSave(): String? = save

    override suspend fun writeSave(save: String?) {
        this.save = save
    }
}

/** Two corridors standing in for the shipped collection of four thousand. */
private class FakeLevels(
    private val pictures: List<String> =
        listOf(
            "#######\n#@$--.#\n#######",
            "########\n#@$---.#\n########",
        ),
) : LevelSource {
    override suspend fun count(size: Size): Int = pictures.size

    override suspend fun levelAt(
        size: Size,
        index: Int,
    ): Level? = pictures.getOrNull(index)?.let(::parseLevel)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SokobanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val seed = 1

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        store: ProgressStore = FakeStore(),
        levels: LevelSource = FakeLevels(),
    ) = SokobanViewModel(store, levels, seedOf = { seed }, workDispatcher = dispatcher)

    /** One warehouse, so a test that counts moves knows which one it is counting. */
    private fun oneCorridor() = FakeLevels(listOf("#######\n#@$--.#\n#######"))

    /** Push the crate all the way home, which is the whole of these warehouses. */
    private fun solve(model: SokobanViewModel) {
        var guard = 0
        while (model.uiState.value.screen == Screen.PLAYING && guard++ < 50) {
            model.step(Direction.RIGHT)
        }
    }

    @Test
    fun `opens on the start screen with what was last played`() =
        runTest(dispatcher) {
            val store =
                FakeStore().apply {
                    size = Size.L
                    source = Source.GENERATED
                }
            store.bests["GENERATED_L"] = 42
            val model = viewModel(store)
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(Screen.START, state.screen)
            assertEquals(Size.L, state.size)
            assertEquals(Source.GENERATED, state.source)
            assertEquals(42, state.best)
            assertFalse(state.canContinue)
            assertNull(state.game)
        }

    @Test
    fun `cycles the size and the source, and remembers both`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.cycleSize()
            advanceUntilIdle()
            assertEquals(Size.S, model.uiState.value.size)
            assertEquals(Size.S, store.size)

            model.cycleSource()
            advanceUntilIdle()
            assertEquals(Source.GENERATED, model.uiState.value.source)
            assertEquals(Source.GENERATED, store.source)
        }

    @Test
    fun `shows the record kept for the size and source now chosen`() =
        runTest(dispatcher) {
            val store = FakeStore()
            store.bests["BUILT_IN_S"] = 99
            val model = viewModel(store)
            advanceUntilIdle()

            assertEquals(0, model.uiState.value.best)
            model.cycleSize()
            advanceUntilIdle()

            assertEquals(99, model.uiState.value.best)
        }

    @Test
    fun `deals a warehouse from the collection and saves it at once`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(Screen.PLAYING, state.screen)
            assertNotNull(state.game)
            assertEquals(0, state.game!!.moves)
            assertNotNull("a dealt warehouse is saved before the first step", store.save)
            assertTrue(state.canContinue)
        }

    @Test
    fun `says it is building while it works`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.startGame()

            assertEquals(Screen.BUILDING, model.uiState.value.screen)
            advanceUntilIdle()
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
        }

    @Test
    fun `marks a dealt warehouse as played so it does not come round again`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            assertEquals(1, decodePlayed(store.played[Size.XS], 2).count { it })
        }

    @Test
    fun `works through the collection before repeating a warehouse`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()
            val first =
                model.uiState.value.game!!
                    .level.cols
            model.startGame()
            advanceUntilIdle()

            assertEquals(2, decodePlayed(store.played[Size.XS], 2).count { it })
            assertTrue(
                "the second deal is the other warehouse",
                first !=
                    model.uiState.value.game!!
                        .level.cols,
            )
        }

    @Test
    fun `goes back to the menu when there is nothing to deal`() =
        runTest(dispatcher) {
            val model = viewModel(levels = FakeLevels(emptyList()))
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            assertEquals(Screen.START, model.uiState.value.screen)
            assertNull(model.uiState.value.game)
        }

    @Test
    fun `builds a warehouse on the wrist, and moves the bar while it does`() =
        runTest(dispatcher) {
            val store = FakeStore().apply { source = Source.GENERATED }
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()

            assertEquals(Screen.PLAYING, model.uiState.value.screen)
            assertNotNull(model.uiState.value.game)
            assertEquals(1f, model.uiState.value.building, 0.001f)
            assertTrue("a built warehouse spends nothing from the collection", store.played.isEmpty())
        }

    @Test
    fun `steps the keeper and counts the move`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            val before = model.uiState.value.game!!

            model.step(Direction.RIGHT)

            val after = model.uiState.value.game!!
            assertEquals(before.player + 1, after.player)
            assertEquals(1, after.moves)
            assertEquals(1, after.pushes)
        }

    @Test
    fun `does nothing at all when a step is not a legal one`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.step(Direction.UP)

            assertEquals(
                0,
                model.uiState.value.game!!
                    .moves,
            )
        }

    @Test
    fun `saves the position after every step`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.step(Direction.RIGHT)
            advanceUntilIdle()

            assertEquals(1, decodeSave(store.save)!!.game.moves)
        }

    @Test
    fun `takes back the last step, and stops at the start`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.step(Direction.RIGHT)
            model.undo()
            assertEquals(
                0,
                model.uiState.value.game!!
                    .moves,
            )
            model.undo()
            assertEquals(
                "nothing left to take back",
                0,
                model.uiState.value.game!!
                    .moves,
            )
        }

    @Test
    fun `clears the warehouse without dealing a new one`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            val picture =
                model.uiState.value.game!!
                    .level

            model.step(Direction.RIGHT)
            model.restart()

            val after = model.uiState.value.game!!
            assertEquals(0, after.moves)
            assertEquals("it is the same warehouse", picture.cols, after.level.cols)
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
        }

    @Test
    fun `pauses over the warehouse and comes back to it`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            model.pauseGame()
            assertEquals(Screen.PAUSED, model.uiState.value.screen)
            model.resumeGame()
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
        }

    @Test
    fun `ignores a step on a screen that is not the warehouse`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            model.pauseGame()

            model.step(Direction.RIGHT)
            model.undo()
            model.resumeGame()
            model.resumeGame()

            assertEquals(
                0,
                model.uiState.value.game!!
                    .moves,
            )
            assertEquals(Screen.PLAYING, model.uiState.value.screen)
        }

    @Test
    fun `has nothing to do before a warehouse is dealt`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.step(Direction.RIGHT)
            model.undo()
            model.restart()
            model.pauseGame()

            assertEquals(Screen.START, model.uiState.value.screen)
        }

    @Test
    fun `shows the solved screen and keeps the record when the last crate lands`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store, oneCorridor())
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            solve(model)
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(Screen.SOLVED, state.screen)
            assertEquals("three cells between the crate and its goal", 3, state.game!!.moves)
            assertEquals(3, state.best)
            assertTrue(state.isRecord)
            assertEquals(3, store.bests["BUILT_IN_XS"])
        }

    @Test
    fun `keeps the fewer moves and does not call a slower run a record`() =
        runTest(dispatcher) {
            val store = FakeStore()
            store.bests["BUILT_IN_XS"] = 2
            val model = viewModel(store, oneCorridor())
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            solve(model)
            advanceUntilIdle()

            assertFalse(model.uiState.value.isRecord)
            assertEquals(2, model.uiState.value.best)
            assertEquals("a slower run never overwrites the record", 2, store.bests["BUILT_IN_XS"])
        }

    @Test
    fun `throws the save away once the warehouse is finished`() =
        runTest(dispatcher) {
            // A finished warehouse is not something to be picked up again.
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            solve(model)
            advanceUntilIdle()

            assertNull(store.save)
            assertFalse(model.uiState.value.canContinue)
        }

    @Test
    fun `offers to pick up a warehouse left half-finished`() =
        runTest(dispatcher) {
            val store = FakeStore()
            var game = Game.newGame(parseLevel("#######\n#@$--.#\n#######")!!)
            game = game.moved(Direction.RIGHT)!!
            store.save = encodeSave(Size.M, Source.GENERATED, game)

            val model = viewModel(store)
            advanceUntilIdle()

            assertTrue(model.uiState.value.canContinue)
            assertEquals("the saved size is the one offered", Size.M, model.uiState.value.size)
            assertEquals(Source.GENERATED, model.uiState.value.source)
        }

    @Test
    fun `picks it up exactly where it was left`() =
        runTest(dispatcher) {
            val store = FakeStore()
            var game = Game.newGame(parseLevel("#######\n#@$--.#\n#######")!!)
            repeat(2) { game = game.moved(Direction.RIGHT)!! }
            store.save = encodeSave(Size.XS, Source.BUILT_IN, game)

            val model = viewModel(store)
            advanceUntilIdle()
            model.continueGame()
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(Screen.PLAYING, state.screen)
            assertEquals(2, state.game!!.moves)
            assertEquals(game.player, state.game.player)
            assertEquals(game.boxes.toList(), state.game.boxes.toList())
        }

    @Test
    fun `stops offering to continue when the save has gone`() =
        runTest(dispatcher) {
            val store = FakeStore()
            store.save = "not a save at all"
            val model = viewModel(store)
            advanceUntilIdle()

            model.continueGame()
            advanceUntilIdle()

            assertEquals(Screen.START, model.uiState.value.screen)
            assertFalse(model.uiState.value.canContinue)
        }

    @Test
    fun `stays on the menu when back is pressed while a warehouse is being built`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            model.showStart()
            advanceUntilIdle()

            assertEquals(Screen.START, model.uiState.value.screen)
            assertNull("the warehouse nobody waited for is not shown", model.uiState.value.game)
            assertTrue("and none is spent on it", store.played.isEmpty())
        }

    @Test
    fun `deals one warehouse however many times Play is tapped`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            model.startGame()
            advanceUntilIdle()

            assertEquals(Screen.PLAYING, model.uiState.value.screen)
            assertEquals(1, decodePlayed(store.played[Size.XS], 2).count { it })
        }

    @Test
    fun `forgets the warehouse just played when the size changes`() =
        runTest(dispatcher) {
            // The index is a position in one size's collection. Carried into another
            // it names a different warehouse, and skips one there for no reason.
            val store = FakeStore()
            store.played[Size.S] = encodePlayed(booleanArrayOf(true, true))
            val model = viewModel(store)
            advanceUntilIdle()

            model.startGame()
            advanceUntilIdle()
            val playedInXs = decodePlayed(store.played[Size.XS], 2).indexOfFirst { it }

            model.cycleSize()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()

            assertEquals(
                "the fresh round in S skipped the warehouse the XS deal had used",
                playedInXs,
                decodePlayed(store.played[Size.S], 2).indexOfFirst { it },
            )
        }

    @Test
    fun `goes back to the menu with no record showing`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            model.startGame()
            advanceUntilIdle()
            solve(model)
            advanceUntilIdle()

            model.showStart()

            assertEquals(Screen.START, model.uiState.value.screen)
            assertFalse(model.uiState.value.isRecord)
        }

    @Test
    fun `remembers where the map has been dragged to`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.moveCamera(120, 30)

            assertEquals(120, model.uiState.value.cameraX)
            assertEquals(30, model.uiState.value.cameraY)
            assertEquals(120, model.camera().x)
        }
}
