package astroids.model;

import org.joml.Vector3f;

/** The small enemy ship: it wanders the arena, shoots at the player, and leaves again after a while. */
public final class Boss {
  public final Vector3f position = new Vector3f();
  public final Vector3f velocity = new Vector3f();
  public int health;
  /** Until it next picks a new direction, fires, and leaves. */
  public float turnTimer;
  public float fireTimer;
  public float timeLeft;
}
