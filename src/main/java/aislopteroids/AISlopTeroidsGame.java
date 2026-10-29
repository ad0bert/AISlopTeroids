package aislopteroids;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.newt.event.KeyEvent;

import aislopteroids.model.Difficulty;
import aislopteroids.model.Tuning;
import engine.core.Engine;
import engine.core.Game;
import engine.core.Settings;
import engine.input.Gamepad;

/**
 * AISlopTeroids: the classic Asteroids as a 3D first-person game. Sets up what every screen shares (art, sounds,
 * controls, options, the score tables) and starts on the title screen; the screens are {@link engine.core.GameState}s.
 */
public final class AISlopTeroidsGame implements Game {
  private static final Logger log = LoggerFactory.getLogger(AISlopTeroidsGame.class);
  private Art art;
  private Settings scores;
  private Settings settings;
  private Options options;
  private Path tuningFile;
  private Tuning.Loaded tuning = new Tuning.Loaded(new Tuning(), 0, java.util.List.of());

  @Override
  public void init(Engine engine) {
    this.art = new Art(engine);
    final var input = engine.input();
    // thrust uses the engine's defaults for forward and back: W / S and the up and down arrows (and the left stick)
    input.bind("fire", KeyEvent.VK_CONTROL, KeyEvent.VK_F);
    input.bindGamepad("fire", Gamepad.BUTTON_RIGHT_BUMPER, Gamepad.BUTTON_A);
    input.bindGamepadAxis("fire", Gamepad.AXIS_RIGHT_TRIGGER, 0.5f);
    input.bind("pause", KeyEvent.VK_P);
    input.bindGamepad("pause", Gamepad.BUTTON_START);
    input.bind("start", KeyEvent.VK_ENTER);
    input.bindGamepad("start", Gamepad.BUTTON_START, Gamepad.BUTTON_A);
    input.bind("fullscreen", KeyEvent.VK_F11);
    input.bind("options", KeyEvent.VK_O);
    input.bind("hyperspace", KeyEvent.VK_E);
    input.bind("view", KeyEvent.VK_V); // cockpit, no cockpit, behind the ship
    input.bindGamepad("view", Gamepad.BUTTON_Y);
    input.bindGamepad("hyperspace", Gamepad.BUTTON_B, Gamepad.BUTTON_LEFT_BUMPER);
    input.bind("previous", KeyEvent.VK_LEFT, KeyEvent.VK_A);
    input.bindGamepad("previous", Gamepad.BUTTON_DPAD_LEFT);
    input.bind("next", KeyEvent.VK_RIGHT, KeyEvent.VK_D);
    input.bindGamepad("next", Gamepad.BUTTON_DPAD_RIGHT);
    input.bind("quit", KeyEvent.VK_Q); // back to the title screen, and from there out of the game
    input.bindGamepad("quit", Gamepad.BUTTON_BACK);
    input.bind("exit", KeyEvent.VK_ESCAPE, KeyEvent.VK_Q); // on the title screen only: Esc frees the mouse in a game
    OldName.carryOver(engine.userDirs()); // from when it was SimpleAstroids
    this.scores = Settings.load(scoresFile(engine));
    if (HighScores.migrate(this.scores)) {
      saveScores();
    }
    this.settings = Settings.load(settingsFile(engine));
    this.options = Options.load(this.settings, engine.isFullscreen(), engine.isVsync());
    applyOptions(engine);
    this.tuningFile = settingsFile(engine).resolveSibling("tuning.properties");
    try {
      Tuning.writeExample(this.tuningFile.resolveSibling("tuning.example.properties"));
    } catch (IOException e) {
      log.warn("Could not write the tuning example: {}", e.getMessage());
    }
    loadTuning();
    engine.setDebugOverlayVisible(false); // it would cover the HUD; F3 still shows it
    // -Daislopteroids.autopilot=true: straight into a game that flies itself (screenshots, smoke tests)
    engine.states().push(Boolean.getBoolean("aislopteroids.autopilot") ? new PlayState(this) : new TitleState(this));
    if (Boolean.getBoolean("aislopteroids.showOptions")) { // -Daislopteroids.showOptions=true: the options panel on top (screenshots)
      engine.states().push(new SettingsState(this));
    }
  }

