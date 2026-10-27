package astroids.model;

import org.joml.Vector3f;

/** A rock drifting through the arena. Big ones split in two when hit, small ones are destroyed. */
public final class Asteroid {
  public enum Size {
    BIG(World.BIG_RADIUS), SMALL(World.SMALL_RADIUS);

    public final float radius;

    Size(float radius) {
      this.radius = radius;
    }
  }

  public final Size size;
  public final Vector3f position = new Vector3f();
  public final Vector3f velocity = new Vector3f();
  /** For the view: the axis it tumbles about, how fast (radians per second), and a lumpy non-uniform scale. */
  public final Vector3f spinAxis = new Vector3f(0f, 1f, 0f);
  public float spinSpeed;
  public final Vector3f shape = new Vector3f(1f);
  /** For the view: which of its rock shapes this one has (any number; the view takes it modulo how many it has). */
  public int variant;

  public Asteroid(Size size) {
    this.size = size;
  }

  public float radius() {
    return this.size.radius;
  }
}
