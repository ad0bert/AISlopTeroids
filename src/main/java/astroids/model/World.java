package astroids.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

/**
 * The game's rules, without any rendering: a cube of space that wraps around on all sides (fly out on one side, come
 * back on the other), asteroids drifting through it, the player's ship, bullets, and now and then the boss ship.
 *
 * <ul>
 * <li>A big asteroid that is hit splits into two small ones; a small one is destroyed.</li>
 * <li>The boss shoots at the player, takes {@value #BOSS_HEALTH} hits, and leaves after {@value #BOSS_STAY}
 * seconds.</li>
 * <li>Touching an asteroid or the boss, or being hit by a boss bullet, costs a life; the ship comes back in the middle
 * after {@value #RESPAWN_DELAY} seconds and is safe for {@value #INVULNERABLE_TIME} seconds.</li>
 * <li>When no asteroids are left, the next wave starts with one more big asteroid.</li>
 * </ul>
 *
 * {@link #step} advances everything by a fixed time step; the events of the step are collected for sounds and effects.
 * Deterministic for a given seed.
 */
public final class World {
  /** Half the edge of the arena cube: positions stay within -ARENA..ARENA on every axis. */
  public static final float ARENA = 60f;
  public static final float BIG_RADIUS = 5f;
  public static final float SMALL_RADIUS = 2.5f;
  public static final int BIG_SCORE = 20;
  public static final int SMALL_SCORE = 50;

  public static final float SHIP_RADIUS = 1f;
  public static final float SHIP_ACCELERATION = 30f;
  public static final float SHIP_MAX_SPEED = 25f;
  /** How fast the ship coasts to a stop without thrust (per second, exponential). */
  public static final float SHIP_DAMPING = 0.8f;
  public static final int START_LIVES = 3;
  public static final float RESPAWN_DELAY = 2f;
  public static final float INVULNERABLE_TIME = 3f;

  public static final float BULLET_SPEED = 70f;
  public static final float BULLET_TIME = 1.1f;
  public static final float FIRE_INTERVAL = 0.16f;
  public static final float BULLET_RADIUS = 0.3f;

  public static final float BOSS_RADIUS = 2f;
  public static final int BOSS_HEALTH = 3;
  public static final int BOSS_HIT_SCORE = 100;
  public static final int BOSS_SCORE = 1000;
  public static final float BOSS_SPEED = 9f;
  public static final float BOSS_STAY = 25f;
  public static final float BOSS_FIRE_INTERVAL = 1.4f;
  public static final float BOSS_BULLET_SPEED = 32f;
  public static final float BOSS_BULLET_TIME = 3f;
  /** How far off (radians) the boss's aim may be. */
  public static final float BOSS_SPREAD = 0.06f;
  /** Seconds between one boss leaving (or dying) and the next arriving. */
  public static final float BOSS_MIN_DELAY = 20f;
  public static final float BOSS_MAX_DELAY = 35f;

  public static final float WAVE_DELAY = 2.5f;
  public static final int MAX_BIG_ASTEROIDS = 12;
  /** New asteroids appear at least this far from the ship. */
  public static final float SAFE_DISTANCE = 25f;

  private final Random random;
  private final List<Asteroid> asteroids = new ArrayList<>();
  private final List<Bullet> bullets = new ArrayList<>();
  private final List<GameEvent> events = new ArrayList<>();
  private final Ship ship;
  private Boss boss;
  private float bossDelay;
  private float nextWaveIn = -1f;
  private int score;
  private int lives = START_LIVES;
  private int wave;
  private boolean gameOver;

  private final Vector3f tmp = new Vector3f();
  private final Vector3f tmp2 = new Vector3f();

  private World(long seed, boolean withShip) {
    this.random = new Random(seed);
    this.ship = withShip ? new Ship() : null;
    this.bossDelay = withShip ? between(BOSS_MIN_DELAY, BOSS_MAX_DELAY) : Float.POSITIVE_INFINITY;
  }

  /** A new game: the ship in the middle, the first wave around it. */
  public static World newGame(long seed) {
    final var w = new World(seed, true);
    w.startWave(1);
    return w;
  }

  /** Asteroids drifting about with no ship and no boss, for the title screen. */
  public static World attract(long seed, int asteroids) {
    final var w = new World(seed, false);
    for (int i = 0; i < asteroids; i++) {
      w.spawnBig();
    }
    w.gameOver = true;
    return w;
  }

