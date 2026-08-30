package com.dchernykh.sokoban

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dchernykh.sokoban.game.Direction
import com.dchernykh.sokoban.game.Size
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * What no JVM test can check: that the game actually runs on a watch.
 *
 * Launching the activity exercises the manifest, the theme, the launcher icon, the
 * warehouse canvas, the whole Compose tree, the DataStore-backed progress store and
 * the reader that pulls the shipped collection out of the APK's assets - the parts
 * excused from the coverage floor precisely because they need a device. The rules,
 * the solver and the generator are covered far more cheaply by the unit tests, so
 * this walks the menu, deals a warehouse and takes a step in it.
 *
 * Every test starts from whatever is on screen rather than from what it would like,
 * because the size and the source are stored: a test that assumed one of them would
 * pass or fail depending on what had run before it.
 *
 * Every label is read from the resources, so the test says the same thing on a watch
 * set to any of the eleven languages.
 */
class GameScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private fun onScreen(label: String) = rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()

    private fun arrow(direction: Direction) = rule.onAllNodesWithContentDescription(direction.name)

    /** The two icon buttons beside the down arrow, which carry no words to find them by. */
    private fun icon(id: Int) = rule.onAllNodesWithContentDescription(text(id))

    @Test
    fun opensOnTheMenu() {
        rule.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.play)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.size_label)).assertIsDisplayed()
    }

    @Test
    fun walksTheSizes() {
        val labels = Size.entries.map { it.label }
        rule.waitUntil { labels.any(::onScreen) }
        val before = labels.first(::onScreen)

        rule.onNodeWithText(before).performClick()
        rule.waitUntil { !onScreen(before) }

        assertNotEquals(before, labels.first(::onScreen))
    }

    @Test
    fun walksTheSources() {
        val builtIn = text(R.string.source_builtin)
        val random = text(R.string.source_random)
        rule.waitUntil { onScreen(builtIn) || onScreen(random) }
        val before = if (onScreen(builtIn)) builtIn else random

        rule.onNodeWithText(before).performClick()
        rule.waitUntil { !onScreen(before) }

        assertNotEquals(before, if (onScreen(builtIn)) builtIn else random)
    }

    @Test
    fun dealsAWarehouseAndTakesAStepInIt() {
        walkToBuiltIn()
        walkToSize(Size.XS)
        deal()

        // Every arrow is on screen, and one of them moves the keeper - which is the
        // proof that the canvas, the layout and the rules met each other correctly.
        for (direction in Direction.entries) {
            arrow(direction).assertCountEquals(1)
        }
        for (direction in Direction.entries) {
            arrow(direction)[0].performClick()
        }
        rule.waitForIdle()

        icon(R.string.menu)[0].performClick()
        rule.waitUntil { onScreen(text(R.string.resume)) }
        rule.onNodeWithText(text(R.string.restart)).performClick()
        rule.waitUntil { !onScreen(text(R.string.resume)) }
    }

    @Test
    fun dealsTheLargestWarehouseTheCollectionHas() {
        // The size that shows least of itself at once: the window is smaller than
        // the warehouse, so most of it is off the canvas and only the camera decides
        // what is drawn.
        walkToBuiltIn()
        walkToSize(Size.XXL)
        deal()

        icon(R.string.menu)[0].performClick()
        rule.waitUntil { onScreen(text(R.string.resume)) }
        // The way back to the sizes is labelled with what it goes back to.
        rule.onNodeWithText(text(R.string.size_label)).performClick()
        rule.waitUntil { onScreen(text(R.string.play)) }
    }

    @Test
    fun offersToPickUpAWarehouseLeftHalfFinished() {
        walkToBuiltIn()
        walkToSize(Size.XS)
        deal()

        arrow(Direction.RIGHT)[0].performClick()
        rule.waitForIdle()
        icon(R.string.menu)[0].performClick()
        rule.waitUntil { onScreen(text(R.string.resume)) }
        rule.onNodeWithText(text(R.string.size_label)).performClick()

        // Back on the start menu, the warehouse just left is offered again.
        rule.waitUntil(timeoutMillis = 5_000) { onScreen(text(R.string.continue_game)) }
        rule.onNodeWithText(text(R.string.continue_game)).performClick()
        rule.waitUntil { icon(R.string.undo).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Tap Play and wait for the warehouse to arrive. */
    private fun deal() {
        rule.onNodeWithText(text(R.string.play)).performClick()
        rule.waitUntil(timeoutMillis = 30_000) { icon(R.string.undo).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Tap the size button until the one wanted is showing. */
    private fun walkToSize(wanted: Size) {
        val labels = Size.entries.map { it.label }
        rule.waitUntil { labels.any(::onScreen) }
        repeat(Size.entries.size) {
            if (onScreen(wanted.label)) return
            val showing = labels.first(::onScreen)
            rule.onNodeWithText(showing).performClick()
            rule.waitUntil { !onScreen(showing) }
        }
        rule.onNodeWithText(wanted.label).assertIsDisplayed()
    }

    /** Tap the source button until the collection is the one selected. */
    private fun walkToBuiltIn() {
        val builtIn = text(R.string.source_builtin)
        rule.waitUntil { onScreen(builtIn) || onScreen(text(R.string.source_random)) }
        if (onScreen(builtIn)) return

        rule.onNodeWithText(text(R.string.source_random)).performClick()
        rule.waitUntil { onScreen(builtIn) }
    }
}
