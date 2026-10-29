package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class TurningAndThrustersTest {
  private static final float STEP = 1f / 60f;

  @Test
  void theShipTurnsAfterTheMouseAndSettlesWithoutOvershooting() {
    final var turn = new Turning();
    turn.look(30f, -10f);
    turn.step(STEP);
    assertTrue(turn.yaw > 0f && turn.yaw < 5f, "it starts turning, but lags behind: " + turn.yaw);
    assertTrue(turn.yawAcceleration > 0f, "the thrusters speed the turn up");
    boolean braked = false;
    for (int i = 0; i < 120; i++) {
      turn.step(STEP);
      braked |= turn.yawAcceleration < 0f;
      assertTrue(turn.yaw <= 30f + 1e-3f, "no overshoot: " + turn.yaw);
    }
    assertTrue(braked, "and then brake it");
    assertEquals(30f, turn.yaw, 0.05f, "two seconds later it points where the player looks");
    assertEquals(-10f, turn.pitch, 0.05f);
  }

  @Test
  void thePitchStopsShortOfStraightUp() {
    final var turn = new Turning();
    turn.look(0f, 200f);
    assertEquals(Turning.MAX_PITCH, turn.targetPitch);
  }

  @Test
  void fourWingsInAnXWithTheGunsTakingTurnsAcrossIt() {
    assertEquals(4, Art.PLAYER_GUNS.length);
    for (int i = 0; i < 4; i++) {
      final var gun = Art.PLAYER_GUNS[i];
      assertEquals(Art.WING_X[i], Math.signum(gun.x), "gun " + i);
      assertEquals(Art.WING_Y[i], Math.signum(gun.y), "gun " + i);
      assertTrue(Math.abs(gun.x) > Math.abs(gun.y), "the wings spread wider than they open");
      assertTrue(new Vector3f(gun.x, gun.y, 0f).length() < 1.3f, "the tips fit the ship's size");
      // each shot after the last comes from the opposite corner, or from the other wing on the far side
      final var next = Art.PLAYER_GUNS[(i + 1) % 4];
      assertTrue(Math.signum(next.x) != Math.signum(gun.x) || Math.signum(next.y) != Math.signum(gun.y));
    }
    final int[][] corners = new int[2][2];
    for (int i = 0; i < 4; i++) {
      corners[(Art.WING_X[i] + 1) / 2][(Art.WING_Y[i] + 1) / 2]++;
    }
    for (int[] row : corners) {
      assertEquals(1, row[0]);
      assertEquals(1, row[1]);
    }
  }

  @Test
  void turningFiresTheThrustersOnTheOtherSide() {
    for (int i = 0; i < 4; i++) {
      final boolean leftWing = Art.WING_X[i] < 0;
      final boolean lowerWing = Art.WING_Y[i] < 0;
      assertEquals(leftWing ? 1f : 0f, Exhaust.thrusterStrength(i, 1f, 0f), "turning right: thruster " + i);
      assertEquals(leftWing ? 0f : 1f, Exhaust.thrusterStrength(i, -1f, 0f), "turning left: thruster " + i);
      assertEquals(lowerWing ? 1f : 0f, Exhaust.thrusterStrength(i, 0f, 1f), "turning up: thruster " + i);
      assertEquals(lowerWing ? 0f : 1f, Exhaust.thrusterStrength(i, 0f, -1f), "turning down: thruster " + i);
      assertEquals(0f, Exhaust.thrusterStrength(i, 0f, 0f), "quiet without a turn");
    }
  }
}
