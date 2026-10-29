package aislopteroids;

import engine.ecs.Component;
import engine.ecs.Entity;

/** Keeps its entity at another entity's position (the star field stays around the camera). */
final class Follow extends Component {
  private final Entity target;

  Follow(Entity target) {
    this.target = target;
  }

  @Override
  public void update(double dt) {
    entity().transform().position().set(this.target.transform().position());
  }
}
