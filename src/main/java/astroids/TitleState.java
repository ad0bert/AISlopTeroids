package astroids;

import java.util.Locale;

import org.joml.Vector3f;

import astroids.model.Controls;
import astroids.model.World;
import engine.core.Engine;
import engine.core.LevelState;
import engine.ecs.Entity;
import engine.render.UiCanvas;

/**
 * The title screen: rocks drifting past a slowly turning camera, the name, the difficulty (Left / Right to change it),
 * and in turn the controls and the difficulty's table of best scores. Q or Esc (or Back on a gamepad) quits the game.
 */
final class TitleState extends LevelState {
  /** Seconds each page (controls, scores) stays before the other one. */
  static final float PAGE_SECONDS = 8f;

  private final AstroidsGame game;
  private World world;
  private WorldView view;
  private Entity camera;
  private Clicks clicks;
  private float time;
  private boolean scoresPage;
  private float pageStart;
  private HighScores table;

  TitleState(AstroidsGame game) {
    this.game = game;
  }

  @Override
  protected void build(Engine engine) {
    this.world = World.attract(System.nanoTime(), 10);
    this.view = new WorldView(engine, this.game.art());
    this.camera = this.game.art().addBackdrop(engine.scene());
    this.clicks = new Clicks(engine.input());
    this.view.sync(this.world, new Vector3f(), 0f);
    this.table = this.game.highScores(this.game.options().difficulty);
    this.game.art().addLoop(engine.scene(), "ambience", this.game.art().titleAmbience, 0.8f);
  }

  @Override
  public void revealed(Engine engine) {
    this.clicks = new Clicks(engine.input()); // clicks in the options panel must not start a game
  }

  @Override
  public void update(Engine engine, double dtD) {
    final float dt = (float) dtD;
    this.time += dt;
    this.world.step(dt, Controls.NONE);
    this.view.sync(this.world, this.camera.transform().position(), dt);
    this.camera.transform().rotation().identity().rotateY(this.time * 0.05f).rotateX(0.15f * (float) Math.sin(
        this.time * 0.1f));
    final var input = engine.input();
    final int step = (input.wasActionPressed("next") ? 1 : 0) - (input.wasActionPressed("previous") ? 1 : 0);
    final var options = this.game.options();
    if (step != 0 && options.difficulty.step(step) != options.difficulty) {
      options.difficulty = options.difficulty.step(step);
      this.game.saveOptions();
      this.game.loadTuning(); // what a tuning file changes depends on the difficulty
      this.table = this.game.highScores(options.difficulty);
      this.scoresPage = true; // show what there is to beat
      this.pageStart = this.time;
    } else if (this.time - this.pageStart > PAGE_SECONDS) {
      this.scoresPage = !this.scoresPage;
      this.pageStart = this.time;
    }
    // not in its first moments: the Q that left a game for this screen must not also quit
    if (this.time > 0.3f && (input.wasActionPressed("exit") || input.wasActionPressed("quit"))) {
      engine.stop(); // everything is saved as it changes: the options, the scores
      return;
    }
    if (engine.input().wasActionPressed("options")) {
      engine.states().push(new SettingsState(this.game));
      return;
    }
    final boolean clicked = this.clicks.clicked();
    if (engine.input().wasActionPressed("start") || clicked) {
      engine.captureMouse(true);
      engine.states().replace(new PlayState(this.game));
    }
  }

  /** The keys, two columns of key and what it does. */
  private static final String[][] CONTROLS = { { "Mouse", "steer" }, { "W / S", "thrust / brake" },
      { "Left click / Ctrl", "fire" }, { "E", "hyperspace" }, { "P", "pause" }, { "O", "options" } };

  /**
   * The controls as a table (keys in yellow, what they do in white), and two lines about the game below it, on a dark
   * backing so the rocks drifting behind do not cut through the letters.
   */
  private void drawControls(UiCanvas ui, int width, float top) {
    final float z = Hud.zoom();
    final float scale = 1.2f;
    final float noteScale = 1.1f;
    final int rows = CONTROLS.length / 2;
    float keyW = 0f;
    float doesW = 0f;
    for (String[] c : CONTROLS) {
      keyW = Math.max(keyW, Hud.width(ui, c[0], scale));
      doesW = Math.max(doesW, Hud.width(ui, c[1], scale));
    }
    final float pad = 16f * z;
    final float colW = keyW + pad + doesW;
    final float gap = 56f * z;
    final String[] notes = { "Big rocks split in two, small ones shatter. Watch out for the red ship and the quick "
        + "yellow scout.", extraShips(this.game.tuning().tuning().extraLifeEvery) };
    float noteW = 0f;
    for (String n : notes) {
      noteW = Math.max(noteW, Hud.width(ui, n, noteScale));
    }
    final float row = Hud.lineHeight(ui, scale) + 5f * z;
    final float noteRow = Hud.lineHeight(ui, noteScale) + 4f * z;
    final float blockW = Math.max(2f * colW + gap, noteW);
    final float blockH = rows * row + 10f * z + notes.length * noteRow;
    Hud.backing(ui, (width - blockW) / 2f - 24f * z, top - 12f * z, blockW + 48f * z, blockH + 22f * z);
    final float left = (width - (2f * colW + gap)) / 2f;
    for (int i = 0; i < CONTROLS.length; i++) {
      final float x = left + (i / rows) * (colW + gap);
      final float y = top + (i % rows) * row;
      final var c = CONTROLS[i];
      Hud.text(ui, c[0], x + keyW - Hud.width(ui, c[0], scale), y, scale, 0.98f, 0.85f, 0.45f, 1f);
      Hud.text(ui, c[1], x + keyW + pad, y, scale, 0.95f, 0.96f, 1f, 1f);
    }
    float y = top + rows * row + 10f * z;
    for (String n : notes) {
      Hud.centered(ui, n, width, y, noteScale, 0.82f, 0.86f, 0.94f, 1f);
      y += noteRow;
    }
  }

