package com.dchernykh.sokoban.ui

import androidx.compose.ui.graphics.Color

// The colours, carried over unchanged from the Zepp OS original so the two versions
// of the game look like the same game.
//
// A warehouse is read at arm's length in daylight, so every kind of cell is a solid
// block of colour: a crate that is home is green and one that is not is timber, and
// the difference has to be obvious without counting.

/** Pixels kept between anything centred and the edge of the circle. */
const val SCREEN_PADDING = 8

val ColorBackground = Color(0xFF000000)
val ColorFloor = Color(0xFF1B2027)
val ColorWall = Color(0xFF39424C)
val ColorGoal = Color(0xFF2F4A3A)
val ColorGoalMark = Color(0xFF6FCF97)
val ColorBox = Color(0xFFB07D3C)
val ColorBoxHome = Color(0xFF2A9D5C)
val ColorKeeper = Color(0xFF56A8E0)
val ColorFrame = Color(0xFF2A323B)

val ColorText = Color(0xFFFFFFFF)
val ColorMuted = Color(0xFF93A1AD)
val ColorAccent = Color(0xFFF0A202)
val ColorButton = Color(0xFF1A2027)
val ColorButtonPressed = Color(0xFF2F3D46)
val ColorArrow = Color(0xFF7E8B99)
val ColorArrowPressed = Color(0xFFD7E2EC)

/**
 * How opaque the panel behind a stacked menu is. Not fully, so the warehouse
 * underneath still shows through and the menu reads as something laid over the game
 * rather than a different screen.
 */
const val PANEL_ALPHA = 225f / 255f

/**
 * How far a finger may travel and still count as a tap rather than a drag. A
 * fingertip never lands and lifts on exactly one pixel, and without some slack a
 * map that can be dragged would swallow half the taps meant to step.
 */
const val DRAG_SLOP = 8f
