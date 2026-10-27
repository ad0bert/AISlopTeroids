package astroids;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import astroids.model.Asteroid;
import astroids.model.Boss;
import astroids.model.Bullet;
import astroids.model.GameEvent;
import astroids.model.World;
import engine.audio.AudioBus;
import engine.audio.AudioClip;
import engine.core.Engine;
import engine.ecs.Entity;
import engine.ecs.Scene;
import engine.render.Light;
import engine.render.Material;
import engine.render.MeshRenderer;

/**
 * Shows a {@link World} with engine entities: one per asteroid, bullet and boss, created and removed as the model's
 * objects come and go, plus explosion debris and sounds for its events.
 *
 * <p>
 * The arena wraps around, so everything is drawn where it is nearest to the viewer (through a wall if that is
 * shorter): the edges of the arena are never seen, space just goes on.
 */
final class WorldView {
  private final Engine engine;
  private final Art art;
  private final Scene scene;
  private final Map<Object, Entity> entities = new IdentityHashMap<>();
  private final Map<Object, Boolean> seen = new IdentityHashMap<>();
  /** Child entities (the boss's dome) that go when their parent goes. */
  private final Map<Entity, List<Entity>> attached = new IdentityHashMap<>();
  private final Random random = new Random(11L);
  private final Vector3f viewer = new Vector3f();
  /** The arena's half-size, from the world last synced. */
  private float arena = new astroids.model.Tuning().arena;
  private final Vector3f tmp = new Vector3f();
  private final Quaternionf turn = new Quaternionf();
  private static final Vector3f FORWARD = new Vector3f(0f, 0f, 1f);

  WorldView(Engine engine, Art art) {
    this.engine = engine;
    this.art = art;
    this.scene = engine.scene();
  }

  /** Moves the entities to the model's state, as seen from {@code viewer}. Call after every world step. */
  void sync(World world, Vector3f viewerPosition, float dt) {
    this.viewer.set(viewerPosition);
    this.arena = world.arena();
    this.seen.clear();
    for (Asteroid a : world.asteroids()) {
      final var e = entityFor(a, () -> newAsteroid(a));
      place(e, a.position);
      e.transform().rotation().rotateAxis(a.spinSpeed * dt, a.spinAxis);
    }
    for (Bullet b : world.bullets()) {
      final var e = entityFor(b, () -> newBullet(b));
      place(e, b.position);
    }
    final Boss boss = world.boss();
    if (boss != null) {
      final var e = entityFor(boss, () -> newBoss(boss.kind));
      place(e, boss.position);
      e.transform().rotation().rotateY((boss.kind == Boss.Kind.SCOUT ? 4f : 1.5f) * dt);
    }
    // what the model no longer has
    final var it = this.entities.entrySet().iterator();
    while (it.hasNext()) {
      final var entry = it.next();
      if (!this.seen.containsKey(entry.getKey())) {
        this.scene.remove(entry.getValue());
        final var children = this.attached.remove(entry.getValue());
        if (children != null) {
          children.forEach(this.scene::remove);
        }
        it.remove();
      }
    }
  }

  /** The point where something at {@code position} is drawn: its copy nearest to the viewer. */
  Vector3f shown(Vector3f position, Vector3f dest) {
    return World.delta(this.viewer, position, dest, this.arena).add(this.viewer);
  }

  private Entity entityFor(Object model, java.util.function.Supplier<Entity> create) {
    this.seen.put(model, Boolean.TRUE);
    var e = this.entities.get(model);
    if (e == null) {
      e = create.get();
      this.entities.put(model, e);
      this.scene.add(e);
    }
    return e;
  }

  private void place(Entity e, Vector3f position) {
    final var p = e.transform().position();
    final var target = shown(position, this.tmp);
    final boolean jumped = p.distanceSquared(target) > this.arena * this.arena;
    p.set(target);
    if (jumped) {
      e.transform().snapshot(); // it came round the far side: no smear across the screen
    }
  }

  private Entity newAsteroid(Asteroid a) {
    final var e = new Entity(a.size == Asteroid.Size.BIG ? "big rock" : "small rock");
    e.add(new MeshRenderer(this.art.rocks[Math.floorMod(a.variant, this.art.rocks.length)],
        a.size == Asteroid.Size.BIG ? this.art.rockMaterial : this.art.smallRockMaterial));
    e.transform().scale().set(a.shape).mul(a.radius());
    shown(a.position, e.transform().position());
    return e;
  }