  @Override
  public void update(Engine engine, double dt) {
    if (engine.input().wasActionPressed("fullscreen")) {
      this.options.fullscreen = !this.options.fullscreen;
      engine.setFullscreen(this.options.fullscreen);
      saveOptions();
    }
  }

  Art art() {
    return this.art;
  }

  Options options() {
    return this.options;
  }

  /** Puts the options into effect, the graphics quality too. */
  void applyOptions(Engine engine) {
    this.options.apply(engine);
    this.art.quality = this.options.quality;
  }

  /** Swaps in other options (the defaults); they take effect at the next {@link #applyOptions}. */
  void replaceOptions(Options replacement) {
    this.options = replacement;
  }

  void saveOptions() {
    this.options.store(this.settings);
    try {
      this.settings.save();
    } catch (IOException e) {
      log.warn("Could not save the options to {}: {}", this.settings.file(), e.getMessage());
    }
  }

  /**
   * Reads {@code tuning.properties} from the config directory (again: every new game picks up edits) and logs what in
   * it could not be used.
   */
  Tuning.Loaded loadTuning() {
    final var difficulty = this.options.difficulty;
    try {
      this.tuning = Tuning.load(this.tuningFile, difficulty);
    } catch (IOException | IllegalArgumentException e) {
      log.warn("Could not read {}: {}; playing with the defaults", this.tuningFile, e.getMessage());
      this.tuning = new Tuning.Loaded(Tuning.of(difficulty), 0,
          java.util.List.of("could not read the file: " + e.getMessage()));
    }
    if (this.tuning.custom()) {
      log.info("Custom tuning from {}: {} values changed", this.tuningFile, this.tuning.overrides());
    }
    this.tuning.problems().forEach(p -> log.warn("{}: {}", this.tuningFile.getFileName(), p));
    return this.tuning;
  }

  /** The tuning last loaded (at start, or for the latest game). */
  Tuning.Loaded tuning() {
    return this.tuning;
  }

  Path tuningFile() {
    return this.tuningFile;
  }

  /** The ten best games on a difficulty. */
  HighScores highScores(Difficulty difficulty) {
    return HighScores.load(this.scores, difficulty);
  }

  /** Puts a game in its difficulty's table and saves it; returns its rank (0 = top), or -1. */
  int recordScore(Difficulty difficulty, String name, int score, int wave) {
    final var table = highScores(difficulty);
    final int rank = table.add(name, score, wave);
    if (rank >= 0) {
      table.store(this.scores, difficulty);
      this.scores.set("lastName", HighScores.cleanName(name));
      saveScores();
    }
    return rank;
  }

  /** The name last put in a table: the default for the next one. */
  String lastName() {
    return this.scores.get("lastName", "");
  }

  private void saveScores() {
    try {
      this.scores.save();
    } catch (IOException e) {
      log.warn("Could not save the scores to {}: {}", this.scores.file(), e.getMessage());
    }
  }

  private static Path settingsFile(Engine engine) {
    try {
      return engine.userDirs().config().resolve("settings.properties");
    } catch (IOException e) {
      log.warn("No config directory ({}); the options are kept next to the game", e.getMessage());
      return Path.of("ai-slop-teroids-settings.properties");
    }
  }

  private static Path scoresFile(Engine engine) {
    try {
      return engine.userDirs().data().resolve("scores.properties");
    } catch (IOException e) {
      log.warn("No data directory ({}); the scores are kept next to the game", e.getMessage());
      return Path.of("ai-slop-teroids-scores.properties");
    }
  }
}
