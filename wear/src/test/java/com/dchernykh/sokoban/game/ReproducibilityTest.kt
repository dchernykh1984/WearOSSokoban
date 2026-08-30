package com.dchernykh.sokoban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The generator against the generator it was ported from.
 *
 * The claim is not merely that this builds a playable warehouse - the rest of
 * GeneratorTest covers that - but that it builds the *same* warehouse the Zepp OS
 * app builds from the same seed. That is a strong claim and an easy one to break:
 * the whole thing runs off one stream of random numbers, so any change to the order
 * the draws are taken in, or to the precision they are taken at, silently produces a
 * different warehouse from the same seed while every other test still passes.
 *
 * The fixtures in src/test/resources/zepp-generated.txt were printed by the Zepp OS
 * app's own `lib/generator.js`, run under node with `seeded(seed)`. Two real bugs
 * were found by comparing against them, and neither could have been found any other
 * way:
 *
 *   * the block placement drew the row before the column, where the original draws
 *     the column first;
 *   * the sectors the goals are spread over were taken in the order the candidates
 *     happened to arrive, where JavaScript hands back integer object keys in
 *     ascending order however they were inserted.
 */
private fun referenceCases(): List<Triple<Size, Int, String>> {
    val text = File("src/test/resources/zepp-generated.txt").readText()
    val cases = mutableListOf<Triple<Size, Int, String>>()

    var header: Pair<Size, Int>? = null
    val picture = StringBuilder()
    for (line in text.lines()) {
        when {
            line.startsWith("CASE ") -> {
                val parts = line.split(" ")
                header = Size.valueOf(parts[1]) to parts[2].toInt()
                picture.clear()
            }
            line == "ENDCASE" -> {
                header?.let { cases.add(Triple(it.first, it.second, picture.toString().trimEnd('\n'))) }
                header = null
            }
            header != null && line.isNotEmpty() -> picture.append(line).append('\n')
        }
    }
    return cases
}

class ReproducibilityTest {
    @Test
    fun `builds the very warehouse the Zepp OS app builds from the same seed`() {
        val cases = referenceCases()
        assertEquals("the fixtures did not load", 6, cases.size)

        for ((size, seed, expected) in cases) {
            val generated = generateLevel(size, seed)
            assertNotNull("$size seed $seed built nothing", generated)
            assertEquals("$size seed $seed", expected, formatLevel(generated!!.level))
        }
    }

    @Test
    fun `draws the same numbers as the generator it was ported from`() {
        // The first eight draws for two seeds, printed by the original's
        // lib/random.js. Nine digits, because a Float agrees to about seven - and
        // that is enough to flip a draw that lands near a whole number.
        val expected =
            mapOf(
                4242 to
                    listOf(
                        "0.546706134",
                        "0.278608789",
                        "0.931236917",
                        "0.507222466",
                        "0.668778280",
                        "0.287878973",
                        "0.259688982",
                        "0.401422797",
                    ),
                1 to
                    listOf(
                        "0.627073941",
                        "0.002735721",
                        "0.527447040",
                        "0.981050967",
                        "0.968377898",
                        "0.281103503",
                        "0.612838861",
                        "0.720743141",
                    ),
            )

        for ((seed, draws) in expected) {
            val random = Mulberry32(seed)
            for (draw in draws) {
                // A fixed locale, or a watch set to Russian prints "0,546706134"
                // and every one of these comparisons fails for the wrong reason.
                assertEquals("seed $seed", draw, String.format(Locale.ROOT, "%.9f", random.next()))
            }
        }
    }
}
