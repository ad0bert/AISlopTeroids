package astroids;

import java.util.Locale;

import org.joml.Vector2f;
import org.joml.Vector3f;

import astroids.model.Boss;
import astroids.model.Controls;
import astroids.model.Difficulty;
import astroids.model.GameEvent;
import astroids.model.World;
import engine.audio.AudioSource;
import engine.core.Engine;
import engine.core.LevelState;
import engine.ecs.Entity;
import engine.input.InputState;
import engine.render.Camera;
import engine.render.UiCanvas;

/**
 * A game in progress: the player's ship is the camera. Mouse (or right stick) looks, W/S thrust along the view, A/D
 * slide, Space/Shift rise and sink, click or Ctrl fires, E jumps through hyperspace. The ship keeps drifting after
 * letting go of thrust, as in the original.
 *
 * <p>
 * {@code -Dastroids.autopilot=true} flies by itself (turning and firing), for screenshots and smoke tests.
 */
final class PlayState extends LevelState {
  private static final float STICK_DEGREES_PER_SECOND = 140f;
  /** With this many rocks or fewer left, each gets an on-screen marker, so the last ones are easy to find. */
  static final int MARK_ROCKS_BELOW = 4;

  private final AstroidsGame game;
  private final boolean autopilot = Boolean.getBoolean("astroids.autopilot");
  private World world;
  private WorldView view;
  private Entity camera;
  private float yaw;
  private float pitch;
  private boolean wasCaptured;
  /** Played with changed numbers from tuning.properties: labelled, and it sets no best score. */
  private boolean custom;
  private Difficulty difficulty;
  private float time;

  private String message = "";
  private float messageUntil;
  private float flash;
  private float warpFlash;
  /** Ambience that follows the game: the engine (by speed) and a throb while an enemy ship is about. */
  private AudioSource hum;
  private AudioSource danger;

  private final Vector3f forward = new Vector3f();
  private final Vector3f right = new Vector3f();
  private final Vector3f up = new Vector3f();
  private final Vector3f thrust = new Vector3f();
  private final Vector2f stick = new Vector2f();

  PlayState(AstroidsGame game) {
    this.game = game;
  }

  @Override
  protected void build(Engine engine) {
    this.difficulty = this.game.options().difficulty;
    this.custom = this.game.loadTuning().custom();
    this.world = World.newGame(System.nanoTime(), this.game.tuning().tuning());
    this.view = new WorldView(engine, this.game.art());
    this.camera = this.game.art().addBackdrop(engine.scene());
    final var art = this.game.art();
    art.addLoop(engine.scene(), "ambience", art.playAmbience, 0.7f);
    this.hum = art.addLoop(engine.scene(), "engine hum", art.engineHum, 0f);
    this.danger = art.addLoop(engine.scene(), "danger", art.danger, 0f);
    this.wasCaptured = engine.isMouseCaptured();
    afterStep(engine, 0f);
  }

  @Override
  public void revealed(Engine engine) {
    this.wasCaptured = engine.isMouseCaptured(); // back from the pause menu: start watching the mouse afresh
  }

  @Override
  public void update(Engine engine, double dtD) {
    final float dt = (float) dtD;
    this.time += dt;
    final var input = engine.input();
    look(engine, dt);

    final var t = this.camera.transform();
    t.forward(this.forward);
    t.right(this.right);
    this.up.set(this.right).cross(this.forward);
    this.thrust.zero();
    axis(input, "forward", "back", this.forward);
    axis(input, "right", "left", this.right);
    axis(input, "up", "down", this.up);
    final var pad = input.firstGamepad();
    if (pad != null) {
      final var move = pad.leftStick(this.stick);
      this.thrust.fma(move.y, this.forward).fma(move.x, this.right);
    }
    boolean fire = input.isActionDown("fire") || (engine.isMouseCaptured()
        && input.isMouseDown(InputState.BUTTON_LEFT));
    boolean jump = input.wasActionPressed("hyperspace");
    if (this.autopilot) {
      this.thrust.set(this.forward).mul(0.3f);
      fire = true;
      jump = this.time > 4f && this.world.ship().hyperspaceCooldown <= 0f; // shows the jump in smoke runs
    }
    this.world.step(dt, new Controls(new Vector3f(this.thrust), new Vector3f(this.forward), fire, jump));
    afterStep(engine, dt);

    if (!this.world.isGameOver()) {
      final boolean captured = engine.isMouseCaptured();
      if (input.wasActionPressed("pause") || (this.wasCaptured && !captured)) {
        engine.states().push(new PauseState(this.game));
      }
      this.wasCaptured = captured;
    }
  }

