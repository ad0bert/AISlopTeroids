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
  /** The numbers as released, which World.newGame(seed) plays with. */
  private static final Tuning T = new Tuning();

  private static float dist(Vector3f a, Vector3f b) {
    return World.delta(a, b, new Vector3f(), T.arena).length();
  }

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
    assertEquals(T.lives, w.lives());
    assertEquals(T.firstWaveRocks, w.asteroids().size());
    assertEquals(T.shieldTime, w.ship().invulnerable, "the first ship starts shielded, like every later one");
    for (Asteroid a : w.asteroids()) {
      assertEquals(Asteroid.Size.BIG, a.size);
      assertTrue(dist(a.position, w.ship().position) >= T.safeDistance);
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
    assertTrue(w.score() >= T.scoreBigRock);
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.AsteroidDestroyed d
        && d.size() == Asteroid.Size.BIG && d.velocity().length() == 0f), "it stood still: so do its pieces");

    // shoot each small one: it goes away without splitting
    final var w2 = empty();
    final var small = w2.addAsteroid(Asteroid.Size.SMALL, new Vector3f(0f, 0f, -15f), new Vector3f());
    w2.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w2, 0.3f, Controls.NONE);
    assertFalse(w2.asteroids().contains(small));
    assertTrue(w2.asteroids().isEmpty());
    assertEquals(T.scoreSmallRock, w2.score());
  }

  @Test
  void theHalvesFlyApart() {
    final var w = empty();
    w.addAsteroid(Asteroid.Size.BIG, new Vector3f(0f, 0f, -20f), new Vector3f());
    w.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w, 0.3f, Controls.NONE);
    final var a = w.asteroids().get(0);
    final var b = w.asteroids().get(1);
    final float before = dist(a.position, b.position);
    run(w, 1f, Controls.NONE);
    assertTrue(dist(a.position, b.position) > before + 3f, "they separate");
  }

  @Test
  void bulletsRunOutAndTheFireRateIsLimited() {
    final var w = empty();
    w.step(DT, fireAt(new Vector3f(1f, 0f, 0f)));
    w.step(DT, fireAt(new Vector3f(1f, 0f, 0f)));
    assertEquals(1, w.bullets().size(), "the second shot waits for the cooldown");
    run(w, T.bulletTime + 0.1f, Controls.NONE);
    assertTrue(w.bullets().isEmpty());
  }

  @Test
  void touchingAnAsteroidCostsALifeAndTheShipComesBackProtected() {
    final var w = empty();
    w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(0f, 0f, -3f), new Vector3f(0f, 0f, 10f));
    run(w, 0.5f, Controls.NONE);
    assertEquals(T.lives - 1, w.lives());
    assertFalse(w.ship().alive);
    final var events = w.drainEvents();
    assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.ShipDestroyed));

    run(w, T.respawnDelay + 0.1f, Controls.NONE);
    assertTrue(w.ship().alive);
    assertTrue(w.ship().invulnerable > 0f);
    assertEquals(0f, w.ship().position.length(), 1e-4f, "back in the middle");

    // while protected, rocks pass through
    w.addAsteroid(Asteroid.Size.BIG, new Vector3f(), new Vector3f());
    run(w, 1f, Controls.NONE);
    assertEquals(T.lives - 1, w.lives());
  }

  @Test
  void losingTheLastLifeEndsTheGame() {
    final var w = empty();
    for (int i = 0; i < T.lives; i++) {
      w.ship().invulnerable = 0f;
      w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(w.ship().position), new Vector3f());
      w.step(DT, Controls.NONE);
      run(w, T.respawnDelay + 0.1f, Controls.NONE);
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
    run(w, T.waveDelay + 0.1f, Controls.NONE);
    assertEquals(2, w.wave());
    assertEquals(T.firstWaveRocks + 1, w.asteroids().size(), "one more big asteroid per wave");
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
    run(w, T.enemyFireInterval + 0.1f, Controls.NONE);
    final var shots = w.bullets().stream().filter(b -> b.fromBoss).toList();
    assertEquals(1, shots.size());
    final var toShip = World.delta(shots.get(0).position, w.ship().position, new Vector3f(), T.arena).normalize();
    final var heading = new Vector3f(shots.get(0).velocity).normalize();
    assertTrue(toShip.dot(heading) > Math.cos((float) Math.toRadians(T.enemyAimErrorDegrees) * 1.5), "aimed at the player, give or take");

    for (int hit = 1; hit <= T.enemyHealth; hit++) {
      w.ship().fireCooldown = 0f;
      w.step(DT, fireAt(new Vector3f(boss.position).normalize()));
      run(w, 0.6f, Controls.NONE);
      w.ship().invulnerable = 100f; // its shots must not end the test
    }
    assertNull(w.boss(), "gone after three hits");
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.BossDestroyed));
    assertEquals(2 * T.scoreEnemyHit + T.scoreEnemyDestroyed, w.score());
  }

  @Test
  void aBossBulletCostsALife() {
    final var w = empty();
    final var boss = w.summonBoss();
    boss.position.set(0f, 0f, -20f);
    boss.velocity.zero();
    boss.turnTimer = 100f;
    run(w, T.enemyFireInterval + 1.2f, Controls.NONE);
    assertEquals(T.lives - 1, w.lives());
  }

  @Test
  void theBossLeavesAfterAWhile() {
    final var w = empty();
    w.summonBoss();
    w.ship().invulnerable = 1000f;
    run(w, T.enemyStay + 0.1f, Controls.NONE);
    assertNull(w.boss());
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.BossLeft));
  }

  @Test
  void theArenaWrapsAround() {
    final var p = new Vector3f(T.arena + 1f, -T.arena - 2f, 3f);
    World.wrap(p, T.arena);
    assertEquals(-T.arena + 1f, p.x, 1e-4f);
    assertEquals(T.arena - 2f, p.y, 1e-4f);
    assertEquals(3f, p.z, 1e-4f);
    final var a = new Vector3f(T.arena - 1f, 0f, 0f);
    final var b = new Vector3f(-T.arena + 1f, 0f, 0f);
    assertEquals(2f, dist(a, b), 1e-4f, "the short way, through the wall");

    // an asteroid touching across the wall still hits
    final var w = empty();
    w.ship().position.set(T.arena - 0.5f, 0f, 0f);
    w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(-T.arena + 1f, 0f, 0f), new Vector3f());
    w.step(DT, Controls.NONE);
    assertFalse(w.ship().alive);
  }

  @Test
  void thrustAcceleratesUpToTheTopSpeedTheShipCoastsLongAndBrakesToAStop() {
    final var w = empty();
    final var forward = new Controls(new Vector3f(0f, 0f, -1f), new Vector3f(0f, 0f, -1f), false);
    run(w, 3f, forward);
    assertEquals(T.shipMaxSpeed, w.ship().velocity.length(), 0.5f);
    run(w, 3f, Controls.NONE);
    assertTrue(w.ship().velocity.length() > T.shipMaxSpeed * 0.6f, "still going: " + w.ship().velocity.length());
    final var brake = new Controls(new Vector3f(), new Vector3f(0f, 0f, -1f), false, false, true);
    run(w, 1.5f, brake);
    assertEquals(0f, w.ship().velocity.length(), 1e-4f, "stopped, not going backwards");
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
  void everySoManyPointsBringAnExtraShip() {
    final var t = new Tuning();
    t.extraLifeEvery = 8 * t.scoreSmallRock;
    final var w = World.newGame(2L, t);
    w.clearAsteroids();
    w.ship().invulnerable = 0f;
    int extra = 0;
    for (int i = 0; i < 9; i++) {
      w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(0f, 0f, -10f), new Vector3f());
      w.ship().fireCooldown = 0f;
      w.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
      run(w, 0.2f, Controls.NONE);
      assertTrue(w.asteroids().isEmpty());
      final long now = w.drainEvents().stream().filter(e -> e instanceof GameEvent.ExtraLife).count();
      assertEquals(i == 7 ? 1 : 0, now, "rock " + (i + 1));
      extra += (int) now;
    }
    assertEquals(1, extra);
    assertEquals(t.lives + 1, w.lives());
    assertEquals(10000, new Tuning().extraLifeEvery, "as in the original");
  }

  @Test
  void bigScoresAtOnceCanBringSeveralShipsAndNoneWhenTurnedOff() {
    final var t = new Tuning();
    t.extraLifeEvery = 500;
    t.scoreEnemyDestroyed = 1200;
    final var w = World.newGame(3L, t);
    w.clearAsteroids();
    final var boss = w.summonBoss();
    boss.health = 1;
    boss.position.set(0f, 0f, -15f);
    boss.velocity.zero();
    boss.turnTimer = 100f;
    w.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w, 0.4f, Controls.NONE);
    assertEquals(1200, w.score());
    assertEquals(t.lives + 2, w.lives(), "500 and 1000 passed in one go");
    assertTrue(w.drainEvents().contains(new GameEvent.ExtraLife(t.lives + 2)));

    t.extraLifeEvery = 0;
    final var none = World.newGame(3L, t);
    none.clearAsteroids();
    none.summonBoss().health = 1;
    none.boss().position.set(0f, 0f, -15f);
    none.boss().velocity.zero();
    none.boss().turnTimer = 100f;
    none.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(none, 0.4f, Controls.NONE);
    assertEquals(1200, none.score());
    assertEquals(t.lives, none.lives());
  }

  @Test
  void hyperspaceJumpsSomewhereElseAndThenNeedsToCoolDown() {
    final var w = empty();
    final var jump = new Controls(new Vector3f(), new Vector3f(0f, 0f, -1f), false, true);
    w.ship().velocity.set(5f, 0f, 0f);
    w.step(DT, jump);
    final var events = w.drainEvents();
    final var hyper = events.stream().filter(e -> e instanceof GameEvent.Hyperspace).map(e -> (GameEvent.Hyperspace) e)
        .findFirst().orElseThrow();
    assertTrue(dist(hyper.from(), hyper.to()) > 1f, "somewhere else");
    assertEquals(0f, w.ship().velocity.length(), 1e-4f, "it comes out standing still");
    final var landed = new Vector3f(w.ship().position);
    for (int i = 0; i < 3; i++) {
      assertTrue(Math.abs(landed.get(i)) <= T.arena);
    }

    run(w, T.hyperspaceCooldown - 0.5f, jump);
    assertTrue(w.drainEvents().stream().noneMatch(e -> e instanceof GameEvent.Hyperspace), "still cooling down");
    run(w, 0.6f, jump);
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.Hyperspace), "ready again");
  }

  @Test
  void noJumpWhileTheShipIsGone() {
    final var w = empty();
    w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(w.ship().position), new Vector3f());
    w.step(DT, Controls.NONE);
    assertFalse(w.ship().alive);
    w.step(DT, new Controls(new Vector3f(), new Vector3f(0f, 0f, -1f), false, true));
    assertTrue(w.drainEvents().stream().noneMatch(e -> e instanceof GameEvent.Hyperspace));
  }

  private static World withScout(Tuning t) {
    final var w = World.newGame(9L, t);
    w.clearAsteroids();
    w.ship().invulnerable = 1000f;
    w.drainEvents();
    final var scout = w.summon(Boss.Kind.SCOUT);
    scout.position.set(0f, 0f, -30f);
    scout.velocity.zero();
    scout.turnTimer = 100f;
    return w;
  }

  @Test
  void theScoutTakesOneHitAndScoresMore() {
    final var t = new Tuning();
    t.scoutDodgeChance = 0f;
    final var w = withScout(t);
    assertEquals(World.SCOUT_RADIUS, w.boss().radius());
    w.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w, 0.6f, Controls.NONE);
    assertNull(w.boss());
    assertEquals(t.scoreScoutDestroyed, w.score());
    assertTrue(w.drainEvents().stream().anyMatch(e -> e instanceof GameEvent.BossDestroyed d
        && d.kind() == Boss.Kind.SCOUT && d.points() == t.scoreScoutDestroyed && d.velocity() != null));
  }

  @Test
  void aScoutThatSeesTheShotComingSwervesOutOfItsWay() {
    final var t = new Tuning();
    t.scoutDodgeChance = 1f;
    final var w = withScout(t);
    w.step(DT, fireAt(new Vector3f(0f, 0f, -1f)));
    run(w, 0.6f, Controls.NONE);
    assertNotNull(w.boss(), "it got out of the way");
    assertTrue(w.boss().velocity.length() > 1f);
    assertEquals(0f, w.boss().velocity.z, 1e-3f, "across the shot's path, not along it");
    assertEquals(0, w.score());
  }

  @Test
  void scoutsComeFromTheirWaveOnAndEnemiesComeSoonerLater() {
    final var t = new Tuning();
    t.scoutChance = 1f;
    t.scoutFromWave = 2;
    t.enemyMinDelay = 5f;
    t.enemyMaxDelay = 5f;
    t.firstWaveRocks = 1;
    final var w = World.newGame(4L, t);
    w.ship().invulnerable = 1000f;
    assertEquals(5f, w.enemyDelay(), 1e-4f);
    run(w, 5.1f, Controls.NONE);
    assertNotNull(w.boss());
    assertEquals(Boss.Kind.BIG, w.boss().kind, "wave 1: still the big one");

    // on to wave 2: the wait after this one is shorter, and the next one is a scout
    w.clearAsteroids();
    run(w, t.waveDelay + 0.1f, Controls.NONE);
    assertEquals(2, w.wave());
    w.boss().timeLeft = 0.01f;
    run(w, 0.05f, Controls.NONE);
    assertNull(w.boss());
    assertEquals(5f * (1f - t.enemyWaitCutPerWave), w.enemyDelay(), 0.1f, "a shorter wait in wave 2");
    run(w, w.enemyDelay() + 0.1f, Controls.NONE);
    assertNotNull(w.boss());
    assertEquals(Boss.Kind.SCOUT, w.boss().kind);
  }

  @Test
  void theWaitForEnemiesStopsShrinkingAtFortyPercent() {
    final var t = new Tuning();
    t.enemyMinDelay = 10f;
    t.enemyMaxDelay = 10f;
    t.enemyWaitCutPerWave = 0.5f;
    t.firstWaveRocks = 1;
    final var w = World.newGame(4L, t);
    w.ship().invulnerable = 100000f;
    for (int wave = 1; wave < 5; wave++) {
      w.clearAsteroids();
      run(w, t.waveDelay + 0.1f, Controls.NONE);
    }
    assertEquals(5, w.wave());
    w.summonBoss().timeLeft = 0.01f;
    run(w, 0.05f, Controls.NONE);
    assertEquals(4f, w.enemyDelay(), 0.05f);
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
