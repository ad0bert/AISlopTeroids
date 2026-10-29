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
  /**
   * Seconds over which a shot drawn at a gun closes in on where the model has it (on the line from the ship's middle,
   * through the crosshair).
   */
  static final float CONVERGE = 0.3f;
  /** Where the player's guns are now (in the world), taking turns to fire. */
  private final Vector3f[] guns = { new Vector3f(), new Vector3f() };
  private int nextGun;
  /** How far each of the player's shots was from its gun when it appeared: drawn there, then less and less. */
  private final Map<Bullet, Vector3f> fromGun = new IdentityHashMap<>();
  private float bulletTime = new astroids.model.Tuning().bulletTime;

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
    this.bulletTime = world.tuning().bulletTime;
    for (Bullet b : world.bullets()) {
      final var e = entityFor(b, () -> newBullet(b));
      place(e, b.position);
      final var offset = this.fromGun.get(b);
      if (offset != null) {
        final float fade = 1f - (this.bulletTime - b.timeLeft) / CONVERGE;
        if (fade <= 0f) {
          this.fromGun.remove(b);
          e.transform().rotation().rotationTo(FORWARD, this.tmp.set(b.velocity).normalize());
          e.transform().scale().set(0.12f, 0.12f, 1.1f);
        } else {
          e.transform().position().fma(fade, offset);
          // thin as it leaves the gun, right under the viewer's nose, and full size where it joins its line
          final float s = 1f - 0.75f * fade;
          e.transform().scale().set(0.12f * s, 0.12f * s, 1.1f * s);
          // heading for its line as well as along it
          e.transform().rotation().rotationTo(FORWARD,
              this.tmp.set(offset).mul(-1f / CONVERGE).add(b.velocity).normalize());
        }
      }
    }
    this.fromGun.keySet().retainAll(world.bullets());
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
    if (!b.fromBoss) {
      // drawn from the gun whose turn it is; the model has it on the line through the crosshair
      final var gun = this.guns[this.nextGun];
      this.nextGun = (this.nextGun + 1) % this.guns.length;
      final float age = this.bulletTime - b.timeLeft;
      final var offset = new Vector3f(gun).sub(e.transform().position()).fma(age, b.velocity);
      this.fromGun.put(b, offset);
    }
    return e;
  }

  /** Where the player's two guns are now, in the world: shots appear at them. Call before {@link #sync}. */
  void guns(Vector3f left, Vector3f right) {
    this.guns[0].set(left);
    this.guns[1].set(right);
  }

  private Entity newBoss(Boss.Kind kind) {
    final boolean scout = kind == Boss.Kind.SCOUT;
    final var e = new Entity(scout ? "scout" : "boss");
    e.transform().scale().set(kind.radius); // the parts are made for radius 1
    final var glow = scout ? Light.point(1f, 0.7f, 0.15f, 3f, 20f) : Light.point(1f, 0.25f, 0.15f, 4f, 30f);
    glow.castShadows = false;
    e.add(glow);
    for (var part : scout ? this.art.scoutParts : this.art.bossParts) {
      final var p = new Entity(e.name() + " part");
      p.add(new MeshRenderer(part.mesh(), part.material()));
      p.transform().position().set(part.position());
      p.transform().rotation().set(part.rotation());
      p.transform().scale().set(part.scale());
      p.transform().setParent(e.transform());
      this.scene.add(p);
      this.attached.computeIfAbsent(e, k -> new ArrayList<>()).add(p);
    }
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
        sparks(shown(h.position(), new Vector3f()), new Vector3f(), this.art.quality.scale(8), 0.06f, 22f);
        wreckage(shown(h.position(), new Vector3f()), new Vector3f(), this.art.quality.scale(3), 0.25f, 9f,
            this.art.bossHull, this.art.bossHull);
      }
      case GameEvent.BossDestroyed d -> {
        final boolean scout = d.kind() == Boss.Kind.SCOUT;
        sound(this.art.bigBoom, d.position(), 1.2f, scout ? 1f : 0.7f);
        shipBurst(shown(d.position(), new Vector3f()), d.velocity(), d.kind().radius,
            scout ? this.art.scoutHull : this.art.bossHull, scout ? this.art.scoutFin : this.art.bossPaint,
            scout ? 5 : 7, scout ? 12 : 18, scout ? 18 : 28);
      }
      case GameEvent.BossAppeared a -> audio.play2D(this.art.alarm, 0.8f, AudioBus.EFFECTS);
      case GameEvent.ShipDestroyed d -> {
        audio.play2D(this.art.shipBoom, 1f, AudioBus.EFFECTS);
        shipBurst(shown(d.position(), new Vector3f()), new Vector3f(), PLAYER_SIZE, this.art.playerHull,
            this.art.playerPaint, 6, 16, 24);
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
    final int chips = this.art.quality.scale(big ? 26 : 14);
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
    for (int i = 0, n = this.art.quality.scale(5); i < n; i++) { // sparks: quick and bright
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

  /** About how big the player's ship would be, for its explosion. */
  private static final float PLAYER_SIZE = 1.4f;

  /**
   * A ship blowing up at {@code at} (where it is shown): a flash of light, overlapping fireballs that swell, cool and
   * fade, torn pieces of its hull (in its own materials) tumbling away with its motion, embers streaking out, and a
   * cloud of smoke. The counts are for the highest quality; lower qualities make fewer.
   */
  private void shipBurst(Vector3f at, Vector3f shipVelocity, float r, Material hull, Material trim, int puffs,
      int pieces, int embers) {
    final var drift = new Vector3f(shipVelocity).mul(0.3f); // the cloud keeps a little of the ship's motion
    final var quality = this.art.quality;
    if (quality.explosionLight) {
      final var flash = new Entity("flash");
      flash.transform().position().set(at);
      final var light = Light.point(1f, 0.6f, 0.25f, 8f, r * 25f);
      light.castShadows = false;
      flash.add(light);
      flash.add(new Flash(this.scene, light, 0.7f));
      this.scene.add(flash);
    }

    // a ragged cluster of puffs of fire, one after another, rather than one ball
    for (int i = 0, n = quality.scale(puffs); i < n; i++) {
      final var ball = new Entity("fireball");
      final var fire = this.art.fireballMaterial();
      ball.add(new MeshRenderer(this.art.smoothBall, fire)).castShadows = false;
      ball.transform().position().set(at).fma(i == 0 ? 0f : r * (0.3f + 0.4f * this.random.nextFloat()),
          randomDirection(new Vector3f()));
      ball.transform().scale().set(0f);
      final float size = r * (i == 0 ? 1.2f : 0.6f + 0.5f * this.random.nextFloat());
      ball.add(new Blast(this.scene, fire, new Vector3f(drift).fma(2f, randomDirection(new Vector3f())),
          i == 0 ? 0f : 0.03f + 0.15f * this.random.nextFloat(), size * 0.25f, size,
          0.6f + 0.5f * this.random.nextFloat()));
      this.scene.add(ball);
    }

    wreckage(at, shipVelocity, quality.scale(pieces), r * 0.32f, 11f, hull, trim);
    sparks(at, drift, quality.scale(embers), 0.09f, 26f);

    final var smoke = new Entity("smoke");
    final var soot = this.art.smokeMaterial();
    smoke.add(new MeshRenderer(this.art.dust, soot));
    smoke.transform().position().set(at);
    smoke.add(new Dust(this.scene, soot, drift, r * 0.5f, r * 3.2f, 2.6f));
    this.scene.add(smoke);
  }

  /** Torn pieces of hull: mostly small, a few large, flung out and tumbling, shrinking away only at the end. */
  private void wreckage(Vector3f at, Vector3f velocity, int count, float largest, float speed, Material hull,
      Material trim) {
    for (int i = 0; i < count; i++) {
      final var dir = randomDirection(new Vector3f());
      final float share = 0.2f + 0.8f * (float) Math.pow(this.random.nextFloat(), 2.5);
      final float size = largest * share;
      final var e = new Entity("wreckage");
      e.add(new MeshRenderer(this.art.shards[this.random.nextInt(this.art.shards.length)],
          this.random.nextFloat() < 0.75f ? hull : trim));
      e.transform().position().set(at).fma(largest * this.random.nextFloat(), dir);
      e.transform().rotation().rotationAxis((float) (2 * Math.PI * this.random.nextFloat()),
          randomDirection(new Vector3f()));
      e.transform().scale().set(size);
      e.add(new Debris(this.scene, new Vector3f(dir).mul(speed * (0.5f + this.random.nextFloat()) / (0.4f + share))
          .add(velocity), randomDirection(new Vector3f()), 2f + 8f * this.random.nextFloat(),
          1.8f + 1.7f * this.random.nextFloat(), size, 0.3f, 0.1f));
      this.scene.add(e);
    }
  }

  /** Glowing embers streaking out, quick to burn out. */
  private void sparks(Vector3f at, Vector3f velocity, int count, float size, float speed) {
    for (int i = 0; i < count; i++) {
      final var e = new Entity("ember");
      e.add(new MeshRenderer(this.art.ball, this.art.fire)).castShadows = false;
      e.transform().position().set(at);
      final float s = size * (0.5f + this.random.nextFloat());
      e.transform().scale().set(s);
      e.add(new Debris(this.scene, randomDirection(new Vector3f()).mul(speed * (0.5f + this.random.nextFloat()))
          .add(velocity), randomDirection(new Vector3f()), 6f, 0.4f + 0.6f * this.random.nextFloat(), s));
      this.scene.add(e);
    }
  }

  private Vector3f randomDirection(Vector3f dest) {
    final float z = this.random.nextFloat() * 2f - 1f;
    final float a = (float) (2 * Math.PI * this.random.nextFloat());
    final float r = (float) Math.sqrt(1f - z * z);
    return dest.set(r * (float) Math.cos(a), r * (float) Math.sin(a), z);
  }
}
