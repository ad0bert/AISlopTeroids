package aislopteroids;

import engine.core.Engine;
import engine.core.GameState;
import engine.render.UiCanvas;

/**
 * The game stands still under a dimmed screen. Opened with P (or Start), or when the mouse is let go (Esc, or the
 * window losing focus); P, Esc or a click go on, Q gives the game up and goes back to the title screen.
 */
final class PauseState implements GameState {
  private final AISlopTeroidsGame game;
  private Clicks clicks;

  PauseState(AISlopTeroidsGame game) {
    this.game = game;
  }

  @Override
  public void enter(Engine engine) {
    this.clicks = new Clicks(engine.input());
    engine.audio().mixer().setMuted(true);
    this.game.rumble().stop();
  }

  @Override
  public void revealed(Engine engine) {
    this.clicks = new Clicks(engine.input()); // clicks in the options panel must not end the pause
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
    if (engine.input().wasActionPressed("quit")) {
      engine.captureMouse(false);
      engine.states().clear();
      engine.states().push(new TitleState(this.game));
      return;
    }
    // P, Esc or a click go on, whether the mouse was let go (Esc, focus lost) or is still held (P)
    if (engine.input().wasActionPressed("pause") || engine.input().wasActionPressed("exit") || this.clicks.clicked()) {
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
    Hud.screen(width, height);
    Hud.dim(ui, width, height, 0.5f);
    Hud.centered(ui, "PAUSED", width, height * 0.4f, 3f, 1f, 1f, 1f, 1f);
    Hud.centered(ui, "P, Esc or click to continue      O  options      Q  quit to the title screen", width,
        height * 0.4f + Hud.lineHeight(ui, 3f) + 8f, 1.3f, 0.85f, 0.85f, 0.85f, 1f);
  }
}