  // ----- reading

  public List<Asteroid> asteroids() {
    return Collections.unmodifiableList(this.asteroids);
  }

  public List<Bullet> bullets() {
    return Collections.unmodifiableList(this.bullets);
  }

  /** The player's ship, or null in an attract world. */
  public Ship ship() {
    return this.ship;
  }

  /** The boss, or null while there is none. */
  public Boss boss() {
    return this.boss;
  }

  public int score() {
    return this.score;
  }

  public int lives() {
    return this.lives;
  }

  public int wave() {
    return this.wave;
  }

  public boolean isGameOver() {
    return this.gameOver;
  }

  /** The events since the last call, oldest first; the list is emptied. */
  public List<GameEvent> drainEvents() {
    final var out = List.copyOf(this.events);
    this.events.clear();
    return out;
  }

  // ----- simulation

  public void step(float dt, Controls controls) {
    if (this.ship != null && !this.gameOver) {
      stepShip(dt, controls);
    }
    for (Asteroid a : this.asteroids) {
      a.position.fma(dt, a.velocity);
      wrap(a.position);
    }
    stepBoss(dt);
    for (int i = this.bullets.size() - 1; i >= 0; i--) {
      final var b = this.bullets.get(i);
      b.position.fma(dt, b.velocity);
      wrap(b.position);
      b.timeLeft -= dt;
      if (b.timeLeft <= 0f) {
        this.bullets.remove(i);
      }
    }
    collide();
    if (this.ship != null && !this.gameOver) {
      stepWaves(dt);
    }
  }

  private void stepShip(float dt, Controls c) {
    final var s = this.ship;
    if (!s.alive) {
      s.respawnIn -= dt;
      if (s.respawnIn <= 0f) {
        s.alive = true;
        s.position.zero();
        s.velocity.zero();
        s.invulnerable = INVULNERABLE_TIME;
        this.events.add(new GameEvent.ShipRespawned());
      }
      return;
    }
    s.invulnerable = Math.max(0f, s.invulnerable - dt);
    final float thrust = Math.min(1f, c.thrust().length());
    if (thrust > 0f) {
      s.velocity.fma(SHIP_ACCELERATION * dt * thrust / c.thrust().length(), c.thrust());
    }
    s.velocity.mul((float) Math.exp(-SHIP_DAMPING * dt));
    if (s.velocity.length() > SHIP_MAX_SPEED) {
      s.velocity.normalize(SHIP_MAX_SPEED);
    }
    s.position.fma(dt, s.velocity);
    wrap(s.position);

    s.fireCooldown -= dt;
    if (c.fire() && s.fireCooldown <= 0f) {
      s.fireCooldown = FIRE_INTERVAL;
      final var aim = this.tmp.set(c.aim()).normalize();
      final var b = new Bullet(false);
      b.position.set(s.position).fma(SHIP_RADIUS + 0.5f, aim);
      b.velocity.set(aim).mul(BULLET_SPEED).add(s.velocity);
      b.timeLeft = BULLET_TIME;
      this.bullets.add(b);
      this.events.add(new GameEvent.Shot(new Vector3f(b.position)));
    }
  }

  private void stepBoss(float dt) {
    if (this.boss == null) {
      if (this.ship == null || this.gameOver) {
        return;
      }
      this.bossDelay -= dt;
      if (this.bossDelay <= 0f) {
        spawnBoss();
      }
      return;
    }
    final var b = this.boss;
    b.timeLeft -= dt;
    if (b.timeLeft <= 0f) {
      this.boss = null;
      this.bossDelay = between(BOSS_MIN_DELAY, BOSS_MAX_DELAY);
      this.events.add(new GameEvent.BossLeft());
      return;
    }
    b.turnTimer -= dt;
    if (b.turnTimer <= 0f) {
      b.turnTimer = between(1.5f, 3f);
      randomDirection(b.velocity).mul(BOSS_SPEED);
    }
    b.position.fma(dt, b.velocity);
    wrap(b.position);
    b.fireTimer -= dt;
    if (b.fireTimer <= 0f && this.ship != null && this.ship.alive && !this.gameOver) {
      b.fireTimer = BOSS_FIRE_INTERVAL;
      final var aim = delta(b.position, this.ship.position, this.tmp).normalize();
      // a little off, so that moving keeps the player safe
      final var off = randomDirection(this.tmp2).cross(aim);
      if (off.lengthSquared() > 1e-6f) {
        aim.fma((float) Math.tan(BOSS_SPREAD) * this.random.nextFloat(), off.normalize()).normalize();
      }
      final var shot = new Bullet(true);
      shot.position.set(b.position).fma(BOSS_RADIUS + 0.5f, aim);
      shot.velocity.set(aim).mul(BOSS_BULLET_SPEED);
      shot.timeLeft = BOSS_BULLET_TIME;
      this.bullets.add(shot);
      this.events.add(new GameEvent.BossShot(new Vector3f(shot.position)));
    }
  }

