# SimpleAstroids

The classic Asteroids as a 3D first-person game, built on
[EngineNamedScratch](https://github.com/ad0bert/EngineNamedScratch). You fly a ship through a cube of space that wraps
around on every side. Big rocks split in two when shot, small ones shatter, and now and then an enemy ship shows up
and shoots at you. Pick Easy, Normal or Hard on the title screen; each keeps its own table of the ten best games.

The graphics are kept simple: lumpy, cratered rocks (twelve different shapes) with a rock texture and a bump map,
glowing shots, a star field, flat colours. Everything (meshes, textures, sounds) is generated in code; the repository
has no asset files.

## Play

| Control | Keyboard and mouse | Gamepad |
|---|---|---|
| Look | mouse | right stick |
| Thrust forward / back | `W` / `S` (or arrows) | left stick |
| Slide left / right | `A` / `D` | left stick |
| Rise / sink | `Space` / `Shift` | |
| Fire | left click or `Ctrl` (hold for rapid fire) | `RB`, `RT` or `A` |
| Hyperspace | `E` | `B` or `LB` |
| Pause | `P` (or `Esc` to free the mouse) | `Start` |
| Quit to the title screen | `Q` in the pause menu or after the game | `Back` |
| Quit the game | `Q` or `Esc` on the title screen | `Back` on the title screen |
| Difficulty | `Left` / `Right` on the title screen | d-pad |
| Options | `O` on the title screen or in the pause menu | |
| Fullscreen | `F11` | |
| Debug text | `F3` | |

The ship keeps drifting when you let go of thrust and slowly coasts to a stop.

The options panel (`O`) has mouse sensitivity, inverted mouse Y, volume, effects volume, ambience volume, fullscreen,
vsync and the radar. Changes take effect at once and are saved in your user config directory.

Ambience: the title screen has a warm, breathing pad with a soft, low synth arpeggio inside it. In the game, a darker drone plays under the engine hum, which rises
with your speed, and a low throb swells while an enemy ship is around.

### Rules

The numbers below are for Normal.

- **Asteroids:** a big asteroid (20 points) splits into two small ones when hit, and a small one (50 points) is
  destroyed. The first wave has 10 big rocks, and clearing a wave starts the next one with one more.
- **Enemy ship:** it appears every 20–35 seconds, a little sooner with every wave (down to 40% of that), darts
  around, and fires at you twice a second. Its shots are slower than yours but accurate, so keep moving. Each hit is
  worth 100 points, and the third hit destroys it for 1000. It leaves after 25 seconds. A red marker on screen shows
  where it is.
- **Scout:** from wave 5 on, about half the enemy ships are scouts instead: small, yellow and quick. It turns often, aims
  worse and fires less, and half the time it sees your shot coming and swerves. One hit destroys it, for 1500 points.
- **Lives:** you have 3 ships, and every 10,000 points bring another. Touching a rock or the enemy ship, or being hit
  by its shot, costs one. The next ship appears in the middle after 2 seconds. Every ship, the first one too, is
  shielded for 3 seconds.
- **Hyperspace:** `E` jumps your ship to a random place in the arena and stops it. It gets you out of a tight spot,
  but you might come out somewhere worse, and it needs 8 seconds before the next jump.
- **Difficulty:** Easy has fewer and slower rocks, a slower enemy that aims worse and comes less often, scouts only from
  wave 7, and an extra ship every 7,500 points. Hard has more and faster rocks, an enemy that is quicker, sharper, takes
  four hits and comes more often, scouts from wave 3, a 2-second shield, and an extra ship every 15,000 points.
- **Best scores:** a game that makes the ten best of its difficulty asks for your name (type it, then Enter). The
  tables are on the title screen, in turn with the controls, and are kept in your user data directory.
- **Finding things:** the radar in the bottom right shows every rock and the enemy ship around you, seen from above
  with your view pointing up. A line from each blip shows whether it is above or below you. When only three rocks are
  left, each gets a marker on screen, or an arrow at the edge when it's behind you.
- **Wrap-around:** the arena wraps around: what leaves on one side comes back on the other. Everything is drawn where
  it is nearest to you, so you never see a wall; space just goes on.

### Tuning (playtesting)

The numbers in the rules above are defaults. Speeds, timings, wave sizes, the enemy ships, scores, extra ships,
hyperspace and the arena size can all be changed without rebuilding:

1. Start the game once. It writes `tuning.example.properties` into your config directory, listing every value with its
   default (Normal's, with Easy's and Hard's in brackets where they differ) and what it does:
   - Linux: `~/.config/simple-astroids`
   - macOS: `~/Library/Application Support/simple-astroids`
   - Windows: `%APPDATA%\simple-astroids`
2. Copy it to `tuning.properties` in the same folder, remove the `#` in front of the lines you want to change, and edit
   the values. For example, `ship.maxSpeed = 35` or `enemy.fireInterval = 2.5`.
3. Start a new game. The file is read again for every game, so restarting the program isn't necessary.

Values in the file apply on top of the chosen difficulty, whichever it is. A value at its Normal default changes
nothing, on any difficulty: Easy and Hard keep their own. While values differ from the difficulty's, the game shows
"CUSTOM TUNING" and doesn't record best scores. The title screen shows the first unknown key or unusable
value, which keeps its default. Delete the file to play as released.

## Build and run

You need JDK 25 and Maven. The game depends on the engine, `io.github.ad0bert:engine-named-scratch` (version in
`pom.xml`). Get it in one of two ways:

**Build the engine yourself (no credentials needed).** This is what CI does:

```bash
git clone --branch v0.8.2 https://github.com/ad0bert/EngineNamedScratch.git
cd EngineNamedScratch
mvn -q versions:set -DnewVersion=0.8.2 -DgenerateBackupPoms=false
mvn -q install -DskipTests -pl engine -am
```

**Or from GitHub Packages.** Add a `github` server to `~/.m2/settings.xml` with your GitHub user name and a token that
has `read:packages`, then build with `-Pgithub-packages`.

Then:

```bash
mvn package                              # tests, then target/dist/simple-astroids.jar (+ lib/)
java -jar target/dist/simple-astroids.jar
mvn package -Pjpackage                   # also target/jpackage: the game with its own Java runtime
```

`-Dastroids.autopilot=true` starts straight in a game that flies and shoots by itself, and `-Dastroids.showOptions=true`
opens the options panel at start. Both are handy for screenshots.
The engine's system properties (`engine.fullscreen`, `engine.vsync`, `engine.screenshot`, ...) work too. CI runs the
title screen and an autopilot game under Xvfb and keeps the screenshots as an artifact.

## Releases

Push a tag like `v0.1.0` to release that version (`.github/workflows/release.yml`). The workflow builds the game with its
own Java runtime on Linux, macOS and Windows, plus a runnable jar, and attaches them all to a GitHub release once every
build has succeeded. The release's title and description come from `release-notes/<tag>.md`; write that file before
tagging. Actions → release → Run workflow, with a tag, publishes an existing tag again.

## Code

| | |
|---|---|
| `astroids.model` | The rules, without rendering: `World` (arena, asteroids, bullets, enemy ships, ship, waves, score) steps by a fixed time step and reports `GameEvent`s, with its numbers from a `Tuning` (a `Difficulty`'s values, plus `tuning.properties`). Deterministic for a seed and a tuning, and unit-tested. |
| `WorldView` | Mirrors a `World` with engine entities, drawn at their nearest wrap-around copy; turns events into debris and sounds. |
| `TitleState`, `PlayState`, `PauseState`, `GameOverState` | The screens, as engine game states. `TitleState` and `PlayState` are `LevelState`s that own the scene. `GameOverState` takes the name for the score table. |
| `RockArt` | The rocks' shapes (a sphere pushed in and out by random lumps and craters, one per seed) and their surface: a seamless height field turned into a colour texture and a bump map. |
| `Art`, `Sounds` | Meshes, materials and synthesized sounds (effects and seamless ambience loops), made once at start-up. |
| `Hud`, `Radar` | Text, crosshair, on-screen markers for the enemy ship and the last rocks, score tables, and the radar. |
| `HighScores` | The ten best games of each difficulty, kept in the scores file. |
| `Options`, `SettingsState` | The player's options (saved with the engine's `Settings`) and the panel that edits them (engine `Gui`). |
