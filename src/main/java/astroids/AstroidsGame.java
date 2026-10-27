package astroids;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.newt.event.KeyEvent;

import engine.core.Engine;
import engine.core.Game;
import engine.core.Settings;
import engine.input.Gamepad;

/**
 * SimpleAstroids: the classic Asteroids as a 3D first-person game. Sets up what every screen shares (art, sounds,
 * controls, the high score) and starts on the title screen; the screens are {@link engine.core.GameState}s.
 */
public final class AstroidsGame implements Game {
  private static final Logger log = LoggerFactory.getLogger(AstroidsGame.class);
  private Art art;
  private Settings scores;

  @Override
  public void init(Engine engine) {
    this.art = new Art(engine);
    final var input = engine.input();
    // movement uses the engine's defaults: WASD / arrows, Space up, Shift down (and the gamepad's sticks)
    input.bind("fire", KeyEvent.VK_CONTROL, KeyEvent.VK_F);
    input.bindGamepad("fire", Gamepad.BUTTON_RIGHT_BUMPER, Gamepad.BUTTON_A);
    input.bindGamepadAxis("fire", Gamepad.AXIS_RIGHT_TRIGGER, 0.5f);
    input.bind("pause", KeyEvent.VK_P);
    input.bindGamepad("pause", Gamepad.BUTTON_START);
    input.bind("start", KeyEvent.VK_ENTER);
    input.bindGamepad("start", Gamepad.BUTTON_START, Gamepad.BUTTON_A);
    input.bind("fullscreen", KeyEvent.VK_F11);
    this.scores = Settings.load(scoresFile(engine));
    engine.setDebugOverlayVisible(false); // it would cover the HUD; F3 still shows it
    // -Dastroids.autopilot=true: straight into a game that flies itself (screenshots, smoke tests)
    engine.states().push(Boolean.getBoolean("astroids.autopilot") ? new PlayState(this) : new TitleState(this));
  }

  @Override
  public void update(Engine engine, double dt) {
    if (engine.input().wasActionPressed("fullscreen")) {
      engine.setFullscreen(!engine.isFullscreen());
    }
  }

  Art art() {
    return this.art;
  }

  int highScore() {
    return this.scores.getInt("highScore", 0);
  }

  /** Remembers a new best score; returns whether it was one. */
  boolean recordScore(int score) {
    if (score <= highScore()) {
      return false;
    }
    this.scores.set("highScore", score);
    try {
      this.scores.save();
    } catch (IOException e) {
      log.warn("Could not save the high score to {}: {}", this.scores.file(), e.getMessage());
    }
    return true;
  }

  private static Path scoresFile(Engine engine) {
    try {
      return engine.userDirs().data().resolve("scores.properties");
    } catch (IOException e) {
      log.warn("No data directory ({}); the high score is kept next to the game", e.getMessage());
      return Path.of("simple-astroids-scores.properties");
    }
  }
}
