package astroids;

import engine.core.Engine;
import engine.core.GameState;
import engine.render.UiCanvas;
import engine.ui.Gui;

/**
 * The options panel, opened with O from the title screen or the pause menu: mouse sensitivity, inverted look, volumes,
 * fullscreen, vsync and the radar. Changes take effect at once and are saved when the panel closes (Done, or O again).
 */
final class SettingsState implements GameState {
  private static final float PANEL_WIDTH = 380f;

  private final AstroidsGame game;
  private Gui gui;
  private boolean done;

  SettingsState(AstroidsGame game) {
    this.game = game;
  }

  @Override
  public void enter(Engine engine) {
    this.gui = new Gui(engine.input());
    engine.captureMouse(false);
    engine.setClickCapturesMouse(() -> false); // clicks belong to the panel
  }

  @Override
  public void exit(Engine engine) {
    engine.setClickCapturesMouse(() -> true);
    this.game.saveOptions();
  }

  @Override
  public void update(Engine engine, double dt) {
    if (this.done || engine.input().wasActionPressed("options")) {
      engine.states().pop();
    }
  }

  @Override
  public boolean simulates() {
    return false;
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    Hud.dim(ui, width, height, 0.55f);
    final var o = this.game.options();
    final var g = this.gui;
    g.begin(ui, width, height, true);
    g.beginPanel("Options", (width - PANEL_WIDTH) / 2f, height * 0.18f, PANEL_WIDTH);
    g.label("Controls");
    o.mouseSensitivity = g.slider("Mouse sensitivity", o.mouseSensitivity, Options.MIN_SENSITIVITY,
        Options.MAX_SENSITIVITY);
    o.invertY = g.checkbox("Invert mouse Y", o.invertY);
    g.space(6f);
    g.label("Sound");
    o.masterVolume = g.slider("Volume", o.masterVolume, 0f, 1f);
    o.effectsVolume = g.slider("Effects", o.effectsVolume, 0f, 1f);
    o.ambienceVolume = g.slider("Ambience", o.ambienceVolume, 0f, 1f);
    g.space(6f);
    g.label("Screen");
    g.row(2);
    o.fullscreen = g.checkbox("Fullscreen", o.fullscreen);
    o.vsync = g.checkbox("VSync", o.vsync);
    o.radar = g.checkbox("Show the radar", o.radar);
    g.space(10f);
    g.row(2);
    if (g.button("Defaults")) {
      final var defaults = new Options();
      defaults.fullscreen = o.fullscreen; // the window stays as it is
      defaults.vsync = o.vsync;
      defaults.difficulty = o.difficulty; // picked on the title screen, not here
      this.game.replaceOptions(defaults);
    }
    if (g.button("Done")) {
      this.done = true;
    }
    g.endPanel();
    g.end();
    this.game.options().apply(engine);
    Hud.centered(ui, "O or Done to close", width, height * 0.9f, 1.1f, 0.8f, 0.8f, 0.85f, 1f);
  }
}
