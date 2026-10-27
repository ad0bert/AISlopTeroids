package astroids;

import engine.ecs.Component;
import engine.ecs.Scene;
import engine.render.Light;

/** The light of an explosion: bright at once, dying away quickly; then it removes its entity. */
final class Flash extends Component {
  private final Scene scene;
  private final Light light;
  private final float brightness;
  private final float lifetime;
  private float age;

  Flash(Scene scene, Light light, float lifetime) {
    this.scene = scene;
    this.light = light;
    this.brightness = light.intensity;
    this.lifetime = lifetime;
  }

  @Override
  public void update(double dtD) {
    this.age += (float) dtD;
    if (this.age >= this.lifetime) {
      this.scene.remove(entity());
      return;
    }
    final float left = 1f - this.age / this.lifetime;
    this.light.intensity = this.brightness * left * left;
  }
}