  /** Lets the finished game drift on behind the game-over screen. */
  void drift(Engine engine, float dt) {
    this.time += dt;
    this.world.step(dt, Controls.NONE);
    afterStep(engine, dt);
  }

  private void look(Engine engine, float dt) {
    final var input = engine.input();
    final var options = this.game.options();
    this.yaw += options.yawFor(input.consumeMouseDx());
    this.pitch += options.pitchFor(input.consumeMouseDy());
    final var pad = input.firstGamepad();
    if (pad != null) {
      final var look = pad.rightStick(this.stick);
      this.yaw -= look.x * STICK_DEGREES_PER_SECOND * dt;
      this.pitch += (options.invertY ? -look.y : look.y) * STICK_DEGREES_PER_SECOND * dt;
    }
    if (this.autopilot) {
      this.yaw += 25f * dt;
      this.pitch = 12f * (float) Math.sin(this.time * 0.7f);
    }
    this.pitch = Math.max(-85f, Math.min(85f, this.pitch));
    this.camera.transform().rotation().identity().rotateY((float) Math.toRadians(this.yaw))
        .rotateX((float) Math.toRadians(this.pitch));
  }

  private void axis(InputState input, String plus, String minus, Vector3f direction) {
    if (input.isActionDown(plus)) {
      this.thrust.add(direction);
    }
    if (input.isActionDown(minus)) {
      this.thrust.sub(direction);
    }
  }

  /** Brings the view up to date with the world and reacts to what happened. */
  private void afterStep(Engine engine, float dt) {
    final var ship = this.world.ship();
    final var events = this.world.drainEvents();
    final var p = this.camera.transform().position();
    final boolean jumped = p.distanceSquared(ship.position) > this.world.arena() * this.world.arena()
        || events.stream().anyMatch(e -> e instanceof GameEvent.Hyperspace);
    p.set(ship.position);
    if (jumped) {
      this.camera.transform().snapshot(); // flew through a wall, came back in the middle, or jumped
    }
    this.view.sync(this.world, ship.position, dt);
    this.view.play(events);
    this.flash = Math.max(0f, this.flash - dt * 1.5f);
    this.warpFlash = Math.max(0f, this.warpFlash - dt * 2f);
    followAmbience(dt);
    for (GameEvent e : events) {
      switch (e) {
      case GameEvent.WaveStarted w -> say("WAVE " + w.wave(), 2.5f);
      case GameEvent.BossAppeared b -> say(b.kind() == Boss.Kind.SCOUT ? "SCOUT!" : "ENEMY SHIP!", 2.5f);
      case GameEvent.BossDestroyed b -> say((b.kind() == Boss.Kind.SCOUT ? "SCOUT" : "ENEMY SHIP") + " DESTROYED  +"
          + b.points(), 2.5f);
      case GameEvent.BossLeft b -> say("The enemy ship left", 2f);
      case GameEvent.ExtraLife l -> say("EXTRA SHIP!", 2.5f);
      case GameEvent.Hyperspace h -> this.warpFlash = 1f;
      case GameEvent.ShipDestroyed s -> {
        this.flash = 0.8f;
        if (this.world.lives() > 0) {
          say("SHIP LOST", this.world.tuning().respawnDelay);
        }
      }
      case GameEvent.GameOver g -> engine.states().push(new GameOverState(this.game, this, g.score(),
          this.world.wave(), this.difficulty, this.custom));
      default -> {
      }
      }
    }
  }

  /** The engine hums louder and higher the faster the ship goes; the throb swells while an enemy ship is about. */
  private void followAmbience(float dt) {
    final var ship = this.world.ship();
    final float speed = ship.alive && !this.world.isGameOver()
        ? Math.min(1f, ship.velocity.length() / this.world.tuning().shipMaxSpeed) : -1f;
    final float humTarget = speed < 0f ? 0f : 0.12f + 0.4f * speed;
    this.hum.volume = approach(this.hum.volume, humTarget, dt * 1.5f);
    this.hum.pitch = 0.8f + 0.5f * Math.max(0f, speed);
    final float dangerTarget = this.world.boss() != null && !this.world.isGameOver() ? 0.6f : 0f;
    this.danger.volume = approach(this.danger.volume, dangerTarget, dt * 0.5f);
  }

  private static float approach(float value, float target, float step) {
    return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
  }

