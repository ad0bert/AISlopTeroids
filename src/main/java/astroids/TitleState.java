package astroids;

import org.joml.Vector3f;

import astroids.model.Controls;
import astroids.model.World;
import engine.core.Engine;
import engine.core.LevelState;
import engine.ecs.Entity;
import engine.render.UiCanvas;

/** The title screen: rocks drifting past a slowly turning camera, the name, the controls and the best score. */
final class TitleState extends LevelState {
  private final AstroidsGame game;
  private World world;
  private WorldView view;
  private Entity camera;
  private Clicks clicks;
  private float time;

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
  }

  @Override
  public void update(Engine engine, double dtD) {
    final float dt = (float) dtD;
    this.time += dt;
    this.world.step(dt, Controls.NONE);
    this.view.sync(this.world, this.camera.transform().position(), dt);
    this.camera.transform().rotation().identity().rotateY(this.time * 0.05f).rotateX(0.15f * (float) Math.sin(
        this.time * 0.1f));
    final boolean clicked = this.clicks.clicked();
    if (engine.input().wasActionPressed("start") || clicked) {
      engine.captureMouse(true);
      engine.states().replace(new PlayState(this.game));
    }
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    final float top = height * 0.22f;
    Hud.centered(ui, "SIMPLE ASTROIDS", width, top, 4f, 0.95f, 0.85f, 0.45f, 1f);
    Hud.centered(ui, "Asteroids in three dimensions", width, top + ui.lineHeight(4f) + 4f, 1.3f, 0.75f, 0.8f,
        0.9f, 1f);
    final float blink = 0.6f + 0.4f * (float) Math.sin(this.time * 4f);
    Hud.centered(ui, "Press Enter or click to start", width, height * 0.5f, 1.6f, 1f, 1f, 1f, blink);

    final String[] help = { "Mouse  look       W / S  thrust forward / back       A / D  slide",
        "Space / Shift  up / down       Left click or Ctrl  fire       P  pause       F11  fullscreen",
        "Big rocks split in two, small ones shatter. Watch out for the red ship: it shoots back." };
    float y = height * 0.66f;
    for (String line : help) {
      Hud.centered(ui, line, width, y, 1.05f, 0.8f, 0.82f, 0.88f, 1f);
      y += ui.lineHeight(1.05f) + 6f;
    }
    final int best = this.game.highScore();
    if (best > 0) {
      Hud.centered(ui, "Best score  " + best, width, height * 0.88f, 1.2f, 0.95f, 0.85f, 0.45f, 1f);
    }
  }
}
