package astroids.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

/**
 * The game's rules, without any rendering: a cube of space that wraps around on all sides (fly out on one side, come
 * back on the other), asteroids drifting through it, the player's ship, bullets, and now and then an enemy ship (the
 * "boss").
 *
 * <ul>
 * <li>A big asteroid that is hit splits into two small ones; a small one is destroyed.</li>
 * <li>The enemy ship shoots at the player, takes a few hits, and leaves after a while. It comes a little more often with
 * every wave; from a later wave on, it may be a scout instead: small, quick, one hit, and it may swerve out of a shot's
 * way.</li>
 * <li>Touching an asteroid or the enemy, or being hit by its bullet, costs a life; the ship comes back in the middle
 * after a moment. Every ship, the first one too, is shielded for a few seconds. Every so many points bring an extra
 * ship.</li>
 * <li>Hyperspace jumps the ship to a random place in the arena, as a last resort: it might be a worse one.</li>
 * <li>When no asteroids are left, the next wave starts with one more big asteroid.</li>
 * </ul>
 *
 * The numbers (speeds, timings, counts, scores) come from a {@link Tuning}. {@link #step} advances everything by a
 * fixed time step; the events of the step are collected for sounds and effects. Deterministic for a seed and a tuning.
 */
public final class World {
  public static final float BIG_RADIUS = 5f;
  public static final float SMALL_RADIUS = 2.5f;
  public static final float SHIP_RADIUS = 1f;
  public static final float BULLET_RADIUS = 0.3f;
  public static final float BOSS_RADIUS = 2.4f;
  public static final float SCOUT_RADIUS = 1.44f;
  /** A scout notices player shots this close (and heading its way). */
  static final float DODGE_RANGE = 18f;

  private final Tuning t;
  private final Random random;
  private final List<Asteroid> asteroids = new ArrayList<>();
  private final List<Bullet> bullets = new ArrayList<>();
  private final List<GameEvent> events = new ArrayList<>();
  private final Ship ship;
  private Boss boss;
  private float bossDelay;
  private float nextWaveIn = -1f;
  private int score;
  private int lives;
  private int wave;
  private boolean gameOver;

  private final Vector3f tmp = new Vector3f();
  private final Vector3f tmp2 = new Vector3f();

  private World(long seed, boolean withShip, Tuning tuning) {
    this.t = tuning;
    this.random = new Random(seed);
    this.ship = withShip ? new Ship() : null;
    this.lives = tuning.lives;
    this.bossDelay = withShip ? enemyWait() : Float.POSITIVE_INFINITY;
  }

  /** A new game as released: the ship in the middle, the first wave around it. */
  public static World newGame(long seed) {
    return newGame(seed, new Tuning());
  }

  /** A new game with other numbers (playtesting). */
  public static World newGame(long seed, Tuning tuning) {
    final var w = new World(seed, true, tuning);
    w.ship.invulnerable = tuning.shieldTime; // like a respawned ship: the first rocks may already be heading its way
    w.startWave(1);
    return w;
  }

