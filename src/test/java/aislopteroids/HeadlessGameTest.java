package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import engine.core.Engine;
import engine.core.EngineConfig;
import engine.debug.DebugOverlayPlugin;
import engine.input.MouseReleasePlugin;

/**
 * The whole game on the engine without a window: title screen, a game flying itself, the plugins it chose. Faster than
 * real time, so a minute of play takes a few seconds; the smoke run in CI still checks what is drawn.
 */
class HeadlessGameTest {
  private static final int STEPS_PER_SECOND = 60;

  @TempDir
  Path userDir;
  private Engine engine;

  @BeforeEach
  void ownUserDirectory() {
    System.setProperty("engine.userDir", this.userDir.toString());
  }

  @AfterEach
  void stop() {
    if (this.engine != null) {
      this.engine.stop();
    }
    System.clearProperty("engine.userDir");
    System.clearProperty("aislopteroids.autopilot");
  }

  private Engine start() {
    this.engine = new Engine(EngineConfig.load("application.properties"), new AISlopTeroidsGame());
    this.engine.startHeadless();
    return this.engine;
  }

  @Test
  void onlyTheEnginePluginsTheGameUses() {
    final var e = start();
    assertTrue(e.plugin(MouseReleasePlugin.class).isPresent());
    final var debug = e.plugin(DebugOverlayPlugin.class);
    assertTrue(debug.isPresent());
    assertFalse(debug.get().isVisible(), "the debug text would cover the HUD");
    assertFalse(e.hasPlugin("engine.first-person-input"), "the game binds its own controls");
    assertFalse(e.hasPlugin("engine.impact-sounds"), "the game has no rigid bodies to sound");
    assertTrue(e.input().actions().contains("forward") && e.input().actions().contains("back"));
  }

  @Test
  void theTitleScreenRuns() {
    final var e = start();
    e.runTicks(10 * STEPS_PER_SECOND);
    assertInstanceOf(TitleState.class, e.states().current());
  }

  @Test
  void aGameThatFliesItselfRunsForAMinute() {
    System.setProperty("aislopteroids.autopilot", "true");
    final var e = start();
    e.runTicks(60 * STEPS_PER_SECOND);
    final var state = e.states().current();
    assertTrue(state instanceof PlayState || state instanceof GameOverState, "still playing, or game over: " + state);
  }
}
