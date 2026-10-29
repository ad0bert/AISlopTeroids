package astroids;

import engine.ecs.Entity;
import engine.ecs.Scene;
import engine.render.ParticleEmitter;

/**
 * The fire out of the player's engine, seen from behind the ship: a hot flame that moves with the ship. It grows with
 * the thrust ahead and dies down when it stops; it is not there while the ship is gone or seen from inside.
 */
final class Exhaust {
  /** Where the engine is on the ship (as {@link Art#playerParts}): the glowing nozzle at its tail. */
  static final float NOZZLE_Z = 1.05f;
  /** How fast the fire follows the thrust (full in about 1/8 of a second). */
  private static final float RESPONSE = 8f;
  private static final float FLAME_RATE = 180f;

  private final ParticleEmitter flame;
  /** The thrust shown, 0..1: follows the thrust ahead smoothly. */
  private float throttle;
  private boolean shown;

  /** Adds the fire to {@code ship}, the player's whole ship. */
  Exhaust(Scene scene, Entity ship) {
    final var nozzle = new Entity("exhaust");
    nozzle.transform().position().set(0f, 0f, NOZZLE_Z);
    nozzle.transform().setParent(ship.transform());
    this.flame = nozzle.add(new ParticleEmitter());
    scene.add(nozzle);

    // a short, bright jet out of the nozzle, white-hot at the start and orange at its end, held to the ship
    final var f = this.flame;
    f.localSpace = true;
    f.blend = ParticleEmitter.Blend.ADDITIVE;
    f.maxParticles = 96;
    f.direction.set(0f, 0f, 1f);
    f.spreadDegrees = 5f;
    f.shapeRadius = 0.04f;
    f.lifetimeMin = 0.1f;
    f.lifetimeMax = 0.2f;
    f.sizeStart = 0.34f;
    f.sizeEnd = 0.06f;
    f.sizeVariation = 0.25f;
    f.colorStart.set(1f, 0.88f, 0.6f, 0.9f);
    f.colorEnd.set(1f, 0.3f, 0.05f, 0f);
    f.brightness = 3f;
    f.softness = 0f; // it starts right at the hull: do not fade it there
    f.emitting = false;
  }

  /**
   * Brings the fire up to date: {@code ahead} is how hard the ship thrusts ahead (0..1, the main engine; sliding and
   * braking use none), {@code visible} whether the ship is seen from outside and flying.
   */
  void show(float ahead, boolean visible, Quality quality, float dt) {
    final float target = visible ? Math.max(0f, Math.min(1f, ahead)) : 0f;
    this.throttle = this.throttle < target ? Math.min(target, this.throttle + dt * RESPONSE)
        : Math.max(target, this.throttle - dt * RESPONSE);
    if (!visible && this.shown) {
      // the ship is hidden by scaling it to nothing, which would gather the flame into one glowing dot
      this.flame.clear();
    }
    this.shown = visible;
    final float u = this.throttle;
    this.flame.emitting = visible && u > 0.02f;
    this.flame.rate = FLAME_RATE * u * quality.effects;
    this.flame.speedMin = 2.5f + 3f * u;
    this.flame.speedMax = 3.5f + 4f * u;
  }
}