  private void collide() {
    // bullets against what they can hit
    for (int i = this.bullets.size() - 1; i >= 0; i--) {
      final var b = this.bullets.get(i);
      boolean used = false;
      if (b.fromBoss) {
        if (this.ship != null && this.ship.canBeHit()
            && touches(b.position, BULLET_RADIUS, this.ship.position, SHIP_RADIUS)) {
          destroyShip();
          used = true;
        }
      } else {
        final var hit = asteroidAt(b.position, BULLET_RADIUS);
        if (hit != null) {
          breakAsteroid(hit, true);
          used = true;
        } else if (this.boss != null && touches(b.position, BULLET_RADIUS, this.boss.position, BOSS_RADIUS)) {
          hitBoss();
          used = true;
        }
      }
      if (used) {
        this.bullets.remove(i);
      }
    }
    // the ship against rocks and the boss
    if (this.ship != null && this.ship.canBeHit() && !this.gameOver) {
      final var rock = asteroidAt(this.ship.position, SHIP_RADIUS);
      if (rock != null) {
        breakAsteroid(rock, true);
        destroyShip();
      } else if (this.boss != null && touches(this.ship.position, SHIP_RADIUS, this.boss.position, BOSS_RADIUS)) {
        this.score += BOSS_SCORE;
        this.events.add(new GameEvent.BossDestroyed(new Vector3f(this.boss.position)));
        this.boss = null;
        this.bossDelay = between(BOSS_MIN_DELAY, BOSS_MAX_DELAY);
        destroyShip();
      }
    }
  }

  private void stepWaves(float dt) {
    if (!this.asteroids.isEmpty()) {
      return;
    }
    if (this.nextWaveIn < 0f) {
      this.nextWaveIn = WAVE_DELAY;
    }
    this.nextWaveIn -= dt;
    if (this.nextWaveIn <= 0f) {
      this.nextWaveIn = -1f;
      startWave(this.wave + 1);
    }
  }

  private void startWave(int number) {
    this.wave = number;
    final int count = Math.min(MAX_BIG_ASTEROIDS, 3 + number);
    for (int i = 0; i < count; i++) {
      spawnBig();
    }
    this.events.add(new GameEvent.WaveStarted(number));
  }

  private void spawnBig() {
    final var a = new Asteroid(Asteroid.Size.BIG);
    do {
      a.position.set(between(-ARENA, ARENA), between(-ARENA, ARENA), between(-ARENA, ARENA));
    } while (this.ship != null && distance(a.position, this.ship.position) < SAFE_DISTANCE);
    randomDirection(a.velocity).mul(between(3f, 6f) + 0.4f * Math.max(0, this.wave - 1));
    decorate(a);
    this.asteroids.add(a);
  }

  private void decorate(Asteroid a) {
    randomDirection(a.spinAxis);
    a.spinSpeed = between(0.2f, 1.2f);
    a.shape.set(between(0.8f, 1.2f), between(0.75f, 1.1f), between(0.85f, 1.25f));
  }

  /** Splits a big asteroid in two or removes a small one, and scores it. */
  private void breakAsteroid(Asteroid a, boolean scored) {
    this.asteroids.remove(a);
    if (scored) {
      this.score += a.size.score;
    }
    this.events.add(new GameEvent.AsteroidDestroyed(new Vector3f(a.position), a.size));
    if (a.size == Asteroid.Size.BIG) {
      // the two halves fly apart across the parent's path
      final var apart = randomDirection(this.tmp2);
      final float speed = between(4f, 7f);
      for (int side = -1; side <= 1; side += 2) {
        final var piece = new Asteroid(Asteroid.Size.SMALL);
        piece.position.set(a.position).fma(side * SMALL_RADIUS * 1.1f, apart);
        wrap(piece.position);
        piece.velocity.set(a.velocity).fma(side * speed, apart);
        decorate(piece);
        this.asteroids.add(piece);
      }
    }
  }

