package astroids;

/**
 * The ship's turn, with inertia: the mouse and stick set where the player wants to look ({@link #targetYaw},
 * {@link #targetPitch}), and the ship turns there as if its steering thrusters first speed the turn up and then brake
 * it, so it lags a little behind and settles without overshooting (a critically damped spring). Angles in degrees.
 */
final class Turning {
  /** How quickly the ship follows, per second: it is most of the way there after about 3 / this seconds. */
  static final float RESPONSE = 9f;
  /** Steepest pitch, up or down. */
  static final float MAX_PITCH = 85f;

  float targetYaw;
  float targetPitch;
  /** Where the ship points now. */
  float yaw;
  float pitch;
  /** How fast it turns, degrees per second, and how fast that changes (the thrusters' work), per second squared. */
  float yawSpeed;
  float pitchSpeed;
  float yawAcceleration;
  float pitchAcceleration;

  /** Turns the wanted direction by this much (from the mouse or stick); the pitch stops at {@link #MAX_PITCH}. */
  void look(float yawDegrees, float pitchDegrees) {
    this.targetYaw += yawDegrees;
    this.targetPitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, this.targetPitch + pitchDegrees));
  }

  /** Turns the ship towards the wanted direction for {@code dt} seconds. */
  void step(float dt) {
    final float k = RESPONSE;
    this.yawAcceleration = k * k * (this.targetYaw - this.yaw) - 2f * k * this.yawSpeed;
    this.pitchAcceleration = k * k * (this.targetPitch - this.pitch) - 2f * k * this.pitchSpeed;
    this.yawSpeed += this.yawAcceleration * dt;
    this.pitchSpeed += this.pitchAcceleration * dt;
    this.yaw += this.yawSpeed * dt;
    this.pitch += this.pitchSpeed * dt;
  }
}
