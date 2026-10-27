package astroids.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TuningTest {
  @TempDir
  Path dir;

  private static Tuning.Loaded parse(String text) throws IOException {
    final var p = new Properties();
    p.load(new StringReader(text));
    return Tuning.from(p);
  }

  @Test
  void valuesOverrideTheDefaultsAndOnlyThose() throws IOException {
    final var loaded = parse("""
        ship.maxSpeed = 40
        enemy.fireInterval = 2.5
        ship.lives = 5
        """);
    assertTrue(loaded.custom());
    assertEquals(3, loaded.overrides());
    assertTrue(loaded.problems().isEmpty());
    final var t = loaded.tuning();
    assertEquals(40f, t.shipMaxSpeed);
    assertEquals(2.5f, t.enemyFireInterval);
    assertEquals(5, t.lives);
    assertEquals(new Tuning().bulletSpeed, t.bulletSpeed, "untouched values keep their defaults");
  }

  @Test
  void unknownKeysAndBadValuesAreReportedAndIgnored() throws IOException {
    final var loaded = parse("""
        ship.maxSpeeed = 40
        ship.acceleration = fast
        ship.lives = 0
        ship.lives2 = 3
        enemy.health = 2.5
        gun.bulletTime = -1
        """);
    assertFalse(loaded.custom(), "nothing usable");
    assertEquals(6, loaded.problems().size(), loaded.problems().toString());
    assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("unknown key ship.maxSpeeed")));
    assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("not a number")));
    assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("whole number")));
    final var d = new Tuning();
    assertEquals(d.lives, loaded.tuning().lives);
    assertEquals(d.shipAcceleration, loaded.tuning().shipAcceleration);
  }

  @Test
  void valuesThatOnlyMakeSenseTogetherAreChecked() throws IOException {
    final var loaded = parse("""
        rocks.minSpeed = 9
        rocks.maxSpeed = 2
        enemy.minDelay = 50
        enemy.maxDelay = 10
        arena.halfSize = 20
        rocks.safeDistance = 100
        """);
    final var t = loaded.tuning();
    final var d = new Tuning();
    assertEquals(d.rockMinSpeed, t.rockMinSpeed, "a min above its max falls back to the defaults");
    assertEquals(d.rockMaxSpeed, t.rockMaxSpeed);
    assertEquals(d.enemyMinDelay, t.enemyMinDelay);
    assertEquals(30f, t.safeDistance, 1e-4f, "there must be room to place rocks that far away");
    assertEquals(3, loaded.problems().size(), loaded.problems().toString());
    World.newGame(1L, t); // places its rocks without looping forever
  }

  @Test
  void theExampleFileListsEveryKeyWithItsDefault() throws IOException {
    final var file = this.dir.resolve("tuning.example.properties");
    Tuning.writeExample(file);
    final var text = Files.readString(file);
    for (String key : Tuning.keys()) {
      assertTrue(text.contains("#" + key + " = "), key);
    }
    // every line uncommented: exactly the defaults, nothing rejected
    final var uncommented = text.lines().filter(l -> l.startsWith("#") && l.contains(" = ") && !l.startsWith("# "))
        .map(l -> l.substring(1)).reduce("", (a, b) -> a + b + "\n");
    final var loaded = parse(uncommented);
    assertTrue(loaded.problems().isEmpty(), loaded.problems().toString());
    assertEquals(Tuning.keys().size(), loaded.overrides());
    final var d = new Tuning();
    assertEquals(d.enemyAimErrorDegrees, loaded.tuning().enemyAimErrorDegrees);
    assertEquals(d.shipDamping, loaded.tuning().shipDamping);
  }

  @Test
  void aMissingFileGivesTheDefaults() throws IOException {
    final var loaded = Tuning.load(this.dir.resolve("nothing.properties"));
    assertFalse(loaded.custom());
    assertTrue(loaded.problems().isEmpty());
  }

  @Test
  void theWorldPlaysByItsTuning() throws IOException {
    final var t = parse("""
        ship.lives = 1
        rocks.firstWave = 7
        arena.halfSize = 30
        score.smallRock = 7
        """).tuning();
    final var w = World.newGame(5L, t);
    assertEquals(7, w.asteroids().size());
    assertEquals(30f, w.arena());
    for (var a : w.asteroids()) {
      assertTrue(Math.abs(a.position.x) <= 30f && Math.abs(a.position.y) <= 30f && Math.abs(a.position.z) <= 30f);
    }

    // a small rock shot: its score comes from the tuning
    w.clearAsteroids();
    w.ship().invulnerable = 0f;
    w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(0f, 0f, -10f), new Vector3f());
    w.step(1f / 60f, new Controls(new Vector3f(), new Vector3f(0f, 0f, -1f), true));
    for (int i = 0; i < 30; i++) {
      w.step(1f / 60f, Controls.NONE);
    }
    assertEquals(7, w.score());

    // one life: the first hit ends the game
    w.addAsteroid(Asteroid.Size.SMALL, new Vector3f(w.ship().position), new Vector3f());
    w.step(1f / 60f, Controls.NONE);
    assertTrue(w.isGameOver());

    // the arena wraps at its tuned size
    final var p = new Vector3f(31f, 0f, 0f);
    w.wrap(p);
    assertEquals(-29f, p.x, 1e-4f);
  }
}