  private Entity newBullet(Bullet b) {
    final var e = new Entity(b.fromBoss ? "boss shot" : "shot");
    e.add(new MeshRenderer(this.art.ball, b.fromBoss ? this.art.bossShotMaterial : this.art.shotMaterial));
    // stretched along its flight
    e.transform().scale().set(b.fromBoss ? 0.35f : 0.12f, b.fromBoss ? 0.35f : 0.12f, b.fromBoss ? 0.7f : 1.1f);
    e.transform().rotation().rotationTo(FORWARD, this.tmp.set(b.velocity).normalize());
    shown(b.position, e.transform().position());
    return e;
  }

  private Entity newBoss(Boss.Kind kind) {
    final boolean scout = kind == Boss.Kind.SCOUT;
    final float r = kind.radius;
    final var e = new Entity(scout ? "scout" : "boss");
    e.add(new MeshRenderer(this.art.ball, scout ? this.art.scoutHull : this.art.bossHull));
    e.transform().scale().set(r * 1.1f, r * (scout ? 0.28f : 0.35f), r * 1.1f);
    final var dome = new Entity(scout ? "scout dome" : "boss dome");
    dome.add(new MeshRenderer(this.art.ball, scout ? this.art.scoutDome : this.art.bossDome));
    dome.transform().position().set(0f, 0.6f, 0f);
    dome.transform().scale().set(0.45f, 1.1f, 0.45f);
    dome.transform().setParent(e.transform());
    this.scene.add(dome);
    this.attached.computeIfAbsent(e, k -> new ArrayList<>()).add(dome);
    final var glow = scout ? Light.point(1f, 0.7f, 0.15f, 3f, 20f) : Light.point(1f, 0.25f, 0.15f, 4f, 30f);
    glow.castShadows = false;
    e.add(glow);
    return e;
  }

  /** Sounds and debris for what happened in the last step; call after {@link #sync}. */
  void play(Iterable<GameEvent> events) {
    final var audio = this.engine.audio();
    for (GameEvent event : events) {
      switch (event) {
      case GameEvent.Shot s -> audio.play2D(this.art.shot, 0.45f, AudioBus.EFFECTS);
      case GameEvent.BossShot s -> sound(this.art.bossShot, s.position(), 1f, 1f);
      case GameEvent.AsteroidDestroyed d -> {
        final boolean big = d.size() == Asteroid.Size.BIG;
        sound(big ? this.art.bigBoom : this.art.smallBoom, d.position(), 1f, 0.9f + 0.2f * this.random.nextFloat());
        rockBurst(d.position(), d.velocity(), big);
      }
      case GameEvent.BossHit h -> {
        sound(this.art.clang, h.position(), 1f, 1f);
        burst(h.position(), 5, 0.4f, 10f, this.art.fire);
      }
      case GameEvent.BossDestroyed d -> {
        final boolean scout = d.kind() == Boss.Kind.SCOUT;
        sound(this.art.bigBoom, d.position(), 1.2f, scout ? 1f : 0.7f);
        burst(d.position(), scout ? 12 : 18, 0.7f, 16f, this.art.fire);
        burst(d.position(), scout ? 6 : 10, 0.8f, 10f, scout ? this.art.scoutHull : this.art.bossHull);
      }
      case GameEvent.BossAppeared a -> audio.play2D(this.art.alarm, 0.8f, AudioBus.EFFECTS);
      case GameEvent.ShipDestroyed d -> {
        audio.play2D(this.art.shipBoom, 1f, AudioBus.EFFECTS);
        burst(d.position(), 16, 0.5f, 14f, this.art.fire);
      }
      case GameEvent.WaveStarted w -> audio.play2D(this.art.chime, 0.7f, AudioBus.EFFECTS);
      case GameEvent.BossLeft l -> {
      }
      case GameEvent.ShipRespawned r -> {
      }
      case GameEvent.GameOver g -> {
      }
      case GameEvent.ExtraLife l -> audio.play2D(this.art.extraLife, 0.8f, AudioBus.EFFECTS);
      case GameEvent.Hyperspace h -> audio.play2D(this.art.warp, 0.9f, AudioBus.EFFECTS);
      }
    }
  }

  private void sound(AudioClip clip, Vector3f position, float volume, float pitch) {
    this.engine.audio().play(clip, shown(position, new Vector3f()), volume, pitch, AudioBus.EFFECTS, 1);
  }