  private static String extraShips(int every) {
    return every > 0 ? String.format(Locale.ROOT, "Every %,d points bring an extra ship.", every)
        : "No extra ships with this tuning.";
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    Hud.screen(width, height);
    final float top = height * 0.22f;
    Hud.centered(ui, "SIMPLE ASTROIDS", width, top, 4f, 0.95f, 0.85f, 0.45f, 1f);
    Hud.centered(ui, "Asteroids in three dimensions", width, top + Hud.lineHeight(ui, 4f) + 4f, 1.3f, 0.75f, 0.8f,
        0.9f, 1f);
    final float blink = 0.6f + 0.4f * (float) Math.sin(this.time * 4f);
    Hud.centered(ui, "Press Enter or click to start", width, height * 0.46f, 1.6f, 1f, 1f, 1f, blink);

    final var difficulty = this.game.options().difficulty;
    final float dy = height * 0.46f + Hud.lineHeight(ui, 1.6f) + 14f;
    final var left = difficulty.step(-1) != difficulty ? "<   " : "    ";
    final var right = difficulty.step(1) != difficulty ? "   >" : "    ";
    Hud.centered(ui, left + difficulty.label.toUpperCase(Locale.ROOT) + right, width, dy, 1.6f, 0.6f, 0.85f, 1f, 1f);
    final var best = this.table.entries().isEmpty() ? "Left / Right: difficulty"
        : "Left / Right: difficulty      Best  " + this.table.best() + "  " + this.table.entries().get(0).name();
    Hud.centered(ui, best, width, dy + Hud.lineHeight(ui, 1.6f) + 2f, 1.1f, 0.85f, 0.88f, 0.94f, 1f);

    float y = height * 0.63f;
    final float z = Hud.zoom();
    if (this.scoresPage) {
      final float scale = 1.15f;
      final float gap = 40f * z;
      final var entries = this.table.entries();
      final float tableW = entries.isEmpty() ? Hud.width(ui, "No scores yet: be the first", scale)
          : 2f * Hud.tableWidth(scale) + gap;
      final float rows = entries.isEmpty() ? 1 : Math.min(5, entries.size());
      final float blockH = Hud.lineHeight(ui, 1.25f) + 8f * z + rows * (Hud.lineHeight(ui, scale) + 3f * z);
      Hud.backing(ui, (width - tableW) / 2f - 24f * z, y - 12f * z, tableW + 48f * z, blockH + 24f * z);
      Hud.centered(ui, "BEST ON " + difficulty.label.toUpperCase(Locale.ROOT), width, y, 1.25f, 0.95f, 0.85f, 0.45f,
          1f);
      y += Hud.lineHeight(ui, 1.25f) + 8f * z;
      if (entries.isEmpty()) {
        Hud.centered(ui, "No scores yet: be the first", width, y, scale, 0.92f, 0.94f, 0.98f, 1f);
      } else { // two columns of five
        final float x = (width - tableW) / 2f;
        Hud.scoreRows(ui, entries, 0, 5, -1, x, y, scale);
        Hud.scoreRows(ui, entries, 5, 10, -1, x + Hud.tableWidth(scale) + gap, y, scale);
      }
    } else {
      drawControls(ui, width, y);
    }
    final var quit = "Q / Esc  quit";
    Hud.text(ui, quit, width - Hud.width(ui, quit, 1.1f) - 16f, height - Hud.lineHeight(ui, 1.1f) - 12f, 1.1f, 0.85f,
        0.88f, 0.94f, 1f);
    final var tuning = this.game.tuning();
    if (tuning.custom()) {
      final var note = "Custom tuning: " + tuning.overrides() + " value" + (tuning.overrides() == 1 ? "" : "s")
          + " changed  -  best scores are not recorded";
      Hud.centered(ui, note, width, height * 0.92f, 1f, 1f, 0.75f, 0.3f, 1f);
    }
    if (!tuning.problems().isEmpty()) { // the packaged game has no visible log: say what was wrong here
      final int more = tuning.problems().size() - 1;
      final var note = "tuning.properties: " + tuning.problems().get(0) + (more > 0 ? "  (+" + more + " more)" : "");
      Hud.centered(ui, note, width, height * 0.92f + Hud.lineHeight(ui, 1f) + 2f, 1f, 1f, 0.45f, 0.35f, 1f);
    }
  }
}
