package com.dchernykh.sokoban.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import com.dchernykh.sokoban.R
import com.dchernykh.sokoban.Screen
import com.dchernykh.sokoban.SokobanUiState
import com.dchernykh.sokoban.SokobanViewModel
import com.dchernykh.sokoban.game.Direction
import com.dchernykh.sokoban.game.Game
import com.dchernykh.sokoban.layout.BoardWindow
import com.dchernykh.sokoban.layout.Camera
import com.dchernykh.sokoban.layout.ControlLayout
import com.dchernykh.sokoban.layout.Grid
import com.dchernykh.sokoban.layout.arrowMetrics
import com.dchernykh.sokoban.layout.boardWindow
import com.dchernykh.sokoban.layout.centerCamera
import com.dchernykh.sokoban.layout.controlLayout
import com.dchernykh.sokoban.layout.followCamera
import com.dchernykh.sokoban.layout.panCamera
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The whole screen: the window onto the warehouse, the four arrows in the segments
 * around it, and whichever menu is in front.
 *
 * The arrows and the map never fight over a touch, because they live in different
 * places: the board keeps the square window to itself and every control sits in the
 * round caps and segments outside it, which are dead space on a round watch.
 */
@Composable
fun SokobanApp(viewModel: SokobanViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val container = LocalWindowInfo.current.containerSize
    val screenSize = minOf(container.width, container.height)
    if (screenSize <= 0) return

    val menu = remember(screenSize) { MenuMetrics(screenSize) }
    val game = state.game
    val window = remember(screenSize, state.size) { boardWindow(screenSize, state.size.visible) }
    val controls = remember(screenSize, window) { controlLayout(screenSize, window) }

    // The map opens with the keeper in the middle of the window, and is put back
    // there whenever a new warehouse arrives or the same one is restarted.
    LaunchedEffect(state.recenter, window) {
        val level = game?.level ?: return@LaunchedEffect
        val centre =
            centerCamera(window, Grid(level.cols, level.rows), level.columnOf(game.player), level.rowOf(game.player))
        viewModel.moveCamera(centre.x, centre.y)
    }

    // A warehouse is thought about rather than tapped at, and a ten-second display
    // timeout would black out mid-deduction.
    KeepScreenOnWhile(state.screen == Screen.PLAYING)

    BackHandler(enabled = state.screen != Screen.START) {
        when (state.screen) {
            Screen.PLAYING -> viewModel.pauseGame()
            else -> viewModel.showStart()
        }
    }

    MaterialTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(ColorBackground)
                    .mapGestures(state, window, viewModel),
        ) {
            if (game != null && state.screen != Screen.START) {
                BoardCanvas(
                    game = game,
                    window = window,
                    camera = Camera(state.cameraX, state.cameraY),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (state.screen == Screen.PLAYING && game != null) {
                PlayControls(controls, game, viewModel, window)
            }

            Screens(screenSize, menu, state, viewModel)
        }
    }
}

@Composable
private fun KeepScreenOnWhile(playing: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Dragging the window moves the map.
 *
 * A drag has to travel further than the slop before it counts as one, so a tap that
 * wobbles is still a tap - and a tap on the board itself does nothing at all, which
 * is what keeps a misjudged drag from stepping the keeper somewhere unwanted.
 */
private fun Modifier.mapGestures(
    state: SokobanUiState,
    window: BoardWindow,
    viewModel: SokobanViewModel,
): Modifier {
    val level = state.game?.level
    return if (state.screen != Screen.PLAYING || level == null) {
        this
    } else {
        this.pointerInput(window, level) {
            var start = Camera(0, 0)
            var travelled = 0f
            var dx = 0f
            var dy = 0f
            var onTheBoard = false
            detectDragGestures(
                onDragStart = { offset ->
                    // Only a finger that went down on the window drags the map. The
                    // arrows are outside it, and a thumb that slides while pressing
                    // one is aiming a step, not moving the warehouse.
                    onTheBoard = (offset.x.roundToInt() to offset.y.roundToInt()) in window.box
                    start = viewModel.camera()
                    travelled = 0f
                    dx = 0f
                    dy = 0f
                },
            ) { change, drag ->
                if (!onTheBoard) return@detectDragGestures
                travelled += abs(drag.x) + abs(drag.y)
                dx += drag.x
                dy += drag.y
                if (travelled > DRAG_SLOP) {
                    change.consume()
                    val moved = panCamera(start, dx.roundToInt(), dy.roundToInt(), window, Grid(level.cols, level.rows))
                    viewModel.moveCamera(moved.x, moved.y)
                }
            }
        }
    }
}

/**
 * The arrows, the two buttons and the counters.
 *
 * Every control is placed by the same pure layout a test can ask about, so what a
 * finger lands on is decided by arithmetic rather than by a stack of composables
 * that happen to be in the way.
 */
@Composable
private fun PlayControls(
    controls: ControlLayout,
    game: Game,
    viewModel: SokobanViewModel,
    window: BoardWindow,
) {
    MenuLine(
        controls.counter,
        ColorText,
        stringResource(R.string.counter_value, game.boxesOnGoals, game.level.goals.size, game.moves),
        fraction = 0.86f,
    )

    val arrows =
        listOf(
            controls.up to Direction.UP,
            controls.down to Direction.DOWN,
            controls.left to Direction.LEFT,
            controls.right to Direction.RIGHT,
        )
    // One size for all four and for the two icons beside them, worked out from the
    // tightest button, so the whole row reads as one set of controls rather than as
    // four symbols that happen to point different ways.
    val metrics = remember(controls) { arrowMetrics(arrows.map { it.first } + controls.undo + controls.menu) }

    for ((box, direction) in arrows) {
        ArrowButton(box = box, direction = direction, metrics = metrics, label = direction.name) {
            viewModel.step(direction)
            follow(viewModel, window)
        }
    }

    UndoButton(controls.undo, metrics, stringResource(R.string.undo), viewModel::undo)
    MenuButton(controls.menu, metrics, stringResource(R.string.menu), viewModel::pauseGame)
}

/**
 * Drag the map along behind the keeper when it walks towards the edge of the window,
 * the way a navigator scrolls ahead of you - and leave a map deliberately dragged
 * elsewhere where it was put.
 *
 * Where the keeper actually is afterwards, not where the step was aimed: a step into
 * a wall moves nothing, and a map that slid anyway would answer a tap that the rules
 * had already refused.
 */
private fun follow(
    viewModel: SokobanViewModel,
    window: BoardWindow,
) {
    val game = viewModel.uiState.value.game ?: return
    val level = game.level
    val moved =
        followCamera(
            viewModel.camera(),
            window,
            Grid(level.cols, level.rows),
            level.columnOf(game.player),
            level.rowOf(game.player),
        )
    viewModel.moveCamera(moved.x, moved.y)
}
