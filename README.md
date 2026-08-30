# WearOS Sokoban

**Box Pusher** for **Wear OS** watches, in Kotlin and Jetpack Compose.

Sokoban is the classic warehouse puzzle: push every crate onto a goal. Crates only
ever move away from you, never towards you, so a crate shoved into a corner is
lost - which is why undo is part of the game rather than a convenience.

Everything runs on the watch: no phone, no network, no account.

This is a port of
[AmazfitSokoban](https://github.com/dchernykh1984/AmazfitSokoban), the same game as
a Zepp OS mini app. The **4,000 shipped warehouses are the same files, byte for
byte**, and the rules, the generator, the layout proportions and the eleven
translations are carried over unchanged; the implementation is new.

## Playing it

- **Four arrows** in the segments around the board step the keeper one cell. They
  sit in the round caps and corners a square board leaves over, so steering never
  fights with dragging: the window belongs to the map and nothing else is drawn in
  it.
- **Drag the board** to move the map, pixel for pixel, the way a navigator does. The
  map also follows the keeper on its own when it walks towards the edge of the
  window, and stays where it was put otherwise.
- **Undo** takes back the last step, crate and all. **The menu** pauses over the
  board. Both are drawn as icons rather than lettered - a symbol reads the same in
  all eleven languages, and there is no room for a word beside the down arrow.
- **XS through XXL** - 9x9 with two crates up to 19x19 with seven. XS and S fit the
  screen whole; from M upwards the warehouse is bigger than the window and has to be
  dragged around.
- **Built-in / Random** picks where a warehouse comes from. The collection is dealt
  without repeating one until the whole pool has been played; **Random** builds a
  fresh warehouse on the wrist behind a progress bar.
- **Pick it up later** - the position is saved after every step, so a 19x19
  warehouse can be finished over several sittings. The whole board is saved, not a
  level number, so a future collection cannot leave you standing in a wall.
- **Best score** is the fewest moves, kept per size **and** per source: a warehouse
  the watch rolled is not the same challenge as one that was vetted before it
  shipped.
- **Languages** - English, Russian, German, French, Italian, Spanish, Portuguese,
  Dutch, Polish, Czech and Kazakh. The watch's own language is followed, and all
  eleven are offered individually in the system per-app language list - so Kazakh,
  which Zepp OS had no device-language code for, finally reaches the people it was
  translated for.

## Where the warehouses come from

The collection ships as six plain-text `.sok` files in
`wear/src/main/assets/levels/`, copied unchanged from the Zepp OS app - 1,000 each
at XS, S and M, 500 at L, 300 at XL and 200 at XXL, 690KB in all. A test reads every
one of the 4,000: it parses it, checks it re-encodes to the same picture, and checks
it is a warehouse the rules can be played on - a solid border, as many crates as
goals, nothing standing in a wall and something still left to do.

**Random** builds a warehouse on the watch instead, and it is never scrambled at
random: most random Sokoban positions are dead on arrival, because a crate pushed
into a corner can never come out. The generator builds a *solved* warehouse and
walks the game backwards, **pulling** crates off their goals. A pull is the exact
inverse of a push, so replaying the pulls in reverse is a solution by construction -
and that solution is handed back with the warehouse as a certificate. The unit tests
replay it through the real rule set, which is what turns "should be solvable" into
"is".

Building one is a search, so it runs on a background dispatcher a round at a time
with a bar on screen; a pause on the main thread would be a frozen watch rather than
a thinking one.

The **solver** is here too, in the test source set rather than the app: an optimal
breadth-first search over pushes, with the keeper normalised to the region it can
reach and crates frozen in corners pruned. Nothing on the watch ever solves a
warehouse - that is the player's job - but it is what the tests use to confirm the
shipped collection is not trivially easy, a warehouse that falls over in four pushes
not being worth playing however big it is.

## Devices

Round watches, **Wear OS 3 (API 30) and newer**. Built and tested against a
**OnePlus Watch 2R** (466x466 round, Wear OS 5).

## Setup

```bash
git clone https://github.com/dchernykh1984/WearOSSokoban.git
cd WearOSSokoban
```

A JDK 17 and the Android SDK (compileSdk 36) are all that is needed; Gradle comes
with the repository through the wrapper. Point the build at your SDK with a
`local.properties` holding `sdk.dir=/path/to/Android/sdk`, or export `ANDROID_HOME`.

## Develop

```bash
./gradlew testDebugUnitTest   # the JVM unit tests
./gradlew koverVerify         # unit tests + the coverage floor
./gradlew ktlintCheck         # formatting
./gradlew detekt              # static analysis
./gradlew lintDebug           # Android Lint, including the Wear OS checks
./gradlew assembleDebug       # build the APK
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a watch or emulator)
./gradlew installDebug        # install on a watch over ADB
```

The whole pull-request gate in one line, which is exactly what CI runs:

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease
```

### Layout of the code

```
wear/
  src/main/AndroidManifest.xml         watch-only, standalone, no permissions
  src/main/assets/levels/              the shipped collection, as it left Zepp OS
  src/main/java/com/dchernykh/sokoban/
    MainActivity.kt                    the single activity
    SokobanViewModel.kt                the state the screen draws
    game/Level.kt                      a warehouse, and the rules of walking one
    game/Direction.kt                  the four ways to step
    game/LevelFormat.kt                the pictures the collection is written as
    game/Walking.kt                    flood fills, paths, and what a run touches
    game/Room.kt                       the room and where the crates have to end up
    game/Generator.kt                  reverse play, and the certificate it leaves
    game/Building.kt                   building one a round at a time, with a bar
    game/Played.kt                     dealing without repeating a warehouse
    game/Save.kt                       the position, so a big one can be left
    game/Scores.kt                     the fewest-moves record
    game/Size.kt                       the six sizes and the two sources
    game/Mulberry32.kt                 the Zepp OS generator's RNG, digit for digit
    layout/BoardWindow.kt              the square window a round screen leaves
    layout/Camera.kt                   which part of the warehouse is on screen
    layout/Controls.kt                 where the arrows sit, and what a tap hit
    layout/RoundGeometry.kt            chord maths that keeps content off the bezel
    store/LevelSource.kt               the collection, read from the APK's assets
    store/ProgressStore.kt             progress, on Preferences DataStore
    ui/                                the Compose screens
  src/main/res/values*/strings.xml     the screen strings, a table per language
  src/test/                            JVM unit tests
    game/Solver.kt                     the fewest pushes a warehouse can be done in
    game/Quality.kt                    whether a warehouse is a good puzzle
  src/androidTest/                     instrumented tests - what needs a device
tools/make-launcher-icons.sh           regenerates the icon from the Zepp OS one
config/detekt/detekt.yml               static-analysis overrides
gradle/libs.versions.toml              every dependency and plugin version
```

The rule that shapes it: anything a test can reach without a device - the rules, the
solver, the generator, the level format, the save format, the geometry, the camera -
is a plain Kotlin class outside the Compose layer, and `koverVerify` holds it to a
floor of 80 (the suite sits at 99). Only what genuinely needs a device is exempt,
and each exemption is written down where it is made, with the instrumented test that
covers it instead.

## Pre-commit hooks (contributors)

```bash
uv tool install pre-commit   # or: pipx install pre-commit
pre-commit install
pre-commit install --hook-type commit-msg --hook-type pre-push
```

On commit: whitespace and line endings, YAML/TOML/XML well-formedness, a non-ASCII
guard on source and config (translations in `res/values-*/` are exempt - that is
what they are for), and a check that apostrophes in string resources are escaped,
which is an aapt2 error rather than a warning. On the commit message: Conventional
Commits. On push: ktlint, detekt and the unit tests.

## Continuous integration and releases

Every pull request must pass: pre-commit, `actionlint`, commitizen, the Gradle gate
above, a CodeQL analysis, an OSV dependency scan and the instrumented tests on two
Wear OS emulators.

Releases are automated with `release-please`: it maintains a version-bump PR from
the Conventional Commits and, when merged, tags a GitHub Release. The release build
then produces a **signed APK**, verifies its signature, records a build-provenance
attestation and attaches the APK and its R8 mapping file to the release.

Verify a published APK came from this repository:

```bash
gh attestation verify wearos-sokoban-<version>.apk --repo dchernykh1984/WearOSSokoban
```

### Dependency locking

`wear/gradle.lockfile` pins every transitive version. After changing a dependency,
regenerate it with the **Update lockfiles** workflow (or
`./gradlew :wear:dependencies --write-locks`) and commit the result.

## License

Released under the [MIT License](LICENSE).
