package astroids;

import org.joml.Vector3f;

import engine.ecs.Entity;
import engine.ecs.Scene;
import engine.render.ParticleEmitter;

/**
 * What blows out of the player's ship, seen from behind it: a hot flame out of the engine at its tail, which grows with
 * the thrust ahead, and puffs of gas from the steering thrusters at the wingtips while the ship turns. All of it moves
 * with the ship, and none of it is there while the ship is gone or seen from inside.
 */
final class Exhaust {
  /** How fast the flame follows the thrust (full in about 1/8 of a second). */
  private static final float RESPONSE = 8f;
  private static final float FLAME_RATE = 180f;
  private static final float PUFF_RATE = 140f;
  /** A turn this fast (degrees per second) fires its thrusters fully. */
  static final float FULL_TURN = 90f;
  /** Below this share of full the thrusters stay quiet: a hand resting on the mouse does not fire them. */
  static final float PUFF_THRESHOLD = 0.1f;

  private final ParticleEmitter flame;
  private final ParticleEmitter[] puffs = new ParticleEmitter[Art.PLAYER_THRUSTERS.length];
  /** The thrust shown, 0..1: follows the thrust ahead smoothly. */
  private float throttle;
  private boolean shown;

  /** Adds the flame and thrusters to {@code ship}, the player's whole ship. */
  Exhaust(Scene scene, Entity ship) {
    this.flame = flame(add(scene, ship, "exhaust", Art.PLAYER_ENGINE, 0.03f));
    final var out = new Vector3f();
    for (int i = 0; i < this.puffs.length; i++) {
      this.puffs[i] = puff(add(scene, ship, "thruster", Art.PLAYER_THRUSTERS[i], 0.04f));
      // back, and a little out along the wing, so it is seen from behind
      this.puffs[i].direction.set(Art.wingDirection(i, out).mul(0.4f)).add(0f, 0f, 1f);
    }
  }

  private static ParticleEmitter add(Scene scene, Entity ship, String name, Vector3f at, float behind) {
    final var e = new Entity(name);
    e.transform().position().set(at.x, at.y, at.z + behind);
    e.transform().setParent(ship.transform());
    final var emitter = e.add(new ParticleEmitter());
    scene.add(e);
    emitter.localSpace = true;
    emitter.blend = ParticleEmitter.Blend.ADDITIVE;
    emitter.softness = 0f; // they start right at the hull: do not fade them there
    emitter.emitting = false;
    return emitter;
  }

  /** A short, bright jet out of the engine, white-hot at the start and orange at its end. */
  private static ParticleEmitter flame(ParticleEmitter f) {
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
    return f;
  }

  /** A puff of cold gas: pale blue, it spreads out and thins quickly. */
  private static ParticleEmitter puff(ParticleEmitter p) {
    p.maxParticles = 48;
    p.spreadDegrees = 12f;
    p.shapeRadius = 0.02f;
    p.lifetimeMin = 0.12f;
    p.lifetimeMax = 0.24f;
    p.drag = 4f;
    p.sizeStart = 0.07f;
    p.sizeEnd = 0.22f;
    p.sizeVariation = 0.3f;
    p.colorStart.set(0.85f, 0.95f, 1f, 0.75f);
    p.colorEnd.set(0.55f, 0.75f, 1f, 0f);
    p.brightness = 2f;
    return p;
  }

  /**
   * Brings it up to date: {@code ahead} is how hard the ship thrusts ahead (0..1; braking uses none),
   * {@code yawSpeed} and {@code pitchSpeed} how fast it turns to the left and upwards (degrees per second, negative the
   * other way), {@code visible} whether the ship is seen from outside and flying.
   */
  void show(float ahead, float yawSpeed, float pitchSpeed, boolean visible, Quality quality, float dt) {
    final float target = visible ? Math.max(0f, Math.min(1f, ahead)) : 0f;
    this.throttle = this.throttle < target ? Math.min(target, this.throttle + dt * RESPONSE)
        : Math.max(target, this.throttle - dt * RESPONSE);
    if (!visible && this.shown) {
      // the ship is hidden by scaling it to nothing, which would gather the particles into glowing dots
      this.flame.clear();
      for (var p : this.puffs) {
        p.clear();
      }
    }
    this.shown = visible;
    final float u = this.throttle;
    this.flame.emitting = visible && u > 0.02f;
    this.flame.rate = FLAME_RATE * u * quality.effects;
    this.flame.speedMin = 2.5f + 3f * u;
    this.flame.speedMax = 3.5f + 4f * u;
    for (int i = 0; i < this.puffs.length; i++) {
      final var p = this.puffs[i];
      final float strength = thrusterStrength(i, -yawSpeed / FULL_TURN, pitchSpeed / FULL_TURN);
      p.emitting = visible && strength > PUFF_THRESHOLD;
      p.rate = PUFF_RATE * strength * quality.effects;
      p.speedMin = 2f + 2f * strength;
      p.speedMax = 3f + 3f * strength;
    }
  }

  /**
   * How hard thruster {@code i} fires (0..1) while the ship turns {@code right} and {@code up} (shares of
   * {@link #FULL_TURN}, negative for left and down). Blowing back pushes its wingtip forward, so the ship turns away
   * from it: turning right fires the left wing's thrusters, turning up the lower ones, and the other way round.
   */
  static float thrusterStrength(int i, float right, float up) {
    return Math.min(1f, Math.max(0f, -Art.WING_X[i] * right) + Math.max(0f, -Art.WING_Y[i] * up));
  }
}
