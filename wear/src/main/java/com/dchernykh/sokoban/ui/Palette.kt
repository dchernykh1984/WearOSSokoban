package com.dchernykh.sokoban.ui

import androidx.compose.ui.graphics.Color

// The colours, carried over unchanged from the Zepp OS original so the two versions
// of the game look like the same game.
//
// A warehouse is read at arm's length in daylight, so every kind of cell carries an
// edge a shade off its face: a flat slab of one colour reads as one shape, and the
// edges are what make a grid of cells out of it.

/** Pixels kept between anything centred and the edge of the circle. */
const val SCREEN_PADDING = 8

val ColorBackground = Color(0xFF000000)
val ColorFloor = Color(0xFF141A1F)
val ColorFloorEdge = Color(0xFF1D262C)
val ColorWall = Color(0xFF46545F)
val ColorWallEdge = Color(0xFF5B6C79)

/** The goal is a ring painted on the floor, not a pit: the crate ends up there, it does not fall in. */
val ColorGoal = Color(0xFF2FBF71)

val ColorBox = Color(0xFFC9873A)
val ColorBoxEdge = Color(0xFF8A5A20)
val ColorBoxHome = Color(0xFF35A86A)
val ColorBoxHomeEdge = Color(0xFF1D6B41)
val ColorKeeper = Color(0xFF4FA8FF)

/** The bright mark on the keeper's leading edge, which is how a player sees which way it faces. */
val ColorKeeperFace = Color(0xFFDFF0FF)

val ColorFrame = Color(0xFF2B3339)

val ColorText = Color(0xFFFFFFFF)
val ColorMuted = Color(0xFF9AA4AB)
val ColorAccent = Color(0xFF2FBF71)
val ColorButton = Color(0xFF1D262C)
val ColorButtonPressed = Color(0xFF2F3D46)

/**
 * The controls are drawn white, as the original drew them - a canvas has no pressed
 * state to offer, so it had only one colour to give them. Wear OS does, and a finger
 * with no feedback is a finger that presses twice, so they dim while held.
 */
val ColorArrow = Color(0xFFFFFFFF)
val ColorArrowPressed = Color(0xFF9AA4AB)

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
