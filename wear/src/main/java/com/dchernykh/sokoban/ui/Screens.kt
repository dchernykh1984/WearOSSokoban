package com.dchernykh.sokoban.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dchernykh.sokoban.R
import com.dchernykh.sokoban.Screen
import com.dchernykh.sokoban.SokobanUiState
import com.dchernykh.sokoban.SokobanViewModel
import com.dchernykh.sokoban.game.Source
import com.dchernykh.sokoban.game.hasBest

// The five menus. They live one file away from the shell that hosts them because
// they are what changes when the game gains a screen, and the shell is what does
// not.

/** Whichever menu is in front, or none at all while a warehouse is being played. */
@Composable
fun Screens(
    screenSize: Int,
    metrics: MenuMetrics,
    state: SokobanUiState,
    viewModel: SokobanViewModel,
) {
    when (state.screen) {
        Screen.PLAYING -> Unit
        Screen.START -> StartMenu(screenSize, metrics, state, viewModel)
        Screen.BUILDING -> BuildingScreen(screenSize, metrics, state)
        Screen.PAUSED -> PausedMenu(screenSize, metrics, viewModel)
        Screen.SOLVED -> SolvedMenu(screenSize, metrics, state, viewModel)
    }
}

@Composable
private fun StartMenu(
    screenSize: Int,
    metrics: MenuMetrics,
    state: SokobanUiState,
    viewModel: SokobanViewModel,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items =
            buildList {
                add(MenuItem.Line(metrics.big, ColorText, stringResource(R.string.app_name)))
                add(MenuItem.Gap(metrics.gap))
                add(MenuItem.Line(metrics.row, ColorMuted, bestLine(state)))
                add(MenuItem.Gap(metrics.gap))
                // Each picker is captioned, because the button under it says only
                // what is chosen - "XS" and "Built-in" say nothing about what they
                // are choosing between.
                add(MenuItem.Line(metrics.small, ColorMuted, stringResource(R.string.size_label)))
                // The size itself is letters only and so needs no translating.
                add(MenuItem.Action(metrics.button, state.size.label, viewModel::cycleSize))
                add(MenuItem.Gap(metrics.gap))
                add(MenuItem.Line(metrics.small, ColorMuted, stringResource(R.string.source_label)))
                add(MenuItem.Action(metrics.button, sourceLabel(state.source), viewModel::cycleSource))
                add(MenuItem.Gap(metrics.gap))
                // A warehouse left unfinished is offered before a new one: the big
                // sizes take more than one sitting, and losing that position would
                // be the whole point of having saved it.
                if (state.canContinue) {
                    add(
                        MenuItem.Action(
                            metrics.button,
                            stringResource(R.string.continue_game),
                            viewModel::continueGame,
                        ),
                    )
                    add(MenuItem.Gap(metrics.gap))
                }
                add(MenuItem.Action(metrics.button, stringResource(R.string.play), viewModel::startGame))
            },
    )
}

/**
 * Shown while the watch builds a warehouse.
 *
 * Building one is up to two dozen rounds of reverse play, which is a visible pause
 * on the largest sizes - so the screen says what is happening and shows how far
 * along it is, rather than appearing to have stopped.
 */
@Composable
private fun BuildingScreen(
    screenSize: Int,
    metrics: MenuMetrics,
    state: SokobanUiState,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items =
            listOf(
                MenuItem.Line(metrics.row, ColorMuted, stringResource(R.string.generating)),
                MenuItem.Gap(metrics.gap),
                MenuItem.Bar(metrics.small, state.building),
            ),
    )
}

@Composable
private fun PausedMenu(
    screenSize: Int,
    metrics: MenuMetrics,
    viewModel: SokobanViewModel,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items =
            listOf(
                MenuItem.Action(metrics.button, stringResource(R.string.resume), viewModel::resumeGame),
                MenuItem.Gap(metrics.gap),
                MenuItem.Action(metrics.button, stringResource(R.string.restart), viewModel::restart),
                MenuItem.Gap(metrics.gap),
                // A new warehouse of the same size, without going back for it.
                MenuItem.Action(metrics.button, stringResource(R.string.new_game), viewModel::startGame),
                MenuItem.Gap(metrics.gap),
                MenuItem.Action(metrics.button, stringResource(R.string.size_label), viewModel::showStart),
            ),
    )
}

@Composable
private fun SolvedMenu(
    screenSize: Int,
    metrics: MenuMetrics,
    state: SokobanUiState,
    viewModel: SokobanViewModel,
) {
    MenuOverlay(
        screenSize = screenSize,
        metrics = metrics,
        items =
            listOf(
                MenuItem.Line(metrics.big, ColorAccent, stringResource(R.string.solved)),
                MenuItem.Gap(metrics.gap),
                MenuItem.Line(
                    metrics.row,
                    ColorText,
                    stringResource(R.string.moves_value, stringResource(R.string.moves), state.game?.moves ?: 0),
                ),
                MenuItem.Line(
                    metrics.row,
                    if (state.isRecord) ColorAccent else ColorMuted,
                    if (state.isRecord) stringResource(R.string.new_best) else bestLine(state),
                ),
                MenuItem.Gap(metrics.gap),
                MenuItem.Action(metrics.button, stringResource(R.string.new_game), viewModel::startGame),
                MenuItem.Gap(metrics.gap),
                MenuItem.Action(metrics.button, stringResource(R.string.size_label), viewModel::showStart),
            ),
    )
}

/** The best for the size and source on show, or a dash when there is none. */
@Composable
private fun bestLine(state: SokobanUiState): String {
    val value = if (hasBest(state.best)) state.best.toString() else stringResource(R.string.no_score)
    return stringResource(R.string.best_value, stringResource(R.string.best), value)
}

@Composable
private fun sourceLabel(source: Source): String =
    stringResource(if (source == Source.BUILT_IN) R.string.source_builtin else R.string.source_random)
