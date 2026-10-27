package astroids;

import org.joml.Vector3f;

import engine.ecs.Component;
import engine.ecs.Scene;
import engine.render.Material;

/**
 * A cloud of rock dust from a burst: it spreads out quickly at first, then slowly, drifts with what burst, and fades,
 * then removes its entity. The entity is a cloud of points that is scaled up; {@code material} is its own, for the fade.
 */
final class Dust extends Component {
  private final Scene scene;
  private final Material material;
  private final Vector3f velocity = new Vector3f();
  private final float startSize;
  private final float endSize;
  private final float lifetime;
  private final float alpha;
  private float age;

  Dust(Scene scene, Material material, Vector3f velocity, float startSize, float endSize, float lifetime) {
    this.scene = scene;
    this.material = material;
    this.velocity.set(velocity);
    this.startSize = startSize;
    this.endSize = endSize;
    this.lifetime = lifetime;
    this.alpha = material.color.w;
  }

  @Override
  public void update(double dtD) {
    final float dt = (float) dtD;
    this.age += dt;
    if (this.age >= this.lifetime) {
      this.scene.remove(entity());
      return;
    }
    final float u = this.age / this.lifetime;
    final float spread = 1f - (1f - u) * (1f - u) * (1f - u); // fast, then slower
    final var t = entity().transform();
    t.position().fma(dt, this.velocity);
    t.scale().set(this.startSize + (this.endSize - this.startSize) * spread);
    this.material.color.w = this.alpha * (1f - u) * (1f - u);
  }
}
