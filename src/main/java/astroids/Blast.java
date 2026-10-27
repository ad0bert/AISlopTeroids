package astroids;

import org.joml.Vector3f;

import engine.ecs.Component;
import engine.ecs.Scene;
import engine.render.Material;

/**
 * A puff of fire from an explosion: after {@code delay} it swells fast and then slowly; its glow cools from white-hot
 * through orange and dies away quickly, leaving dark smoke that fades more slowly; then it removes its entity.
 * {@code material} is its own (see-through, smoke-coloured, glowing).
 */
final class Blast extends Component {
  private final Scene scene;
  private final Material material;
  private final Vector3f velocity = new Vector3f();
  private final float delay;
  private final float startSize;
  private final float endSize;
  private final float lifetime;
  private float age;

  Blast(Scene scene, Material material, Vector3f velocity, float delay, float startSize, float endSize,
      float lifetime) {
    this.scene = scene;
    this.material = material;
    this.velocity.set(velocity);
    this.delay = delay;
    this.startSize = startSize;
    this.endSize = endSize;
    this.lifetime = lifetime;
  }

  @Override
  public void update(double dtD) {
    final float dt = (float) dtD;
    this.age += dt;
    final var t = entity().transform();
    t.position().fma(dt, this.velocity);
    final float u = (this.age - this.delay) / this.lifetime;
    if (u >= 1f) {
      this.scene.remove(entity());
      return;
    }
    if (u < 0f) {
      t.scale().set(0f); // not yet
      return;
    }
    final float grow = 1f - (1f - u) * (1f - u) * (1f - u);
    t.scale().set(this.startSize + (this.endSize - this.startSize) * grow);
    // white-hot, then orange, then dull red, while it fades
    final float hot = Math.max(0f, 1f - u * 2.5f);
    final float glow = (float) Math.pow(1f - u, 4); // the fire dies away quickly ...
    this.material.emissive.set(1.4f + 2.4f * hot, 0.45f + 2.2f * hot, 0.08f + 1.3f * hot).mul(2f * glow);
    this.material.color.w = 0.65f * (float) Math.pow(1f - u, 1.5); // ... and the smoke more slowly
  }
}