  /** Asteroids drifting about with no ship and no boss, for the title screen. */
  public static World attract(long seed, int asteroids) {
    final var w = new World(seed, false, new Tuning());
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

  public Tuning tuning() {
    return this.t;
  }

  /** Half the edge of the arena cube: positions stay within -arena..arena on every axis. */
  public float arena() {
    return this.t.arena;
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
    s.hyperspaceCooldown = Math.max(0f, s.hyperspaceCooldown - dt);
    if (!s.alive) {
      s.respawnIn -= dt;
      if (s.respawnIn <= 0f) {
        s.alive = true;
        s.position.zero();
        s.velocity.zero();
        s.invulnerable = this.t.shieldTime;
        this.events.add(new GameEvent.ShipRespawned());
      }
      return;
    }
    s.invulnerable = Math.max(0f, s.invulnerable - dt);
    if (c.hyperspace() && s.hyperspaceCooldown <= 0f) {
      // anywhere at all: away from trouble, or into worse
      final var from = new Vector3f(s.position);
      s.position.set(between(-arena(), arena()), between(-arena(), arena()), between(-arena(), arena()));
      s.velocity.zero();
      s.hyperspaceCooldown = this.t.hyperspaceCooldown;
      this.events.add(new GameEvent.Hyperspace(from, new Vector3f(s.position)));
    }
    final float thrust = Math.min(1f, c.thrust().length());
    if (thrust > 0f) {
      s.velocity.fma(this.t.shipAcceleration * dt * thrust / c.thrust().length(), c.thrust());
    }
    s.velocity.mul((float) Math.exp(-this.t.shipDamping * dt));
    if (s.velocity.length() > this.t.shipMaxSpeed) {
      s.velocity.normalize(this.t.shipMaxSpeed);
    }
    s.position.fma(dt, s.velocity);
    wrap(s.position);

    s.fireCooldown -= dt;
    if (c.fire() && s.fireCooldown <= 0f) {
      s.fireCooldown = this.t.fireInterval;
      final var aim = this.tmp.set(c.aim()).normalize();
      final var b = new Bullet(false);
      b.position.set(s.position).fma(SHIP_RADIUS + 0.5f, aim);
      b.velocity.set(aim).mul(this.t.bulletSpeed).add(s.velocity);
      b.timeLeft = this.t.bulletTime;
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
    final boolean scout = b.kind == Boss.Kind.SCOUT;
    b.timeLeft -= dt;
    if (b.timeLeft <= 0f) {
      this.boss = null;
      this.bossDelay = enemyWait();
      this.events.add(new GameEvent.BossLeft());
      return;
    }
    final float speed = scout ? this.t.scoutSpeed : this.t.enemySpeed;
    b.turnTimer -= dt;
    if (b.turnTimer <= 0f) {
      b.turnTimer = scout ? between(0.4f, 0.9f) : between(1.5f, 3f); // the scout jinks
      randomDirection(b.velocity).mul(speed);
    }
    if (scout) {
      dodge(b, speed);
    }
    b.position.fma(dt, b.velocity);
    wrap(b.position);
    b.fireTimer -= dt;
    if (b.fireTimer <= 0f && this.ship != null && this.ship.alive && !this.gameOver) {
      b.fireTimer = scout ? this.t.scoutFireInterval : this.t.enemyFireInterval;
      final var aim = delta(b.position, this.ship.position, this.tmp).normalize();
      // a little off, so that moving keeps the player safe
      final var off = randomDirection(this.tmp2).cross(aim);
      if (off.lengthSquared() > 1e-6f) {
        final float error = scout ? this.t.scoutAimErrorDegrees : this.t.enemyAimErrorDegrees;
        final float spread = (float) Math.tan(Math.toRadians(error));
        aim.fma(spread * this.random.nextFloat(), off.normalize()).normalize();
      }
      final var shot = new Bullet(true);
      shot.position.set(b.position).fma(b.radius() + 0.5f, aim);
      shot.velocity.set(aim).mul(this.t.enemyBulletSpeed);
      shot.timeLeft = this.t.enemyBulletTime;
      this.bullets.add(shot);
      this.events.add(new GameEvent.BossShot(new Vector3f(shot.position)));
    }
  }

  /**
   * The scout gets one look at each player shot that comes near and heads its way: with {@code scout.dodgeChance} it
   * swerves across the shot's path.
   */
  private void dodge(Boss b, float speed) {
    final var toScout = this.tmp;
    for (Bullet shot : this.bullets) {
      if (shot.fromBoss || shot.dodgeRolled) {
        continue;
      }
      delta(shot.position, b.position, toScout);
      final float along = toScout.dot(shot.velocity);
      if (along <= 0f || toScout.lengthSquared() > DODGE_RANGE * DODGE_RANGE) {
        continue; // flying away from it, or still far
      }
      shot.dodgeRolled = true;
      if (this.random.nextFloat() >= this.t.scoutDodgeChance) {
        continue;
      }
      // the part of the way to the scout that is across the shot's path: move further that way
      toScout.fma(-along / shot.velocity.lengthSquared(), shot.velocity);
      if (toScout.lengthSquared() < 1e-6f) {
        randomDirection(toScout).cross(shot.velocity); // dead on: any way across
      }
      b.velocity.set(toScout).normalize(speed);
      b.turnTimer = 0.8f;
      return;
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
        } else if (this.boss != null && touches(b.position, BULLET_RADIUS, this.boss.position, this.boss.radius())) {
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
      } else if (this.boss != null
          && touches(this.ship.position, SHIP_RADIUS, this.boss.position, this.boss.radius())) {
        bossDestroyed();
        destroyShip();
      }
    }
  }

  private void stepWaves(float dt) {
    if (!this.asteroids.isEmpty()) {
      return;
    }
    if (this.nextWaveIn < 0f) {
      this.nextWaveIn = this.t.waveDelay;
    }
    this.nextWaveIn -= dt;
    if (this.nextWaveIn <= 0f) {
      this.nextWaveIn = -1f;
      startWave(this.wave + 1);
    }
  }

  private void startWave(int number) {
    this.wave = number;
    final int count = Math.min(this.t.maxRocks, this.t.firstWaveRocks + number - 1);
    for (int i = 0; i < count; i++) {
      spawnBig();
    }
    this.events.add(new GameEvent.WaveStarted(number));
  }

  private void spawnBig() {
    final var a = new Asteroid(Asteroid.Size.BIG);
    do {
      a.position.set(between(-arena(), arena()), between(-arena(), arena()), between(-arena(), arena()));
    } while (this.ship != null && distance(a.position, this.ship.position) < this.t.safeDistance);
    randomDirection(a.velocity).mul(between(this.t.rockMinSpeed, this.t.rockMaxSpeed)
        + this.t.rockSpeedPerWave * Math.max(0, this.wave - 1));
    decorate(a);
    this.asteroids.add(a);
  }

  private void decorate(Asteroid a) {
    randomDirection(a.spinAxis);
    a.spinSpeed = between(0.2f, 1.2f);
    a.shape.set(between(0.8f, 1.2f), between(0.75f, 1.1f), between(0.85f, 1.25f));
    a.variant = this.random.nextInt(1 << 16);
  }

  /** Splits a big asteroid in two or removes a small one, and scores it. */
  private void breakAsteroid(Asteroid a, boolean scored) {
    this.asteroids.remove(a);
    if (scored) {
      addScore(a.size == Asteroid.Size.BIG ? this.t.scoreBigRock : this.t.scoreSmallRock);
    }
    this.events.add(new GameEvent.AsteroidDestroyed(new Vector3f(a.position), a.size, new Vector3f(a.velocity)));
    if (a.size == Asteroid.Size.BIG) {
      // the two halves fly apart across the parent's path
      final var apart = randomDirection(this.tmp2);
      final float speed = between(this.t.splitMinSpeed, this.t.splitMaxSpeed);
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
      addScore(this.t.scoreEnemyHit);
      this.events.add(new GameEvent.BossHit(new Vector3f(b.position)));
      return;
    }
    bossDestroyed();
  }

  private void bossDestroyed() {
    final var b = this.boss;
    final int points = b.kind == Boss.Kind.SCOUT ? this.t.scoreScoutDestroyed : this.t.scoreEnemyDestroyed;
    addScore(points);
    this.events.add(new GameEvent.BossDestroyed(new Vector3f(b.position), b.kind, points));
    this.boss = null;
    this.bossDelay = enemyWait();
  }

  /** How long until the next enemy ship: shorter with every wave, down to 40% of the first wave's. */
  private float enemyWait() {
    final float factor = Math.max(0.4f, 1f - this.t.enemyWaitCutPerWave * Math.max(0, this.wave - 1));
    return between(this.t.enemyMinDelay, this.t.enemyMaxDelay) * factor;
  }

  /** Adds points; every {@code ship.extraLifeEvery} of them bring an extra ship. */
  private void addScore(int points) {
    final int before = this.score;
    this.score += points;
    final int every = this.t.extraLifeEvery;
    if (every > 0 && this.ship != null && !this.gameOver) {
      final int extra = this.score / every - before / every;
      if (extra > 0) {
        this.lives += extra;
        this.events.add(new GameEvent.ExtraLife(this.lives));
      }
    }
  }

  private void spawnBoss() {
    final boolean scout = this.wave >= this.t.scoutFromWave && this.random.nextFloat() < this.t.scoutChance;
    spawnBoss(scout ? Boss.Kind.SCOUT : Boss.Kind.BIG);
  }

  private void spawnBoss(Boss.Kind kind) {
    final var b = new Boss(kind);
    // on a face of the arena, as far as possible from the ship
    do {
      b.position.set(between(-arena(), arena()), between(-arena(), arena()), between(-arena(), arena()));
      final int axis = this.random.nextInt(3);
      b.position.setComponent(axis, this.random.nextBoolean() ? arena() : -arena());
    } while (this.ship != null && distance(b.position, this.ship.position) < this.t.safeDistance);
    final boolean scout = kind == Boss.Kind.SCOUT;
    b.health = scout ? this.t.scoutHealth : this.t.enemyHealth;
    b.timeLeft = scout ? this.t.scoutStay : this.t.enemyStay;
    b.fireTimer = scout ? this.t.scoutFireInterval : this.t.enemyFireInterval;
    this.boss = b;
    this.events.add(new GameEvent.BossAppeared(new Vector3f(b.position), kind));
  }

  private void destroyShip() {
    final var s = this.ship;
    s.alive = false;
    s.respawnIn = this.t.respawnDelay;
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

  /** The shortest way from a to b in this arena, going through its walls if that is shorter. */
  public Vector3f delta(Vector3f from, Vector3f to, Vector3f dest) {
    return delta(from, to, dest, arena());
  }

  public float distance(Vector3f a, Vector3f b) {
    return delta(a, b, new Vector3f()).length();
  }

  /** Moves a point that left the arena back in from the opposite side. */
  public void wrap(Vector3f p) {
    wrap(p, arena());
  }

  /** The shortest way from a to b in an arena of half-size {@code arena}. */
  public static Vector3f delta(Vector3f from, Vector3f to, Vector3f dest, float arena) {
    dest.set(to).sub(from);
    for (int i = 0; i < 3; i++) {
      float d = dest.get(i);
      if (d > arena) {
        d -= 2f * arena;
      } else if (d < -arena) {
        d += 2f * arena;
      }
      dest.setComponent(i, d);
    }
    return dest;
  }

  public static void wrap(Vector3f p, float arena) {
    for (int i = 0; i < 3; i++) {
      float v = p.get(i);
      if (v > arena) {
        v -= 2f * arena;
      } else if (v < -arena) {
        v += 2f * arena;
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

  /** Brings the big enemy ship in now, for tests. */
  public Boss summonBoss() {
    return summon(Boss.Kind.BIG);
  }

  /** Brings an enemy ship of this kind in now, for tests. */
  public Boss summon(Boss.Kind kind) {
    spawnBoss(kind);
    return this.boss;
  }

  /** How long until the next enemy ship comes, for tests. */
  public float enemyDelay() {
    return this.bossDelay;
  }
}
