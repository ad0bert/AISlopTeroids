package astroids;

import org.joml.Vector3f;

import engine.ecs.Component;
import engine.ecs.Scene;

/** A chunk flung out of an explosion: it flies, tumbles and shrinks away, then removes its entity. */
final class Debris extends Component {
  private final Scene scene;
  private final Vector3f velocity = new Vector3f();
  private final Vector3f spinAxis = new Vector3f();
  private final float spinSpeed;
  private final float lifetime;
  private final float size;
  private float age;

  Debris(Scene scene, Vector3f velocity, Vector3f spinAxis, float spinSpeed, float lifetime, float size) {
    this.scene = scene;
    this.velocity.set(velocity);
    this.spinAxis.set(spinAxis).normalize();
    this.spinSpeed = spinSpeed;
    this.lifetime = lifetime;
    this.size = size;
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
    this.velocity.mul((float) Math.exp(-0.6f * dt));
    t.rotation().rotateAxis(this.spinSpeed * dt, this.spinAxis);
    t.scale().set(this.size * (1f - this.age / this.lifetime));
  }
}
