package astroids.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WorldTest {
  private static final float DT = 1f / 60f;

  private static Controls fireAt(Vector3f aim) {
    return new Controls(new Vector3f(), new Vector3f(aim).normalize(), true);
  }

  /** A world with only what the test puts in: no asteroids, and the ship safe to be hit. */
  private static World empty() {
    final var w = World.newGame(1L);
    w.clearAsteroids();
    w.ship().invulnerable = 0f;
    w.drainEvents();
    return w;
  }

  private static void run(World w, float seconds, Controls c) {
    for (float t = 0f; t < seconds; t += DT) {
      w.step(DT, c);
    }
  }

  @Test
  void aNewGameStartsWithWaveOneAwayFromTheShip() {
    final var w = World.newGame(42L);
    assertEquals(1, w.wave());
    assertEquals(World.START_LIVES, w.lives());
    assertEquals(4, w.asteroids().size());
    for (Asteroid a : w.asteroids()) {
      assertEquals(Asteroid.Size.BIG, a.size);
      assertTrue(World.distance(a.position, w.ship().position) >= World.SAFE_DISTANCE);
    }
    assertEquals(List.of(new GameEvent.WaveStarted(1)), w.drainEvents());
    assertTrue(w.drainEvents().isEmpty(), "drained");
  }

  @Test
  void aBigAsteroidSplitsInTwoSmallOnesAndASmallOneIsDestroyed() {
    final var w = empty();
    w.addAsteroid(Asteroid.Size.BIG, new Vector3f(0f, 0f, -20f), new Vector3f());
    w.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w, 0.5f, Controls.NONE);

    final var pieces = w.asteroids();
    assertEquals(2, pieces.size(), "one shot split it");
    assertTrue(pieces.stream().allMatch(a -> a.size == Asteroid.Size.SMALL));
    assertTrue(w.score() >= World.BIG_SCORE);
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.AsteroidDestroyed d
        && d.size() == Asteroid.Size.BIG));

    // shoot each small one: it goes away without splitting
    final var w2 = empty();
    final var small = w2.addAsteroid(Asteroid.Size.SMALL, new Vector3f(0f, 0f, -15f), new Vector3f());
    w2.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w2, 0.3f, Controls.NONE);
    assertFalse(w2.asteroids().contains(small));
    assertTrue(w2.asteroids().isEmpty());
    assertEquals(World.SMALL_SCORE, w2.score());
  }

  @Test
  void theHalvesFlyApart() {
    final var w = empty();
    w.addAsteroid(Asteroid.Size.BIG, new Vector3f(0f, 0f, -20f), new Vector3f());
    w.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w, 0.3f, Controls.NONE);
    final var a = w.asteroids().get(0);
    final var b = w.asteroids().get(1);
    final float before = World.distance(a.position, b.position);
    run(w, 1f, Controls.NONE);
    assertTrue(World.distance(a.position, b.position) > before + 3f, "they separate");
  }

  @Test
  void bulletsRunOutAndTheFireRateIsLimited() {
    final var w = empty();
    w.step(DT, fireAt(new Vector3f(1f, 0f, 0f)));
    w.step(DT, fireAt(new Vector3f(1f, 0f, 0f)));
    assertEquals(1, w.bullets().size(), "the second shot waits for the cooldown");
    run(w, World.BULLET_TIME + 0.1f, Controls.NONE);
    assertTrue(w.bullets().isEmpty());
  }

  @Test
  void touchingAnAsteroidCostsALifeAndTheShipComesBackProtected() {
    final var w = empty();
    w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(0f, 0f, -3f), new Vector3f(0f, 0f, 10f));
    run(w, 0.5f, Controls.NONE);
    assertEquals(World.START_LIVES - 1, w.lives());
    assertFalse(w.ship().alive);
    final var events = w.drainEvents();
    assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.ShipDestroyed));

    run(w, World.RESPAWN_DELAY + 0.1f, Controls.NONE);
    assertTrue(w.ship().alive);
    assertTrue(w.ship().invulnerable > 0f);
    assertEquals(0f, w.ship().position.length(), 1e-4f, "back in the middle");

    // while protected, rocks pass through
    w.addAsteroid(Asteroid.Size.BIG, new Vector3f(), new Vector3f());
    run(w, 1f, Controls.NONE);
    assertEquals(World.START_LIVES - 1, w.lives());
  }

  @Test
  void losingTheLastLifeEndsTheGame() {
    final var w = empty();
    for (int i = 0; i < World.START_LIVES; i++) {
      w.ship().invulnerable = 0f;
      w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(w.ship().position), new Vector3f());
      w.step(DT, Controls.NONE);
      run(w, World.RESPAWN_DELAY + 0.1f, Controls.NONE);
    }
    assertTrue(w.isGameOver());
    assertEquals(0, w.lives());
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.GameOver));
    final int score = w.score();
    run(w, 1f, fireAt(new Vector3f(0f, 0f, -1f)));
    assertTrue(w.bullets().stream().noneMatch(b -> !b.fromBoss), "no shooting after the end");
    assertEquals(score, w.score());
  }

  @Test
  void theNextWaveStartsWhenTheRocksAreGone() {
    final var w = empty();
    run(w, World.WAVE_DELAY + 0.1f, Controls.NONE);
    assertEquals(2, w.wave());
    assertEquals(5, w.asteroids().size(), "one more big asteroid per wave");
    assertTrue(w.drainEvents().contains(new GameEvent.WaveStarted(2)));
  }

  @Test
  void theBossShootsAtThePlayerAndTakesThreeHits() {
    final var w = empty();
    final var boss = w.summonBoss();
    assertNotNull(boss);
    boss.position.set(0f, 0f, -30f);
    boss.velocity.zero();
    boss.turnTimer = 100f; // keep it still
    run(w, World.BOSS_FIRE_INTERVAL + 0.1f, Controls.NONE);
    final var shots = w.bullets().stream().filter(b -> b.fromBoss).toList();
    assertEquals(1, shots.size());
    final var toShip = World.delta(shots.get(0).position, w.ship().position, new Vector3f()).normalize();
    final var heading = new Vector3f(shots.get(0).velocity).normalize();
    assertTrue(toShip.dot(heading) > Math.cos(World.BOSS_SPREAD * 1.5), "aimed at the player, give or take");

    for (int hit = 1; hit <= World.BOSS_HEALTH; hit++) {
      w.ship().fireCooldown = 0f;
      w.step(DT, fireAt(new Vector3f(boss.position).normalize()));
      run(w, 0.6f, Controls.NONE);
      w.ship().invulnerable = 100f; // its shots must not end the test
    }
    assertNull(w.boss(), "gone after three hits");
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.BossDestroyed));
    assertEquals(2 * World.BOSS_HIT_SCORE + World.BOSS_SCORE, w.score());
  }

  @Test
  void aBossBulletCostsALife() {
    final var w = empty();
    final var boss = w.summonBoss();
    boss.position.set(0f, 0f, -20f);
    boss.velocity.zero();
    boss.turnTimer = 100f;
    run(w, World.BOSS_FIRE_INTERVAL + 1.2f, Controls.NONE);
    assertEquals(World.START_LIVES - 1, w.lives());
  }

  @Test
  void theBossLeavesAfterAWhile() {
    final var w = empty();
    w.summonBoss();
    w.ship().invulnerable = 1000f;
    run(w, World.BOSS_STAY + 0.1f, Controls.NONE);
    assertNull(w.boss());
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.BossLeft));
  }

  @Test
  void theArenaWrapsAround() {
    final var p = new Vector3f(World.ARENA + 1f, -World.ARENA - 2f, 3f);
    World.wrap(p);
    assertEquals(-World.ARENA + 1f, p.x, 1e-4f);
    assertEquals(World.ARENA - 2f, p.y, 1e-4f);
    assertEquals(3f, p.z, 1e-4f);
    final var a = new Vector3f(World.ARENA - 1f, 0f, 0f);
    final var b = new Vector3f(-World.ARENA + 1f, 0f, 0f);
    assertEquals(2f, World.distance(a, b), 1e-4f, "the short way, through the wall");

    // an asteroid touching across the wall still hits
    final var w = empty();
    w.ship().position.set(World.ARENA - 0.5f, 0f, 0f);
    w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(-World.ARENA + 1f, 0f, 0f), new Vector3f());
    w.step(DT, Controls.NONE);
    assertFalse(w.ship().alive);
  }

  @Test
  void thrustAcceleratesUpToTheTopSpeedAndTheShipCoastsToAStop() {
    final var w = empty();
    final var forward = new Controls(new Vector3f(0f, 0f, -1f), new Vector3f(0f, 0f, -1f), false);
    run(w, 3f, forward);
    assertEquals(World.SHIP_MAX_SPEED, w.ship().velocity.length(), 0.5f);
    run(w, 6f, Controls.NONE);
    assertTrue(w.ship().velocity.length() < 0.5f);
  }

  @Test
  void theSameSeedPlaysTheSameGame() {
    final var a = World.newGame(7L);
    final var b = World.newGame(7L);
    final var c = new Controls(new Vector3f(0.3f, 0f, -1f), new Vector3f(0f, 0f, -1f), true);
    run(a, 5f, c);
    run(b, 5f, c);
    assertEquals(a.score(), b.score());
    assertEquals(a.asteroids().size(), b.asteroids().size());
    assertEquals(a.asteroids().get(0).position, b.asteroids().get(0).position);
  }

  @Test
  void theAttractWorldHasNoShipAndNoBoss() {
    final var w = World.attract(3L, 6);
    assertNull(w.ship());
    run(w, 60f, Controls.NONE);
    assertEquals(6, w.asteroids().size());
    assertNull(w.boss());
  }
}