  private void hitBoss() {
    final var b = this.boss;
    b.health--;
    if (b.health > 0) {
      this.score += BOSS_HIT_SCORE;
      this.events.add(new GameEvent.BossHit(new Vector3f(b.position)));
      return;
    }
    this.score += BOSS_SCORE;
    this.events.add(new GameEvent.BossDestroyed(new Vector3f(b.position)));
    this.boss = null;
    this.bossDelay = between(BOSS_MIN_DELAY, BOSS_MAX_DELAY);
  }

  private void spawnBoss() {
    final var b = new Boss();
    // on a face of the arena, as far as possible from the ship
    do {
      b.position.set(between(-ARENA, ARENA), between(-ARENA, ARENA), between(-ARENA, ARENA));
      final int axis = this.random.nextInt(3);
      b.position.setComponent(axis, this.random.nextBoolean() ? ARENA : -ARENA);
    } while (this.ship != null && distance(b.position, this.ship.position) < SAFE_DISTANCE);
    b.timeLeft = BOSS_STAY;
    b.fireTimer = BOSS_FIRE_INTERVAL;
    this.boss = b;
    this.events.add(new GameEvent.BossAppeared(new Vector3f(b.position)));
  }

  private void destroyShip() {
    final var s = this.ship;
    s.alive = false;
    s.respawnIn = RESPAWN_DELAY;
    s.velocity.zero();
    this.lives--;
    this.events.add(new GameEvent.ShipDestroyed(new Vector3f(s.position)));
    if (this.lives <= 0) {
      this.gameOver = true;
      this.events.add(new GameEvent.GameOver(this.score));
    }
  }

  // ----- geometry on the wrapping arena

  private Asteroid asteroidAt(Vector3f p, float radius) {
    for (Asteroid a : this.asteroids) {
      if (touches(p, radius, a.position, a.radius())) {
        return a;
      }
    }
    return null;
  }

  private boolean touches(Vector3f a, float ra, Vector3f b, float rb) {
    final float r = ra + rb;
    return delta(a, b, this.tmp2.zero()).lengthSquared() <= r * r;
  }

  /** The shortest way from a to b, going through the arena's walls if that is shorter. */
  public static Vector3f delta(Vector3f from, Vector3f to, Vector3f dest) {
    dest.set(to).sub(from);
    for (int i = 0; i < 3; i++) {
      float d = dest.get(i);
      if (d > ARENA) {
        d -= 2f * ARENA;
      } else if (d < -ARENA) {
        d += 2f * ARENA;
      }
      dest.setComponent(i, d);
    }
    return dest;
  }

  public static float distance(Vector3f a, Vector3f b) {
    return delta(a, b, new Vector3f()).length();
  }

  /** Moves a point that left the arena back in from the opposite side. */
  public static void wrap(Vector3f p) {
    for (int i = 0; i < 3; i++) {
      float v = p.get(i);
      if (v > ARENA) {
        v -= 2f * ARENA;
      } else if (v < -ARENA) {
        v += 2f * ARENA;
      }
      p.setComponent(i, v);
    }
  }

  private Vector3f randomDirection(Vector3f dest) {
    // uniform on the sphere
    final float z = between(-1f, 1f);
    final float angle = between(0f, (float) (2 * Math.PI));
    final float r = (float) Math.sqrt(1f - z * z);
    return dest.set(r * (float) Math.cos(angle), r * (float) Math.sin(angle), z);
  }

  private float between(float min, float max) {
    return min + this.random.nextFloat() * (max - min);
  }

  // ----- for tests

  /** Puts an asteroid somewhere, for tests and set pieces. */
  public Asteroid addAsteroid(Asteroid.Size size, Vector3f position, Vector3f velocity) {
    final var a = new Asteroid(size);
    a.position.set(position);
    a.velocity.set(velocity);
    decorate(a);
    this.asteroids.add(a);
    return a;
  }

  /** Removes every asteroid, for tests. */
  public void clearAsteroids() {
    this.asteroids.clear();
  }

  /** Brings the boss in now, for tests. */
  public Boss summonBoss() {
    spawnBoss();
    return this.boss;
  }
}
