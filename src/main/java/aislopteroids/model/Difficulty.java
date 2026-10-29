package aislopteroids.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How hard the game is, picked on the title screen. Each level is the released {@link Tuning} with some values
 * changed; Normal is the defaults. Best scores are kept for each level apart.
 */
public enum Difficulty {
  /** Fewer, slower rocks and a slower, clumsier enemy: about halfway between v0.2 and v0.3. */
  EASY("Easy", set(
      "rocks.firstWave", 6,
      "rocks.minSpeed", 8,
      "rocks.maxSpeed", 11,
      "rocks.speedPerWave", 0.3,
      "ship.extraLifeEvery", 7500,
      "gun.battery", 42,
      "enemy.speed", 13,
      "enemy.fireInterval", 1,
      "enemy.aimErrorDegrees", 3,
      "enemy.minDelay", 30,
      "enemy.maxDelay", 45,
      "scout.fromWave", 7)),
  /** The game as released. */
  NORMAL("Normal", Map.of()),
  /** More and faster rocks, a sharper and tougher enemy that comes more often, and a shorter shield. */
  HARD("Hard", set(
      "rocks.firstWave", 12,
      "rocks.minSpeed", 16,
      "rocks.maxSpeed", 20,
      "rocks.speedPerWave", 0.5,
      "ship.shieldTime", 2,
      "ship.extraLifeEvery", 15000,
      "gun.battery", 32,
      "enemy.health", 4,
      "enemy.speed", 24,
      "enemy.fireInterval", 0.35,
      "enemy.aimErrorDegrees", 1,
      "enemy.minDelay", 14,
      "enemy.maxDelay", 24,
      "scout.fromWave", 3));

  /** For the screen. */
  public final String label;
  /** Tuning keys and the values this level sets for them. */
  public final Map<String, Number> changes;

  Difficulty(String label, Map<String, Number> changes) {
    this.label = label;
    this.changes = changes;
  }

  /** The next level up or down, staying at the ends. */
  public Difficulty step(int direction) {
    final var all = values();
    return all[Math.max(0, Math.min(all.length - 1, ordinal() + Integer.signum(direction)))];
  }

  /** The level with this name (as {@link #name()}), or Normal. */
  public static Difficulty parse(String name) {
    for (Difficulty d : values()) {
      if (d.name().equalsIgnoreCase(name)) {
        return d;
      }
    }
    return NORMAL;
  }

  private static Map<String, Number> set(Object... keysAndValues) {
    final var m = new LinkedHashMap<String, Number>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      m.put((String) keysAndValues[i], (Number) keysAndValues[i + 1]);
    }
    return Collections.unmodifiableMap(m);
  }
}
