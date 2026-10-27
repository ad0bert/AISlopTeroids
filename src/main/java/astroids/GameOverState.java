package astroids;

import engine.core.Engine;
import engine.core.GameState;
import engine.render.UiCanvas;

/** Over the finished game (whose rocks keep drifting): the score, whether it is a new best, and how to play again. */
final class GameOverState implements GameState {
  private final AstroidsGame game;
  private final PlayState play;
  private final int score;
  private final boolean best;
  private final boolean custom;
  private Clicks clicks;
  private float time;

  GameOverState(AstroidsGame game, PlayState play, int score, boolean best, boolean custom) {
    this.game = game;
    this.play = play;
    this.score = score;
    this.best = best;
    this.custom = custom;
  }

  @Override
  public void enter(Engine engine) {
    this.clicks = new Clicks(engine.input());
  }

  @Override
  public void update(Engine engine, double dt) {
    this.time += (float) dt;
    this.play.drift(engine, (float) dt);
    final boolean clicked = this.clicks.clicked();
    if (this.time > 1.5f && (engine.input().wasActionPressed("start") || clicked)) {
      engine.states().clear();
      engine.states().push(new PlayState(this.game));
    }
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    Hud.dim(ui, width, height, Math.min(0.45f, this.time * 0.3f));
    Hud.centered(ui, "GAME OVER", width, height * 0.33f, 3.5f, 1f, 0.35f, 0.3f, 1f);
    Hud.centered(ui, "Score  " + this.score, width, height * 0.33f + ui.lineHeight(3.5f) + 10f, 2f, 1f, 1f, 1f, 1f);
    if (this.custom) {
      Hud.centered(ui, "Custom tuning: no best score", width, height * 0.55f, 1.3f, 1f, 0.75f, 0.3f, 1f);
    } else if (this.best) {
      Hud.centered(ui, "New best score!", width, height * 0.55f, 1.6f, 0.95f, 0.85f, 0.45f, 1f);
    } else {
      Hud.centered(ui, "Best  " + this.game.highScore(), width, height * 0.55f, 1.3f, 0.8f, 0.8f, 0.8f, 1f);
    }
    if (this.time > 1.5f) {
      Hud.centered(ui, "Press Enter or click to play again", width, height * 0.68f, 1.4f, 1f, 1f, 1f,
          0.6f + 0.4f * (float) Math.sin(this.time * 4f));
    }
  }
}
