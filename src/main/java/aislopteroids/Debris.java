package aislopteroids;

import org.joml.Vector3f;

import engine.ecs.Component;
import engine.ecs.Scene;

/**
 * A piece flung out of an explosion: it flies, tumbles, and shrinks away towards the end of its life, then removes its
 * entity.
 */
final class Debris extends Component {
  private final Scene scene;
  private final Vector3f velocity = new Vector3f();
  private final Vector3f spinAxis = new Vector3f();
  private final float spinSpeed;
  private final float lifetime;
  private final float size;
  /** The part of its life (from the end) over which it shrinks away: 1 shrinks all the time, like a flame. */
  private final float fadeShare;
  /** How quickly it slows down, per second. */
  private final float drag;
  private float age;

  /** A burning piece: it shrinks all the time and slows down quickly. */
  Debris(Scene scene, Vector3f velocity, Vector3f spinAxis, float spinSpeed, float lifetime, float size) {
    this(scene, velocity, spinAxis, spinSpeed, lifetime, size, 1f, 0.6f);
  }

  Debris(Scene scene, Vector3f velocity, Vector3f spinAxis, float spinSpeed, float lifetime, float size,
      float fadeShare, float drag) {
    this.scene = scene;
    this.velocity.set(velocity);
    this.spinAxis.set(spinAxis).normalize();
    this.spinSpeed = spinSpeed;
    this.lifetime = lifetime;
    this.size = size;
    this.fadeShare = Math.max(1e-3f, fadeShare);
    this.drag = drag;
  }

  @Override
  public void update(double dtD) {
    final float dt = (float) dtD;
    this.age += dt;
    final var t = entity().transform();
    if (this.age >= this.lifetime) {
      this.scene.remove(entity());
      return;
    }
    t.position().fma(dt, this.velocity);
    this.velocity.mul((float) Math.exp(-this.drag * dt));
    t.rotation().rotateAxis(this.spinSpeed * dt, this.spinAxis);
    final float left = (this.lifetime - this.age) / (this.lifetime * this.fadeShare);
    t.scale().set(this.size * Math.min(1f, left));
  }
}
