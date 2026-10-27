# SimpleAstroids

The classic Asteroids as a 3D first-person game, built on
[EngineNamedScratch](https://github.com/ad0bert/EngineNamedScratch). You fly a ship through a cube of space that wraps
around on every side. Big rocks split in two when shot, small ones shatter, and now and then a red enemy ship shows up
and shoots at you.

The graphics are kept as simple as possible: low-poly rocks, glowing shots, a star field, flat colours. Everything
(meshes, sounds) is generated in code; the repository has no asset files.

## Play

| Control | Keyboard and mouse | Gamepad |
|---|---|---|
| Look | mouse | right stick |
| Thrust forward / back | `W` / `S` (or arrows) | left stick |
| Slide left / right | `A` / `D` | left stick |
| Rise / sink | `Space` / `Shift` | |
| Fire | left click or `Ctrl` (hold for rapid fire) | `RB`, `RT` or `A` |
| Pause | `P` (or `Esc` to free the mouse) | `Start` |
| Options | `O` on the title screen or in the pause menu | |
| Fullscreen | `F11` | |
| Debug text | `F3` | |

The ship keeps drifting when you let go of thrust and slowly coasts to a stop.

The options panel (`O`) has mouse sensitivity, inverted mouse Y, volume, effects volume, fullscreen, vsync and the
radar. Changes take effect at once and are saved in your user config directory.

### Rules

- **Asteroids:** a big asteroid (20 points) splits into two small ones when hit, and a small one (50 points) is
  destroyed. Clear all rocks to start the next wave, which has one more big rock (up to 12).
- **Enemy ship:** it appears every 20–35 seconds, wanders, and fires at you about every 1.4 seconds. Its shots are
  slower than yours and a little inaccurate, so keep moving. Each hit is worth 100 points, and the third hit destroys it
  for 1000. It leaves after 25 seconds. A red marker on screen shows where it is.
- **Lives:** you have 3 ships. Touching a rock or the enemy ship, or being hit by its shot, costs one. The next ship
  appears in the middle after 2 seconds, shielded for 3 seconds. The best score is kept in your user data directory.
- **Finding things:** the radar in the bottom right shows every rock and the enemy ship around you, seen from above
  with your view pointing up. A line from each blip shows whether it is above or below you. When only three rocks are
  left, each gets a marker on screen, or an arrow at the edge when it's behind you.
- **Wrap-around:** the arena wraps around: what leaves on one side comes back on the other. Everything is drawn where
  it is nearest to you, so you never see a wall; space just goes on.

## Build and run

You need JDK 25 and Maven. The game depends on the engine, `io.github.ad0bert:engine-named-scratch` (version in
`pom.xml`). Get it in one of two ways:

**Build the engine yourself (no credentials needed).** This is what CI does:

```bash
git clone --branch v0.8.1 https://github.com/ad0bert/EngineNamedScratch.git
cd EngineNamedScratch
mvn -q versions:set -DnewVersion=0.8.1 -DgenerateBackupPoms=false
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

## Code

| | |
|---|---|
| `astroids.model` | The rules, without rendering: `World` (arena, asteroids, bullets, boss, ship, waves, score) steps by a fixed time step and reports `GameEvent`s. Deterministic for a seed, and unit-tested. |
| `WorldView` | Mirrors a `World` with engine entities, drawn at their nearest wrap-around copy; turns events into debris and sounds. |
| `TitleState`, `PlayState`, `PauseState`, `GameOverState` | The screens, as engine game states. `TitleState` and `PlayState` are `LevelState`s that own the scene. |
| `Art`, `Sounds` | Meshes, materials and synthesized sounds, made once at start-up. |
| `Hud`, `Radar` | Text, crosshair, on-screen markers for the enemy ship and the last rocks, and the radar. |
| `Options`, `SettingsState` | The player's options (saved with the engine's `Settings`) and the panel that edits them (engine `Gui`). |