  private void say(String text, float seconds) {
    this.message = text;
    this.messageUntil = this.time + seconds;
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    if (this.flash > 0f) {
      ui.drawRect(0f, 0f, width, height, 0.8f, 0.1f, 0.05f, this.flash * 0.45f);
    }
    if (this.warpFlash > 0f) {
      ui.drawRect(0f, 0f, width, height, 0.75f, 0.85f, 1f, this.warpFlash * this.warpFlash * 0.8f);
    }
    if (this.world.isGameOver()) {
      return; // the game-over screen takes over
    }
    final var ship = this.world.ship();
    if (ship.alive) {
      Hud.crosshair(ui, width, height);
    }
    Hud.text(ui, String.format(Locale.ROOT, "SCORE  %d", this.world.score()), 16f, 14f, 1.6f, 1f, 1f, 1f, 1f);
    Hud.text(ui, "WAVE " + this.world.wave() + "    ROCKS " + this.world.asteroids().size() + "    "
        + this.difficulty.label.toUpperCase(Locale.ROOT), 16f, 14f + ui.lineHeight(1.6f) + 2f, 1.1f, 0.75f, 0.8f, 0.9f,
        1f);
    final int ships = Math.max(0, this.world.lives());
    final var lives = "SHIPS " + (ships > 6 ? "▲ x" + ships : "▲ ".repeat(ships).trim());
    Hud.text(ui, lives, width - ui.textWidth(lives, 1.6f) - 16f, 14f, 1.6f, 0.6f, 1f, 0.8f, 1f);
    if (ship.alive && ship.invulnerable > 0f && ((int) (this.time * 6) % 2 == 0)) {
      Hud.centered(ui, "SHIELD", width, height / 2f + 24f, 1.1f, 0.6f, 1f, 0.8f, 0.9f);
    }
    if (this.custom) {
      Hud.text(ui, "CUSTOM TUNING", 16f, 14f + ui.lineHeight(1.6f) + ui.lineHeight(1.1f) + 6f, 1f, 1f, 0.75f, 0.3f, 1f);
    }
    final var speed = String.format(Locale.ROOT, "SPEED %2.0f", ship.velocity.length());
    final float bottom = height - ui.lineHeight(1.1f) - 14f;
    Hud.text(ui, speed, 16f, bottom, 1.1f, 0.75f, 0.8f, 0.9f, 1f);
    final float cooldown = ship.hyperspaceCooldown;
    final var jump = cooldown > 0f ? String.format(Locale.ROOT, "JUMP %.0f", Math.ceil(cooldown)) : "JUMP READY  (E)";
    final float jumpX = 16f + ui.textWidth("SPEED 00", 1.1f) + 30f;
    if (cooldown > 0f) {
      Hud.text(ui, jump, jumpX, bottom, 1.1f, 0.6f, 0.62f, 0.7f, 1f);
    } else {
      Hud.text(ui, jump, jumpX, bottom, 1.1f, 0.6f, 0.85f, 1f, 1f);
    }

    final var view = this.camera.transform().rotation();
    final float fov = this.camera.get(Camera.class).fovDegrees;
    final var rocks = this.world.asteroids();
    if (rocks.size() < MARK_ROCKS_BELOW) {
      final var toRock = new Vector3f();
      for (var rock : rocks) {
        this.world.delta(ship.position, rock.position, toRock);
        Hud.marker(ui, width, height, toRock, view, fov, String.format(Locale.ROOT, "ROCK %.0f m", toRock.length()),
            0.85f, 0.85f, 0.9f);
      }
    }
    final var boss = this.world.boss();
    if (boss != null) {
      final var toBoss = this.world.delta(ship.position, boss.position, new Vector3f());
      if (boss.kind == Boss.Kind.SCOUT) {
        Hud.marker(ui, width, height, toBoss, view, fov, String.format(Locale.ROOT, "SCOUT %.0f m", toBoss.length()),
            1f, 0.7f, 0.15f);
      } else {
        Hud.marker(ui, width, height, toBoss, view, fov, String.format(Locale.ROOT, "ENEMY %.0f m", toBoss.length()),
            1f, 0.3f, 0.25f);
      }
    }
    if (this.game.options().radar) {
      Radar.draw(ui, width, height, this.world, ship.position, view);
    }
    if (this.time < this.messageUntil) {
      final float fade = Math.min(1f, (this.messageUntil - this.time) * 2f);
      Hud.centered(ui, this.message, width, height * 0.28f, 2.2f, 1f, 0.9f, 0.5f, fade);
    }
  }
}
