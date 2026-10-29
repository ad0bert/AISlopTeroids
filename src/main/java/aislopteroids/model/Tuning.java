package aislopteroids.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The numbers that decide how the game plays: speeds, timings, sizes of waves, the enemy ships, scores. The defaults are
 * the game as released (Normal); each {@link Difficulty} changes some of them, and a {@code tuning.properties} file can
 * override any of them on top of that for playtesting, e.g.
 *
 * <pre>
 * ship.maxSpeed = 35
 * enemy.fireInterval = 2.5
 * </pre>
 *
 * {@link #load} reads such a file and reports keys it does not know and values it cannot use (those keep their
 * defaults); {@link #writeExample} writes every key with its default and a description.
 */
public final class Tuning {
  // the arena
  public float arena = 60f;
  // the player's ship
  public float shipAcceleration = 30f;
  public float shipMaxSpeed = 25f;
  public float shipDamping = 0.12f;
  public float shipBrake = 25f;
  public int lives = 3;
  public float respawnDelay = 2f;
  public float shieldTime = 3f;
  public int extraLifeEvery = 10000;
  public float hyperspaceCooldown = 8f;
  // the player's gun
  public float bulletSpeed = 70f;
  public float bulletTime = 1.1f;
  public float fireInterval = 0.16f;
  public int batteryShots = 35;
  public float batteryRecharge = 7.5f;
  public float rechargeDelay = 0.35f;
  // asteroids and waves
  public int firstWaveRocks = 10;
  public int maxRocks = 120;
  public float rockMinSpeed = 13f;
  public float rockMaxSpeed = 16f;
  public float rockSpeedPerWave = 0.4f;
  public float splitMinSpeed = 4f;
  public float splitMaxSpeed = 7f;
  public float rockBounce = 0.35f;
  public float rockFriction = 0.3f;
  public float safeDistance = 25f;
  public float waveDelay = 2.5f;
  // the enemy ship
  public int enemyHealth = 3;
  public float enemySpeed = 20f;
  public float enemyStay = 25f;
  public float enemyFireInterval = 0.5f;
  public float enemyBulletSpeed = 32f;
  public float enemyBulletTime = 3f;
  public float enemyAimErrorDegrees = 1.44f;
  public float enemyMinDelay = 20f;
  public float enemyMaxDelay = 35f;
  public float enemyWaitCutPerWave = 0.06f;
  // the scout: a small, quick enemy ship from a later wave on
  public int scoutFromWave = 5;
  public float scoutChance = 0.5f;
  public int scoutHealth = 1;
  public float scoutSpeed = 32f;
  public float scoutStay = 15f;
  public float scoutFireInterval = 0.9f;
  public float scoutAimErrorDegrees = 4f;
  public float scoutDodgeChance = 0.5f;
  // scores
  public int scoreBigRock = 20;
  public int scoreSmallRock = 50;
  public int scoreEnemyHit = 100;
  public int scoreEnemyDestroyed = 1000;
  public int scoreScoutDestroyed = 1500;

  /** One tunable value: its key in the file, a description, the range that makes sense, and how to set it. */
  private record Param(String key, String description, float min, float max, boolean integer,
      Function<Tuning, Number> get, BiConsumer<Tuning, Number> set) {
  }

  private static final Map<String, Param> PARAMS = new LinkedHashMap<>();

  private static void f(String key, String description, float min, Function<Tuning, Number> get,
      BiConsumer<Tuning, Number> set) {
    PARAMS.put(key, new Param(key, description, min, Float.MAX_VALUE, false, get, set));
  }

  /** A fraction or chance: at most {@code max}. */
  private static void f(String key, String description, float min, float max, Function<Tuning, Number> get,
      BiConsumer<Tuning, Number> set) {
    PARAMS.put(key, new Param(key, description, min, max, false, get, set));
  }

  private static void i(String key, String description, int min, Function<Tuning, Number> get,
      BiConsumer<Tuning, Number> set) {
    PARAMS.put(key, new Param(key, description, min, Float.MAX_VALUE, true, get, set));
  }

  static {
    f("arena.halfSize", "half the edge of the wrap-around cube, metres", 20f, t -> t.arena,
        (t, v) -> t.arena = v.floatValue());
    f("ship.acceleration", "thrust, metres per second per second", 1f, t -> t.shipAcceleration,
        (t, v) -> t.shipAcceleration = v.floatValue());
    f("ship.maxSpeed", "top speed, metres per second", 1f, t -> t.shipMaxSpeed,
        (t, v) -> t.shipMaxSpeed = v.floatValue());
    f("ship.damping", "how fast the ship coasts to a stop without thrust (0 = never)", 0f, t -> t.shipDamping,
        (t, v) -> t.shipDamping = v.floatValue());
    f("ship.brake", "how fast braking slows the ship, metres per second per second", 0f, t -> t.shipBrake,
        (t, v) -> t.shipBrake = v.floatValue());
    i("ship.lives", "ships per game", 1, t -> t.lives, (t, v) -> t.lives = v.intValue());
    f("ship.respawnDelay", "seconds until the next ship appears", 0.1f, t -> t.respawnDelay,
        (t, v) -> t.respawnDelay = v.floatValue());
    f("ship.shieldTime", "seconds a new ship cannot be hit", 0f, t -> t.shieldTime,
        (t, v) -> t.shieldTime = v.floatValue());
    i("ship.extraLifeEvery", "points for each extra ship (0 = none)", 0, t -> t.extraLifeEvery,
        (t, v) -> t.extraLifeEvery = v.intValue());
    f("hyperspace.cooldown", "seconds between hyperspace jumps", 0.5f, t -> t.hyperspaceCooldown,
        (t, v) -> t.hyperspaceCooldown = v.floatValue());
    f("gun.bulletSpeed", "metres per second (on top of the ship's speed)", 1f, t -> t.bulletSpeed,
        (t, v) -> t.bulletSpeed = v.floatValue());
    f("gun.bulletTime", "seconds a shot flies", 0.1f, t -> t.bulletTime, (t, v) -> t.bulletTime = v.floatValue());
    f("gun.fireInterval", "seconds between shots when holding fire", 0.02f, t -> t.fireInterval,
        (t, v) -> t.fireInterval = v.floatValue());
    i("gun.battery", "shots a full battery holds", 1, t -> t.batteryShots, (t, v) -> t.batteryShots = v.intValue());
    f("gun.recharge", "shots' worth the battery recharges per second while fire is not held", 0.1f,
        t -> t.batteryRecharge, (t, v) -> t.batteryRecharge = v.floatValue());
    f("gun.rechargeDelay", "seconds after letting go of fire before the battery recharges", 0f, t -> t.rechargeDelay,
        (t, v) -> t.rechargeDelay = v.floatValue());
    i("rocks.firstWave", "big rocks in wave 1; each wave has one more", 1, t -> t.firstWaveRocks,
        (t, v) -> t.firstWaveRocks = v.intValue());
    i("rocks.max", "most big rocks in a wave", 1, t -> t.maxRocks, (t, v) -> t.maxRocks = v.intValue());
    f("rocks.minSpeed", "slowest big rock in wave 1, metres per second", 0f, t -> t.rockMinSpeed,
        (t, v) -> t.rockMinSpeed = v.floatValue());
    f("rocks.maxSpeed", "fastest big rock in wave 1, metres per second", 0f, t -> t.rockMaxSpeed,
        (t, v) -> t.rockMaxSpeed = v.floatValue());
    f("rocks.speedPerWave", "added to rock speeds with every wave", 0f, t -> t.rockSpeedPerWave,
        (t, v) -> t.rockSpeedPerWave = v.floatValue());
    f("rocks.splitMinSpeed", "slowest speed the halves of a split rock fly apart at", 0f, t -> t.splitMinSpeed,
        (t, v) -> t.splitMinSpeed = v.floatValue());
    f("rocks.splitMaxSpeed", "fastest speed the halves of a split rock fly apart at", 0f, t -> t.splitMaxSpeed,
        (t, v) -> t.splitMaxSpeed = v.floatValue());
    f("rocks.bounce", "how much of their closing speed two rocks keep when they knock together (0 = none, 1 = all)",
        0f, 1f, t -> t.rockBounce, (t, v) -> t.rockBounce = v.floatValue());
    f("rocks.friction", "how much rocks that knock together grip each other, slowing their sliding past (0 = none)",
        0f, t -> t.rockFriction, (t, v) -> t.rockFriction = v.floatValue());
    f("rocks.safeDistance", "new rocks and the enemy ship appear at least this far from the ship", 0f,
        t -> t.safeDistance, (t, v) -> t.safeDistance = v.floatValue());
    f("waves.delay", "seconds between clearing a wave and the next", 0f, t -> t.waveDelay,
        (t, v) -> t.waveDelay = v.floatValue());
    i("enemy.health", "hits the enemy ship takes", 1, t -> t.enemyHealth, (t, v) -> t.enemyHealth = v.intValue());
    f("enemy.speed", "metres per second", 0f, t -> t.enemySpeed, (t, v) -> t.enemySpeed = v.floatValue());
    f("enemy.stay", "seconds before it leaves", 1f, t -> t.enemyStay, (t, v) -> t.enemyStay = v.floatValue());
    f("enemy.fireInterval", "seconds between its shots", 0.1f, t -> t.enemyFireInterval,
        (t, v) -> t.enemyFireInterval = v.floatValue());
    f("enemy.bulletSpeed", "metres per second", 1f, t -> t.enemyBulletSpeed,
        (t, v) -> t.enemyBulletSpeed = v.floatValue());
    f("enemy.bulletTime", "seconds its shots fly", 0.1f, t -> t.enemyBulletTime,
        (t, v) -> t.enemyBulletTime = v.floatValue());
    f("enemy.aimErrorDegrees", "how far off its aim may be", 0f, t -> t.enemyAimErrorDegrees,
        (t, v) -> t.enemyAimErrorDegrees = v.floatValue());
    f("enemy.minDelay", "shortest wait before an enemy ship comes, seconds", 1f, t -> t.enemyMinDelay,
        (t, v) -> t.enemyMinDelay = v.floatValue());
    f("enemy.maxDelay", "longest wait before an enemy ship comes, seconds", 1f, t -> t.enemyMaxDelay,
        (t, v) -> t.enemyMaxDelay = v.floatValue());
    f("enemy.waitCutPerWave", "the wait gets this much shorter with every wave (0.06 = 6%), down to 40% of it", 0f,
        0.6f, t -> t.enemyWaitCutPerWave, (t, v) -> t.enemyWaitCutPerWave = v.floatValue());
    i("scout.fromWave", "first wave a scout can come in", 1, t -> t.scoutFromWave,
        (t, v) -> t.scoutFromWave = v.intValue());
    f("scout.chance", "from then on, the chance that an enemy ship is a scout (0 to 1)", 0f, 1f, t -> t.scoutChance,
        (t, v) -> t.scoutChance = v.floatValue());
    i("scout.health", "hits a scout takes", 1, t -> t.scoutHealth, (t, v) -> t.scoutHealth = v.intValue());
    f("scout.speed", "metres per second", 0f, t -> t.scoutSpeed, (t, v) -> t.scoutSpeed = v.floatValue());
    f("scout.stay", "seconds before it leaves", 1f, t -> t.scoutStay, (t, v) -> t.scoutStay = v.floatValue());
    f("scout.fireInterval", "seconds between its shots", 0.1f, t -> t.scoutFireInterval,
        (t, v) -> t.scoutFireInterval = v.floatValue());
    f("scout.aimErrorDegrees", "how far off its aim may be", 0f, t -> t.scoutAimErrorDegrees,
        (t, v) -> t.scoutAimErrorDegrees = v.floatValue());
    f("scout.dodgeChance", "the chance that it sees a shot coming and swerves (0 to 1)", 0f, 1f,
        t -> t.scoutDodgeChance, (t, v) -> t.scoutDodgeChance = v.floatValue());
    i("score.bigRock", "points for a big rock", 0, t -> t.scoreBigRock, (t, v) -> t.scoreBigRock = v.intValue());
    i("score.smallRock", "points for a small rock", 0, t -> t.scoreSmallRock,
        (t, v) -> t.scoreSmallRock = v.intValue());
    i("score.enemyHit", "points for hitting the enemy ship", 0, t -> t.scoreEnemyHit,
        (t, v) -> t.scoreEnemyHit = v.intValue());
    i("score.enemyDestroyed", "points for destroying it", 0, t -> t.scoreEnemyDestroyed,
        (t, v) -> t.scoreEnemyDestroyed = v.intValue());
    i("score.scoutDestroyed", "points for destroying a scout", 0, t -> t.scoreScoutDestroyed,
        (t, v) -> t.scoreScoutDestroyed = v.intValue());
  }

  /** The game as released (Normal). */
  public Tuning() {
  }

  /** The values of a difficulty level: the defaults with its changes. */
  public static Tuning of(Difficulty difficulty) {
    final var t = new Tuning();
    difficulty.changes.forEach((key, value) -> {
      final var param = PARAMS.get(key);
      if (param == null) {
        throw new IllegalStateException(difficulty + " changes unknown key " + key);
      }
      param.set().accept(t, param.integer() ? (Number) value.intValue() : (Number) value.floatValue());
    });
    return t;
  }

  /** A tuning read from properties: how many values differ from the defaults, and what could not be used. */
  public record Loaded(Tuning tuning, int overrides, List<String> problems) {
    /** Whether anything differs from the defaults. */
    public boolean custom() {
      return this.overrides > 0;
    }
  }

  /** The defaults with the values in {@code p} on top; unknown keys and unusable values are reported, not applied. */
  public static Loaded from(Properties p) {
    return from(p, Difficulty.NORMAL);
  }

  /**
   * A difficulty's values with those in {@code p} on top. A value at its released (Normal) default changes nothing on
   * any difficulty, so a file that writes the defaults down leaves Easy and Hard as they are. Only values that end up
   * different from the difficulty's count as changed; unknown keys and unusable values are reported, not applied.
   */
  public static Loaded from(Properties p, Difficulty difficulty) {
    final var t = of(difficulty);
    final var defaults = of(difficulty);
    final var released = new Tuning();
    final List<String> problems = new ArrayList<>();
    int overrides = 0;
    for (String key : p.stringPropertyNames()) {
      final var param = PARAMS.get(key);
      final var text = p.getProperty(key).trim();
      if (param == null) {
        problems.add("unknown key " + key);
        continue;
      }
      try {
        final double value = Double.parseDouble(text);
        if (Double.isNaN(value) || value < param.min()) {
          problems.add(key + " = " + text + ": must be at least " + format(param.min()));
        } else if (value > param.max()) {
          problems.add(key + " = " + text + ": must be at most " + format(param.max()));
        } else if (param.integer() && value != Math.rint(value)) {
          problems.add(key + " = " + text + ": must be a whole number");
        } else {
          final Number number = param.integer() ? (Number) (int) value : (Number) (float) value;
          if (number.doubleValue() == param.get().apply(released).doubleValue()) {
            continue; // written down at its default: changes nothing, and Easy and Hard keep their own value
          }
          param.set().accept(t, number);
          if (number.doubleValue() != param.get().apply(defaults).doubleValue()) {
            overrides++;
          }
        }
      } catch (NumberFormatException e) {
        problems.add(key + " = " + text + ": not a number");
      }
    }
    // pairs and limits that only make sense together
    final var d = defaults;
    if (t.rockMinSpeed > t.rockMaxSpeed) {
      problems.add("rocks.minSpeed is above rocks.maxSpeed; using the defaults for both");
      t.rockMinSpeed = d.rockMinSpeed;
      t.rockMaxSpeed = d.rockMaxSpeed;
    }
    if (t.splitMinSpeed > t.splitMaxSpeed) {
      problems.add("rocks.splitMinSpeed is above rocks.splitMaxSpeed; using the defaults for both");
      t.splitMinSpeed = d.splitMinSpeed;
      t.splitMaxSpeed = d.splitMaxSpeed;
    }
    if (t.enemyMinDelay > t.enemyMaxDelay) {
      problems.add("enemy.minDelay is above enemy.maxDelay; using the defaults for both");
      t.enemyMinDelay = d.enemyMinDelay;
      t.enemyMaxDelay = d.enemyMaxDelay;
    }
    if (t.firstWaveRocks > t.maxRocks) {
      problems.add("rocks.firstWave is above rocks.max; wave 1 has rocks.max rocks");
    }
    // there must be room to place rocks this far away: the farthest point of the cube is arena * sqrt(3) away
    final float room = t.arena * 1.5f;
    if (t.safeDistance > room) {
      problems.add("rocks.safeDistance is too large for the arena; using " + format(room));
      t.safeDistance = room;
    }
    return new Loaded(t, overrides, Collections.unmodifiableList(problems));
  }

  /** Reads a tuning file; a missing file gives the defaults. */
  public static Loaded load(Path file) throws IOException {
    return load(file, Difficulty.NORMAL);
  }

  /** Reads a tuning file on top of a difficulty's values; a missing file gives the difficulty's values. */
  public static Loaded load(Path file, Difficulty difficulty) throws IOException {
    final var p = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      p.load(in);
    } catch (NoSuchFileException e) {
      return new Loaded(of(difficulty), 0, List.of());
    }
    return from(p, difficulty);
  }

  /** Writes every key, commented out, with its default and what it does: a starting point for a tuning file. */
  public static void writeExample(Path file) throws IOException {
    final var d = new Tuning();
    try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write("# AISlopTeroids tuning: copy this file to tuning.properties next to it, remove the # in front of a\n");
      w.write("# line and change the value. It is read again for every new game. Games with changed values do not\n");
      w.write("# set a best score. This file is rewritten at every start; edit tuning.properties, not this one.\n");
      w.write("# The values below are Normal's; the other difficulties start from the values in brackets. A value set\n");
      w.write("# in tuning.properties counts for every difficulty, unless it is Normal's: that changes nothing.\n");
      String group = "";
      for (Param p : PARAMS.values()) {
        final var prefix = p.key().substring(0, p.key().indexOf('.'));
        if (!prefix.equals(group)) {
          group = prefix;
          w.write("\n");
        }
        final var others = new StringBuilder();
        for (Difficulty level : Difficulty.values()) {
          final var v = level.changes.get(p.key());
          if (v != null) {
            others.append(others.isEmpty() ? " (" : ", ").append(level.label).append(": ").append(format(v));
          }
        }
        w.write("# " + p.description() + (others.isEmpty() ? "" : others + ")") + "\n");
        w.write("#" + p.key() + " = " + format(p.get().apply(d)) + "\n");
      }
    }
  }

  /** The keys a tuning file can have, in file order. */
  public static List<String> keys() {
    return List.copyOf(PARAMS.keySet());
  }

  private static String format(Number n) {
    final double v = n.doubleValue();
    return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%s", n);
  }
}
