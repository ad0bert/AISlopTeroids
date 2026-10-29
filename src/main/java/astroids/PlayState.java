package astroids;

import java.util.Locale;

import org.joml.Quaternionf;
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
import engine.render.MeshRenderer;
import engine.render.UiCanvas;

/**
 * A game in progress: the player's ship is the camera. Mouse (or right stick) steers, the ship turning after it with
 * some inertia; W thrusts ahead along where it points and S brakes, click or Ctrl fires, E jumps through hyperspace.
 * The ship keeps drifting after letting go of thrust, as in the original.
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
  private final Turning turn = new Turning();
  private boolean wasCaptured;
  /** Played with changed numbers from tuning.properties: labelled, and it sets no best score. */
  private boolean custom;
  private Difficulty difficulty;
  private float time;

  private String message = "";
  private float messageUntil;
  private float flash;
  private float warpFlash;
  /** Where the player looks and fires: the ship's turn. In the first-person views it is the camera's too. */
  private final Quaternionf aim = new Quaternionf();
  /** The player's ship from inside: child of the camera, hidden while the ship is gone or in another view. */
  private Entity cockpit;
  private GlassDisplay glass;
  /** The player's whole ship, shown in the view from behind it. */
  private Entity playerShip;
  /** The fire out of its engine while it thrusts ahead. */
  private Exhaust exhaust;
  /** The view from behind the ship: how far behind and above it the camera is, and where the crosshair meets the
   * line of fire (this far ahead of the ship). */
  static final float CHASE_BACK = 5.5f;
  static final float CHASE_UP = 1.7f;
  static final float CHASE_AIM = 45f;
  /** The player's ship model is made with a radius of about 1.2; this brings it to the ship's size. */
  static final float PLAYER_SCALE = 0.85f;
  /** Seconds the camera takes to pull back from a destroyed ship, and how far back and up it goes. */
  static final float DEATH_PULL = 0.9f;
  static final float DEATH_BACK = 11f;
  static final float DEATH_UP = 3.5f;
  /** Where the ship was destroyed, the way it was facing then, and when (-1: it is flying). */
  private final Vector3f deathAt = new Vector3f();
  /** Where the camera was then (behind the ship in the view from behind). */
  private final Vector3f deathFrom = new Vector3f();
  private final Vector3f deathBack = new Vector3f();
  private final Vector3f deathUp = new Vector3f();
  private final Quaternionf deathView = new Quaternionf();
  private float deathTime = -1f;
  /** Ambience that follows the game: the engine (by speed) and a throb while an enemy ship is about. */
  private AudioSource hum;
  private AudioSource danger;

  private final Vector3f forward = new Vector3f();
  private final Vector3f thrust = new Vector3f();
  private final Vector2f stick = new Vector2f();

  PlayState(AstroidsGame game) {
    this.game = game;
  }

  @Override
  protected void build(Engine engine) {
    this.difficulty = this.game.options().difficulty;
    // -Dastroids.view=COCKPIT, CLEAR or CHASE: start in that view (screenshots)
    final var view = System.getProperty("astroids.view");
    if (view != null) {
      this.game.options().view = View.parse(view, this.game.options().view);
    }
    this.custom = this.game.loadTuning().custom();
    this.world = World.newGame(System.nanoTime(), this.game.tuning().tuning());
    this.view = new WorldView(engine, this.game.art());
    this.camera = this.game.art().addBackdrop(engine.scene());
    final var art = this.game.art();
    this.cockpit = new Entity("cockpit");
    this.cockpit.transform().setParent(this.camera.transform());
    engine.scene().add(this.cockpit);
    addParts(engine, this.cockpit, art.cockpitParts, false);
    this.glass = new GlassDisplay(engine.scene(), this.cockpit, art.cube, art.holoOn, art.holoGhost, art.holoJump);
    this.playerShip = new Entity("player ship");
    engine.scene().add(this.playerShip);
    addParts(engine, this.playerShip, art.playerParts, true);
    this.exhaust = new Exhaust(engine.scene(), this.playerShip);
    art.addLoop(engine.scene(), "ambience", art.playAmbience, 0.7f);
    this.hum = art.addLoop(engine.scene(), "engine hum", art.engineHum, 0f);
    this.danger = art.addLoop(engine.scene(), "danger", art.danger, 0f);
    this.wasCaptured = engine.isMouseCaptured();
    afterStep(engine, 0f);
  }

  private static void addParts(Engine engine, Entity parent, java.util.List<Art.Part> parts, boolean shadows) {
    for (var part : parts) {
      final var e = new Entity(parent.name() + " part");
      e.add(new MeshRenderer(part.mesh(), part.material())).castShadows = shadows;
      e.transform().position().set(part.position());
      e.transform().rotation().set(part.rotation());
      e.transform().scale().set(part.scale());
      e.transform().setParent(parent.transform());
      engine.scene().add(e);
    }
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
    if (input.wasActionPressed("view")) {
      final var options = this.game.options();
      options.view = options.view.next();
      this.game.saveOptions();
      say(options.view.label.toUpperCase(Locale.ROOT), 1.2f);
    }

    // the engine pushes only ahead, along where the ship points (no sliding, rising or sinking); back brakes
    this.aim.transform(this.forward.set(0f, 0f, -1f));
    float push = input.isActionDown("forward") ? 1f : 0f;
    boolean brake = input.isActionDown("back");
    final var pad = input.firstGamepad();
    if (pad != null) {
      final float y = pad.leftStick(this.stick).y;
      push += Math.max(0f, y);
      brake |= y < -0.5f;
    }
    this.thrust.set(this.forward).mul(Math.min(1f, push));
    boolean fire = input.isActionDown("fire") || (engine.isMouseCaptured()
        && input.isMouseDown(InputState.BUTTON_LEFT));
    boolean jump = input.wasActionPressed("hyperspace");
    if (this.autopilot) {
      this.thrust.set(this.forward).mul(0.3f);
      fire = true;
      jump = this.time > 4f && this.world.ship().hyperspaceCooldown <= 0f; // shows the jump in smoke runs
    }
    this.world.step(dt, new Controls(new Vector3f(this.thrust), new Vector3f(this.forward), fire, jump, brake));
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

  /** The mouse and stick turn where the player wants to look; the ship turns after it, with inertia. */
  private void look(Engine engine, float dt) {
    final var input = engine.input();
    final var options = this.game.options();
    final var turn = this.turn;
    turn.look(options.yawFor(input.consumeMouseDx()), options.pitchFor(input.consumeMouseDy()));
    final var pad = input.firstGamepad();
    if (pad != null) {
      final var look = pad.rightStick(this.stick);
      turn.look(-look.x * STICK_DEGREES_PER_SECOND * dt,
          (options.invertY ? -look.y : look.y) * STICK_DEGREES_PER_SECOND * dt);
    }
    if (this.autopilot) {
      // in steps, so the steering thrusters show in screenshots
      if ((int) (this.time / 0.8f) != (int) ((this.time - dt) / 0.8f)) {
        turn.targetYaw += 20f;
      }
      turn.targetPitch = 12f * (float) Math.sin(this.time * 0.7f);
    }
    turn.step(dt);
    this.aim.identity().rotateY((float) Math.toRadians(turn.yaw)).rotateX((float) Math.toRadians(turn.pitch));
  }

  /** Brings the view up to date with the world and reacts to what happened. */
  private void afterStep(Engine engine, float dt) {
    final var ship = this.world.ship();
    final var events = this.world.drainEvents();
    final var view = this.camera.transform();
    final var p = view.position();
    final var shown = this.game.options().view;
    if (events.stream().anyMatch(e -> e instanceof GameEvent.ShipDestroyed)) {
      // remember the moment: the camera pulls back from here to watch the ship go
      this.deathAt.set(ship.position);
      this.deathFrom.set(p);
      this.deathView.set(view.rotation());
      this.aim.transform(this.deathBack.set(0f, 0f, 1f));
      this.aim.transform(this.deathUp.set(0f, 1f, 0f));
      this.deathTime = this.time;
    }
    final var shipTurn = this.playerShip.transform();
    if (ship.alive) {
      final var target = new Vector3f(ship.position);
      if (shown.firstPerson()) {
        view.rotation().set(this.aim);
      } else {
        // behind and above the ship, tilted down so the crosshair is where the shots go, CHASE_AIM ahead
        target.add(this.aim.transform(new Vector3f(0f, CHASE_UP, CHASE_BACK)));
        view.rotation().set(this.aim).rotateX((float) -Math.atan2(CHASE_UP, CHASE_BACK + CHASE_AIM));
      }
      final boolean jumped = p.distanceSquared(target) > this.world.arena() * this.world.arena()
          || events.stream().anyMatch(e -> e instanceof GameEvent.Hyperspace || e instanceof GameEvent.ShipRespawned);
      p.set(target);
      shipTurn.position().set(ship.position);
      shipTurn.rotation().set(this.aim);
      if (jumped) {
        view.snapshot(); // flew through a wall, jumped, or the next ship came: no smear across the screen
        shipTurn.snapshot();
      }
      this.deathTime = -1f;
    } else if (this.deathTime >= 0f) {
      final float u = Math.min(1f, (this.time - this.deathTime) / DEATH_PULL);
      final float ease = 1f - (1f - u) * (1f - u) * (1f - u);
      final var away = new Vector3f(this.deathBack).mul(DEATH_BACK).fma(DEATH_UP, this.deathUp).add(this.deathAt);
      this.deathFrom.lerp(away, ease, p);
      final var toWreck = new Vector3f(this.deathAt).sub(p);
      view.rotation().set(this.deathView);
      if (toWreck.lengthSquared() > 1e-4f) {
        view.rotation().slerp(aim(toWreck, this.deathUp), ease);
      }
    }
    this.cockpit.transform().scale().set(ship.alive && shown == View.COCKPIT ? 1f : 0f);
    shipTurn.scale().set(ship.alive && !shown.firstPerson() ? PLAYER_SCALE : 0f);
    this.exhaust.show(this.thrust.dot(this.forward), this.turn.yawSpeed, this.turn.pitchSpeed,
        ship.alive && !shown.firstPerson(), this.game.art().quality, dt);
    final var tuning = this.world.tuning();
    this.glass.show(ship.velocity.length(), tuning.shipMaxSpeed,
        1f - ship.hyperspaceCooldown / Math.max(1e-3f, tuning.hyperspaceCooldown), this.time);
    final var guns = new Vector3f[shown.firstPerson() ? Art.COCKPIT_GUNS.length : Art.PLAYER_GUNS.length];
    for (int i = 0; i < guns.length; i++) {
      guns[i] = shown.firstPerson() ? view.rotation().transform(new Vector3f(Art.COCKPIT_GUNS[i])).add(p)
          : this.aim.transform(new Vector3f(Art.PLAYER_GUNS[i]).mul(PLAYER_SCALE)).add(ship.position);
    }
    this.view.guns(guns);
    this.view.sync(this.world, new Vector3f(p), dt);
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
        this.flash = 0.5f; // less than before: now the explosion is there to see
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

  /**
   * The ship's own displays: markers for the enemy ship and the last rocks, and the radar. While the ship is gone the
   * view is the pulled-back camera's, so they are not drawn. The markers are placed as seen from the camera
   * ({@code eye}, behind the ship in the view from behind it), the radar is the ship's.
   */
  private void shipHud(UiCanvas ui, int width, int height, Vector3f shipAt, Vector3f eye) {
    final var view = this.camera.transform().rotation();
    final float fov = this.camera.get(Camera.class).fovDegrees;
    final var rocks = this.world.asteroids();
    if (rocks.size() < MARK_ROCKS_BELOW) {
      final var toRock = new Vector3f();
      for (var rock : rocks) {
        this.world.delta(eye, rock.position, toRock);
        Hud.marker(ui, width, height, toRock, view, fov, String.format(Locale.ROOT, "ROCK %.0f m", toRock.length()),
            0.85f, 0.85f, 0.9f);
      }
    }
    final var boss = this.world.boss();
    if (boss != null) {
      final var toBoss = this.world.delta(eye, boss.position, new Vector3f());
      if (boss.kind == Boss.Kind.SCOUT) {
        Hud.marker(ui, width, height, toBoss, view, fov, String.format(Locale.ROOT, "SCOUT %.0f m", toBoss.length()),
            1f, 0.7f, 0.15f);
      } else {
        Hud.marker(ui, width, height, toBoss, view, fov, String.format(Locale.ROOT, "ENEMY %.0f m", toBoss.length()),
            1f, 0.3f, 0.25f);
      }
    }
    if (this.game.options().radar) {
      Radar.draw(ui, width, height, this.world, shipAt, view);
    }
  }

  /** The rotation of a camera that looks along {@code direction}, with {@code up} upwards on the screen. */
  static Quaternionf aim(Vector3f direction, Vector3f up) {
    // the view rotation turns the direction to -z; the camera's own rotation is its inverse
    return new Quaternionf().lookAlong(new Vector3f(direction).normalize(), up).conjugate();
  }

  private void say(String text, float seconds) {
    this.message = text;
    this.messageUntil = this.time + seconds;
  }

  @Override
  public void draw(Engine engine, UiCanvas ui, int width, int height) {
    Hud.screen(width, height);
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
    // in the cockpit, speed and hyperspace are on its glass (GlassDisplay)
    final boolean inCockpit = ship.alive && this.game.options().view == View.COCKPIT;
    if (ship.alive) {
      Hud.crosshair(ui, width, height);
    }
    Hud.text(ui, String.format(Locale.ROOT, "SCORE  %d", this.world.score()), 16f, 14f, 1.6f, 1f, 1f, 1f, 1f);
    Hud.text(ui, "WAVE " + this.world.wave() + "    ROCKS " + this.world.asteroids().size() + "    "
        + this.difficulty.label.toUpperCase(Locale.ROOT), 16f, 14f + Hud.lineHeight(ui, 1.6f) + 2f, 1.1f, 0.75f, 0.8f, 0.9f,
        1f);
    final int ships = Math.max(0, this.world.lives());
    final var lives = "SHIPS " + (ships > 6 ? "▲ x" + ships : "▲ ".repeat(ships).trim());
    Hud.text(ui, lives, width - Hud.width(ui, lives, 1.6f) - 16f, 14f, 1.6f, 0.6f, 1f, 0.8f, 1f);
    if (ship.alive && ship.invulnerable > 0f && ((int) (this.time * 6) % 2 == 0)) {
      Hud.centered(ui, "SHIELD", width, height / 2f + 24f, 1.1f, 0.6f, 1f, 0.8f, 0.9f);
    }
    if (this.custom) {
      Hud.text(ui, "CUSTOM TUNING", 16f, 14f + Hud.lineHeight(ui, 1.6f) + Hud.lineHeight(ui, 1.1f) + 6f, 1f, 1f, 0.75f, 0.3f, 1f);
    }
    final float cooldown = ship.hyperspaceCooldown;
    if (!inCockpit) {
      final var speed = String.format(Locale.ROOT, "SPEED %2.0f", ship.velocity.length());
      final float bottom = height - Hud.lineHeight(ui, 1.1f) - 14f;
      Hud.text(ui, speed, 16f, bottom, 1.1f, 0.75f, 0.8f, 0.9f, 1f);
      final var jump = cooldown > 0f ? String.format(Locale.ROOT, "JUMP %.0f", Math.ceil(cooldown)) : "JUMP READY  (E)";
      final float jumpX = 16f + Hud.width(ui, "SPEED 00", 1.1f) + 30f;
      if (cooldown > 0f) {
        Hud.text(ui, jump, jumpX, bottom, 1.1f, 0.6f, 0.62f, 0.7f, 1f);
      } else {
        Hud.text(ui, jump, jumpX, bottom, 1.1f, 0.6f, 0.85f, 1f, 1f);
      }
    }

    if (ship.alive) {
      shipHud(ui, width, height, ship.position, this.camera.transform().position());
    }
    if (this.time < this.messageUntil) {
      final float fade = Math.min(1f, (this.messageUntil - this.time) * 2f);
      Hud.centered(ui, this.message, width, height * 0.28f, 2.2f, 1f, 0.9f, 0.5f, fade);
    }
  }
}
