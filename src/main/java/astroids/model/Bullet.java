package astroids.model;

import org.joml.Vector3f;

/** A shot from the player's ship or from the boss; it flies straight until it hits something or runs out. */
public final class Bullet {
  public final boolean fromBoss;
  public final Vector3f position = new Vector3f();
  public final Vector3f velocity = new Vector3f();
  public float timeLeft;

  public Bullet(boolean fromBoss) {
    this.fromBoss = fromBoss;
  }
}
