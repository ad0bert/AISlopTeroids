# AISlopTeroids

The classic Asteroids as a 3D first-person game, built on
[EngineNamedScratch](https://github.com/ad0bert/EngineNamedScratch). You fly a ship through a cube of space that wraps
around on every side. Big rocks split in two when shot, small ones shatter, and now and then an enemy ship shows up
and shoots at you. Pick Easy, Normal or Hard on the title screen; each keeps its own table of the ten best games.

The graphics are kept simple: lumpy, cratered rocks (twelve different shapes) with a rock texture and a bump map,
worn, polished enemy ships that reflect the space around them and blow up in fire, smoke and wreckage, glowing
shots, a star field. Everything (meshes, textures, sounds) is generated in code; the repository
has no asset files.

## Play

| Control | Keyboard and mouse | Gamepad |
|---|---|---|
| Steer | mouse | right stick |
| Thrust / brake | `W` / `S` (or arrows up / down) | left stick up / down |
| Fire | left click or `Ctrl` (hold for rapid fire) | `RB`, `RT` or `A` |
| Hyperspace | `E` | `B` or `LB` |
| View (cockpit, no cockpit, behind the ship) | `V` | `Y` |
| Pause | `P` (or `Esc` to free the mouse) | `Start` |
| Quit to the title screen | `Q` in the pause menu or after the game | `Back` |
| Quit the game | `Q` or `Esc` on the title screen | `Back` on the title screen |
| Difficulty | `Left` / `Right` on the title screen | d-pad |
| Options | `O` on the title screen or in the pause menu | |
| Fullscreen | `F11` | |
| Debug text (frame times too) | `F3` | |

The ship's engine pushes only ahead, so to fly somewhere you turn the ship towards it. Once up to speed it keeps
drifting for a long while when you let go of thrust; brake with `S` to stop. The ship turns after the mouse with a
little inertia.

The options panel (`O`) has mouse sensitivity, inverted mouse Y, gamepad rumble, volume, effects volume, ambience
volume, fullscreen, vsync, graphics quality, the radar and the view. Changes take effect at once and are saved in your user config
directory.

There are three views; `V` switches between them in the game:

- **Cockpit:** you look out through a bubble of clear glass that catches the light towards its edges, with a low
  dashboard round the bottom and the frame's arch across the top corners, thin teal lines glowing along them. Your
  speed (digits over a bar) and the hyperspace charge (a bar that pulses when the jump is ready) are lit on the glass
  above the dashboard, with a dashed ring round the crosshair. Your shots rise from the guns below the dashboard and
  close in on the crosshair.
- **No cockpit:** the same seat with a clear view; speed and hyperspace are at the bottom left.
- **Behind the ship:** you see your whole ship: a long teal fuselage with an engine at its tail that flames while you
  thrust, four chrome wings spread in an X, and a steering thruster at each wingtip that puffs gas while the ship
  turns (the left wing's turning right, the lower ones turning up). Its shots leave the guns at the four wingtips in
  turn. The crosshair is where the shots go.

When your ship is hit, the view pulls back and you watch it break up; the next ship brings you back to your view.

Graphics quality (Low, Medium, High) is for slower computers: Medium blends see-through things more simply and makes
fewer pieces, specks and sparks in explosions; Low also turns off the glow (bloom), edge smoothing, the ambient
occlusion (the shade in the rocks' craters and creases) and the light of explosions, and makes fewer pieces still. The
reflections on the ships stay at every level.

On a gamepad whose motors can be driven, the pad rumbles when your ship blows up, when you jump through hyperspace, when
rocks and enemy ships blow up near you (less the further away they are) and lightly with every shot. Turn it off in
the options.

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
   - Linux: `~/.config/ai-slop-teroids`
   - macOS: `~/Library/Application Support/ai-slop-teroids`
   - Windows: `%APPDATA%\ai-slop-teroids`
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
git clone --branch v0.9.0 https://github.com/ad0bert/EngineNamedScratch.git
cd EngineNamedScratch
mvn -q versions:set -DnewVersion=0.9.0 -DgenerateBackupPoms=false
mvn -q install -DskipTests -pl engine -am
```

**Or from GitHub Packages.** Add a `github` server to `~/.m2/settings.xml` with your GitHub user name and a token that
has `read:packages`, then build with `-Pgithub-packages`.

Then:

```bash
mvn package                              # tests, then target/dist/ai-slop-teroids.jar (+ lib/)
java -jar target/dist/ai-slop-teroids.jar
mvn package -Pjpackage                   # also target/jpackage: the game with its own Java runtime
```

`-Daislopteroids.autopilot=true` starts straight in a game that flies and shoots by itself, and `-Daislopteroids.showOptions=true`
opens the options panel at start. `-Daislopteroids.view=COCKPIT`, `CLEAR` or `CHASE` starts the game in that view. All are
handy for screenshots.
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
| `aislopteroids.model` | The rules, without rendering: `World` (arena, asteroids, bullets, enemy ships, ship, waves, score) steps by a fixed time step and reports `GameEvent`s, with its numbers from a `Tuning` (a `Difficulty`'s values, plus `tuning.properties`). Deterministic for a seed and a tuning, and unit-tested. |
| `WorldView` | Mirrors a `World` with engine entities, drawn at their nearest wrap-around copy; turns events into debris and sounds. |
| `TitleState`, `PlayState`, `PauseState`, `GameOverState` | The screens, as engine game states. `TitleState` and `PlayState` are `LevelState`s that own the scene. `GameOverState` takes the name for the score table. |
| `RockArt` | The rocks' shapes (a sphere pushed in and out by random lumps and craters, one per seed) and their surface: a seamless height field turned into a colour texture and a bump map. Also the sharp-edged chips and the dust cloud a rock breaks into (`Debris`, `Dust`). |
| `ShipArt` | The ships' parts, turned on a lathe from profiles (the enemies' hulls, rim band, domes, engine plate, the player's fuselage), and the space around everything as a sky image (a sun, a planet's blue shine, a faint nebula) for the polished hulls to reflect. Also the hulls' wear (plates, rivets, grime, scratches) and the torn pieces of hull a ship blows up into (with `Blast` for its fire and `Flash` for its light). |
| `CockpitArt`, `GlassDisplay` | The cockpit seen from inside: the glass canopy (an ellipsoid), the arch of its frame, the dashboard and the glowing lines along them; and the readouts lit on its glass (seven-segment speed, the hyperspace bar, the ring round the crosshair). |
| `Art`, `Sounds` | Meshes, materials and synthesized sounds (effects and seamless ambience loops), made once at start-up. |
| `Hud`, `Radar` | Text, crosshair, on-screen markers for the enemy ship and the last rocks, score tables, and the radar. |
| `HighScores` | The ten best games of each difficulty, kept in the scores file. |
| `Quality` | The graphics quality levels: what each switches in the renderer, and how much of the bursts it keeps. |
| `View` | The three views: the cockpit, no cockpit, and behind the ship. |
| `Options`, `SettingsState` | The player's options (saved with the engine's `Settings`) and the panel that edits them (engine `Gui`). |
