package aislopteroids.model;

import org.joml.Vector3f;

/**
 * An enemy ship: it wanders the arena, shoots at the player, and leaves again after a while. The big one is tough and
 * accurate; the scout, from a later wave on, is small and quick, takes fewer hits and may swerve out of a shot's way.
 */
public final class Boss {
  public enum Kind {
    BIG(World.BOSS_RADIUS), SCOUT(World.SCOUT_RADIUS);

    public final float radius;

    Kind(float radius) {
      this.radius = radius;
    }
  }

  public final Kind kind;
  public final Vector3f position = new Vector3f();
  public final Vector3f velocity = new Vector3f();
  public int health;
  /** Until it next picks a new direction, fires, and leaves. */
  public float turnTimer;
  public float fireTimer;
  public float timeLeft;

  public Boss() {
    this(Kind.BIG);
  }

  public Boss(Kind kind) {
    this.kind = kind;
  }

  public float radius() {
    return this.kind.radius;
  }
}
