package astroids.model;

import org.joml.Vector3f;

/** A rock drifting through the arena. Big ones split in two when hit, small ones are destroyed. */
public final class Asteroid {
  public enum Size {
    BIG(World.BIG_RADIUS, World.BIG_SCORE), SMALL(World.SMALL_RADIUS, World.SMALL_SCORE);

    public final float radius;
    public final int score;

    Size(float radius, int score) {
      this.radius = radius;
      this.score = score;
    }
  }

  public final Size size;
  public final Vector3f position = new Vector3f();
  public final Vector3f velocity = new Vector3f();
  /** For the view: the axis it tumbles about, how fast (radians per second), and a lumpy non-uniform scale. */
  public final Vector3f spinAxis = new Vector3f(0f, 1f, 0f);
  public float spinSpeed;
  public final Vector3f shape = new Vector3f(1f);

  public Asteroid(Size size) {
    this.size = size;
  }

  public float radius() {
    return this.size.radius;
  }
}
