package astroids.model;

import org.joml.Vector3f;

/** The player's ship. Where it looks is the view's business; the model only needs the aim when it fires. */
public final class Ship {
  public final Vector3f position = new Vector3f();
  public final Vector3f velocity = new Vector3f();
  public boolean alive = true;
  /** Seconds of protection left after (re)spawning. */
  public float invulnerable;
  /** Seconds until the ship comes back after it was destroyed. */
  public float respawnIn;
  public float fireCooldown;

  public boolean canBeHit() {
    return this.alive && this.invulnerable <= 0f;
  }
}
