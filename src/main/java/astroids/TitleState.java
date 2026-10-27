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
 * and in turn the controls and the difficulty's table of best scores.
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

  private static String extraShips(int every) {
    return every > 0 ? String.format(Locale.ROOT, "Every %,d points bring an extra ship.", every)
        : "No extra ships with this tuning.";
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    final float top = height * 0.22f;
    Hud.centered(ui, "SIMPLE ASTROIDS", width, top, 4f, 0.95f, 0.85f, 0.45f, 1f);
    Hud.centered(ui, "Asteroids in three dimensions", width, top + ui.lineHeight(4f) + 4f, 1.3f, 0.75f, 0.8f,
        0.9f, 1f);
    final float blink = 0.6f + 0.4f * (float) Math.sin(this.time * 4f);
    Hud.centered(ui, "Press Enter or click to start", width, height * 0.46f, 1.6f, 1f, 1f, 1f, blink);

    final var difficulty = this.game.options().difficulty;
    final float dy = height * 0.46f + ui.lineHeight(1.6f) + 14f;
    final var left = difficulty.step(-1) != difficulty ? "<   " : "    ";
    final var right = difficulty.step(1) != difficulty ? "   >" : "    ";
    Hud.centered(ui, left + difficulty.label.toUpperCase(Locale.ROOT) + right, width, dy, 1.6f, 0.6f, 0.85f, 1f, 1f);
    final var best = this.table.entries().isEmpty() ? "Left / Right: difficulty"
        : "Left / Right: difficulty      Best  " + this.table.best() + "  " + this.table.entries().get(0).name();
    Hud.centered(ui, best, width, dy + ui.lineHeight(1.6f) + 2f, 1f, 0.75f, 0.78f, 0.85f, 1f);

    float y = height * 0.66f;
    if (this.scoresPage) {
      Hud.centered(ui, "BEST ON " + difficulty.label.toUpperCase(Locale.ROOT), width, y, 1.15f, 0.95f, 0.85f, 0.45f,
          1f);
      y += ui.lineHeight(1.15f) + 8f;
      final var entries = this.table.entries();
      if (entries.isEmpty()) {
        Hud.centered(ui, "No scores yet: be the first", width, y, 1.05f, 0.8f, 0.82f, 0.88f, 1f);
      } else { // two columns of five
        final float scale = 1f;
        final float gap = 40f;
        final float x = (width - 2f * Hud.TABLE_WIDTH * scale - gap) / 2f;
        Hud.scoreRows(ui, entries, 0, 5, -1, x, y, scale);
        Hud.scoreRows(ui, entries, 5, 10, -1, x + Hud.TABLE_WIDTH * scale + gap, y, scale);
      }
    } else {
      final String[] help = { "Mouse  look       W / S  thrust forward / back       A / D  slide       Space / Shift  up / down",
          "Left click or Ctrl  fire       E  hyperspace       P  pause       O  options       F11  fullscreen",
          "Big rocks split in two, small ones shatter. Watch out for the red ship and the quick yellow scout.",
          extraShips(this.game.tuning().tuning().extraLifeEvery) };
      for (String line : help) {
        Hud.centered(ui, line, width, y, 1.05f, 0.8f, 0.82f, 0.88f, 1f);
        y += ui.lineHeight(1.05f) + 6f;
      }
    }
    final var tuning = this.game.tuning();
    if (tuning.custom()) {
      final var note = "Custom tuning: " + tuning.overrides() + " value" + (tuning.overrides() == 1 ? "" : "s")
          + " changed  -  best scores are not recorded";
      Hud.centered(ui, note, width, height * 0.92f, 1f, 1f, 0.75f, 0.3f, 1f);
    }
    if (!tuning.problems().isEmpty()) { // the packaged game has no visible log: say what was wrong here
      final int more = tuning.problems().size() - 1;
      final var note = "tuning.properties: " + tuning.problems().get(0) + (more > 0 ? "  (+" + more + " more)" : "");
      Hud.centered(ui, note, width, height * 0.92f + ui.lineHeight(1f) + 2f, 1f, 1f, 0.45f, 0.35f, 1f);
    }
  }
}
