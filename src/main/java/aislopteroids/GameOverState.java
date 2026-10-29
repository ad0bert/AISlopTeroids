package aislopteroids;

import aislopteroids.model.Difficulty;
import engine.core.Engine;
import engine.core.GameState;
import engine.render.UiCanvas;

/**
 * Over the finished game (whose rocks keep drifting): the score, and the difficulty's table of the ten best. A score
 * that makes the table first asks for a name (typed, Enter to save; the last name is offered). Then Enter or a click
 * plays again, and Q goes back to the title screen.
 */
final class GameOverState implements GameState {
  private final AISlopTeroidsGame game;
  private final PlayState play;
  private final int score;
  private final int wave;
  private final Difficulty difficulty;
  private final boolean custom;
  private Clicks clicks;
  private float time;
  private HighScores table;
  /** Typing a name for the table. */
  private boolean naming;
  private final StringBuilder name = new StringBuilder();
  /** The row of this game in the table, or -1. */
  private int rank = -1;
  private float savedAt = -10f;

  GameOverState(AISlopTeroidsGame game, PlayState play, int score, int wave, Difficulty difficulty, boolean custom) {
    this.game = game;
    this.play = play;
    this.score = score;
    this.wave = wave;
    this.difficulty = difficulty;
    this.custom = custom;
  }

  @Override
  public void enter(Engine engine) {
    this.clicks = new Clicks(engine.input());
    this.table = this.game.highScores(this.difficulty);
    this.naming = !this.custom && this.table.rankFor(this.score) >= 0;
    this.name.append(this.game.lastName());
    engine.input().consumeTypedText(); // what was typed while playing (W, A, S, D...) is not a name
  }

  @Override
  public void update(Engine engine, double dt) {
    this.time += (float) dt;
    this.play.drift(engine, (float) dt);
    final var input = engine.input();
    final boolean clicked = this.clicks.clicked();
    if (this.naming) {
      boolean done = false;
      for (char c : input.consumeTypedText().toCharArray()) {
        if (c == '\b') {
          if (!this.name.isEmpty()) {
            this.name.setLength(this.name.length() - 1);
          }
        } else if (c == '\n') {
          done = true;
        } else if (HighScores.allowed(c) && this.name.length() < HighScores.MAX_NAME) {
          this.name.append(c);
        }
      }
      // Enter, a click, or (on a gamepad, which cannot type) Start or A
      if (this.time > 0.8f && (done || clicked || input.wasActionPressed("start"))) {
        this.rank = this.game.recordScore(this.difficulty, this.name.toString(), this.score, this.wave);
        this.table = this.game.highScores(this.difficulty);
        this.naming = false;
        this.savedAt = this.time;
      }
      return;
    }
    if (this.time < 1.5f || this.time - this.savedAt < 0.6f) {
      return; // the key or click that ended the game (or saved the name) does not also start the next one
    }
    if (input.wasActionPressed("start") || clicked) {
      engine.states().clear();
      engine.states().push(new PlayState(this.game));
    } else if (input.wasActionPressed("quit")) {
      engine.captureMouse(false);
      engine.states().clear();
      engine.states().push(new TitleState(this.game));
    }
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    Hud.screen(width, height);
    Hud.dim(ui, width, height, Math.min(0.55f, this.time * 0.35f));
    float y = height * 0.1f;
    Hud.centered(ui, "GAME OVER", width, y, 3.2f, 1f, 0.35f, 0.3f, 1f);
    y += Hud.lineHeight(ui, 3.2f) + 6f;
    Hud.centered(ui, "Score  " + this.score + "      wave " + this.wave + "      " + this.difficulty.label, width, y,
        1.6f, 1f, 1f, 1f, 1f);
    y += Hud.lineHeight(ui, 1.6f) + 22f;
    final float blink = 0.6f + 0.4f * (float) Math.sin(this.time * 4f);

    if (this.naming) {
      final int place = this.table.rankFor(this.score) + 1;
      Hud.centered(ui, place == 1 ? "NEW BEST SCORE!" : "A PLACE IN THE TABLE: #" + place, width, y, 2f, 0.95f,
          0.85f, 0.45f, 1f);
      y += Hud.lineHeight(ui, 2f) + 16f;
      final var cursor = (this.time * 2f) % 1f < 0.5f ? "_" : " ";
      Hud.centered(ui, "Your name:  " + this.name + cursor, width, y, 1.8f, 1f, 1f, 1f, 1f);
      y += Hud.lineHeight(ui, 1.8f) + 14f;
      Hud.centered(ui, "Type your name, then Enter (or click) to save it", width, y, 1.1f, 0.8f, 0.8f, 0.85f, blink);
      return;
    }

    if (this.custom) {
      Hud.centered(ui, "Custom tuning: this game does not go in the table", width, y, 1.2f, 1f, 0.75f, 0.3f, 1f);
      y += Hud.lineHeight(ui, 1.2f) + 10f;
    } else if (this.rank == 0) {
      Hud.centered(ui, "New best score!", width, y, 1.6f, 0.95f, 0.85f, 0.45f, 1f);
      y += Hud.lineHeight(ui, 1.6f) + 10f;
    }
    Hud.centered(ui, "BEST ON " + this.difficulty.label.toUpperCase(java.util.Locale.ROOT), width, y, 1.2f, 0.6f,
        0.85f, 1f, 1f);
    y += Hud.lineHeight(ui, 1.2f) + 8f;
    if (this.table.entries().isEmpty()) {
      Hud.centered(ui, "No scores yet", width, y, 1.1f, 0.75f, 0.75f, 0.8f, 1f);
      y += Hud.lineHeight(ui, 1.1f) + 3f;
    } else {
      final float scale = 1.15f;
      y = Hud.scoreRows(ui, this.table.entries(), 0, HighScores.SIZE, this.rank, (width - Hud.tableWidth(scale)) / 2f,
          y, scale);
    }
    if (this.time > 1.5f) {
      Hud.centered(ui, "Enter or click: play again      Q: title screen", width, Math.max(y + 18f, height * 0.88f),
          1.3f, 1f, 1f, 1f, blink);
    }
  }
}
