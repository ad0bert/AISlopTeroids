package astroids;

import engine.core.Engine;
import engine.core.GameState;
import engine.render.UiCanvas;

/**
 * The game stands still under a dimmed screen. Opened with P (or Start), or when the mouse is let go (Esc, or the
 * window losing focus); P again or a click goes on.
 */
final class PauseState implements GameState {
  private final AstroidsGame game;
  private boolean capturedAtStart;

  PauseState(AstroidsGame game) {
    this.game = game;
  }

  @Override
  public void enter(Engine engine) {
    this.capturedAtStart = engine.isMouseCaptured();
    engine.audio().mixer().setMuted(true);
  }

  @Override
  public void exit(Engine engine) {
    engine.audio().mixer().setMuted(false);
  }

  @Override
  public void update(Engine engine, double dt) {
    if (engine.input().wasActionPressed("options")) {
      engine.states().push(new SettingsState(this.game));
      return;
    }
    // a click into the window captures the mouse again: that goes on too
    if (engine.input().wasActionPressed("pause") || (!this.capturedAtStart && engine.isMouseCaptured())) {
      engine.captureMouse(true);
      engine.states().pop();
    }
  }

  @Override
  public boolean simulates() {
    return false;
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    Hud.dim(ui, width, height, 0.5f);
    Hud.centered(ui, "PAUSED", width, height * 0.4f, 3f, 1f, 1f, 1f, 1f);
    Hud.centered(ui, "P or click to continue      O  options", width, height * 0.4f + ui.lineHeight(3f) + 8f, 1.3f,
        0.85f, 0.85f, 0.85f, 1f);
  }
}
