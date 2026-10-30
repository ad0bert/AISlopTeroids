package aislopteroids;

/**
 * Shakes the gamepads for what happens in the game: the ship lost, a hyperspace jump, rocks and enemy ships blowing up
 * nearby, a light tick for every shot. A pad keeps only the latest rumble it was given, so a weak one (a shot) that
 * comes along would cut short a strong one (the ship blowing up): this lets a new rumble through only when it is at
 * least as strong as what is left of the one running.
 */
final class Rumble {
  /** How far away (metres) an explosion still shakes the pad, less and less with the distance. */
  static final float REACH = 40f;

  /** Where the rumbles go: {@code engine.input()::rumble}, or a test's record of them. */
  interface Motors {
    void rumble(float strong, float weak, float seconds);
  }

  private final Motors motors;
  /** The player's choice in the options. */
  boolean enabled = true;
  private float level;
  private float until;

  Rumble(Motors motors) {
    this.motors = motors;
  }

  /**
   * Shakes the pads at {@code now} (seconds, any clock that runs on), unless a stronger rumble is still running or the
   * player turned rumble off.
   */
  void shake(float strong, float weak, float seconds, float now) {
    if (!this.enabled || seconds <= 0f || strong + weak <= 0f) {
      return;
    }
    final float running = now < this.until ? this.level : 0f;
    final float level = Math.max(strong, weak);
    if (level < running) {
      return;
    }
    this.level = level;
    this.until = now + seconds;
    this.motors.rumble(strong, weak, seconds);
  }

  /** Stops the pads at once (pause). */
  void stop() {
    this.until = 0f;
    this.level = 0f;
    this.motors.rumble(0f, 0f, 0f);
  }

  /** How much an explosion {@code distance} metres away shakes: 1 right here, 0 from {@link #REACH} on. */
  static float nearness(float distance) {
    final float u = Math.max(0f, 1f - distance / REACH);
    return u * u;
  }
}