  /**
   * A rock breaking up: chips of rock of all sizes (many small, a few large) flung out from all through it and flying on
   * with its motion, a cloud of dust spreading and fading, and a few sparks where it was hit.
   */
  private void rockBurst(Vector3f position, Vector3f rockVelocity, boolean big) {
    final var at = shown(position, new Vector3f());
    final float radius = big ? World.BIG_RADIUS : World.SMALL_RADIUS;
    final var material = big ? this.art.rockMaterial : this.art.smallRockMaterial;
    final int chips = big ? 26 : 14;
    for (int i = 0; i < chips; i++) {
      final var dir = randomDirection(new Vector3f());
      final float share = 0.12f + 0.88f * (float) Math.pow(this.random.nextFloat(), 3); // mostly small
      final float size = radius * 0.3f * share;
      final float speed = (big ? 7f : 6f) * (0.6f + 0.8f * this.random.nextFloat()) / (0.35f + share);
      final var e = new Entity("rock chip");
      e.add(new MeshRenderer(this.art.chips[this.random.nextInt(this.art.chips.length)], material));
      e.transform().position().set(at).fma(radius * 0.6f * this.random.nextFloat(), dir);
      e.transform().rotation().rotationAxis((float) (2 * Math.PI * this.random.nextFloat()),
          randomDirection(new Vector3f()));
      e.transform().scale().set(size);
      e.add(new Debris(this.scene, new Vector3f(dir).mul(speed).add(rockVelocity), randomDirection(new Vector3f()),
          1f + 5f * this.random.nextFloat(), 1.6f + 1.6f * this.random.nextFloat(), size, 0.35f, 0.15f));
      this.scene.add(e);
    }
    final var cloud = new Entity("dust");
    final var dustMaterial = this.art.dustMaterial(big);
    cloud.add(new MeshRenderer(this.art.dust, dustMaterial));
    cloud.transform().position().set(at);
    cloud.transform().rotation().rotationAxis((float) (2 * Math.PI * this.random.nextFloat()),
        randomDirection(new Vector3f()));
    cloud.add(new Dust(this.scene, dustMaterial, new Vector3f(rockVelocity).mul(0.9f), radius * 0.6f,
        radius * (big ? 3.2f : 2.6f), 1.4f + 0.6f * this.random.nextFloat()));
    this.scene.add(cloud);
    for (int i = 0; i < 5; i++) { // sparks: quick and bright
      final var e = new Entity("spark");
      e.add(new MeshRenderer(this.art.ball, this.art.fire));
      e.transform().position().set(at);
      final float size = 0.08f + 0.08f * this.random.nextFloat();
      e.transform().scale().set(size);
      e.add(new Debris(this.scene, randomDirection(new Vector3f()).mul(18f + 14f * this.random.nextFloat())
          .add(rockVelocity), new Vector3f(0f, 1f, 0f), 0f, 0.2f + 0.2f * this.random.nextFloat(), size));
      this.scene.add(e);
    }
  }

  private Vector3f randomDirection(Vector3f dest) {
    final float z = this.random.nextFloat() * 2f - 1f;
    final float a = (float) (2 * Math.PI * this.random.nextFloat());
    final float r = (float) Math.sqrt(1f - z * z);
    return dest.set(r * (float) Math.cos(a), r * (float) Math.sin(a), z);
  }

  private void burst(Vector3f position, int count, float size, float speed, Material material) {
    final var at = shown(position, new Vector3f());
    for (int i = 0; i < count; i++) {
      final var e = new Entity("debris");
      e.add(new MeshRenderer(this.art.cube, material));
      e.transform().position().set(at);
      final var dir = new Vector3f(this.random.nextFloat() - 0.5f, this.random.nextFloat() - 0.5f,
          this.random.nextFloat() - 0.5f).normalize();
      final float s = size * (0.5f + this.random.nextFloat());
      e.add(new Debris(this.scene, dir.mul(speed * (0.4f + this.random.nextFloat())),
          new Vector3f(this.random.nextFloat(), this.random.nextFloat(), this.random.nextFloat()).add(0.1f, 0.1f, 0.1f),
          2f + 6f * this.random.nextFloat(), 0.7f + 0.8f * this.random.nextFloat(), s));
      e.transform().scale().set(s);
      this.scene.add(e);
    }
  }
}
