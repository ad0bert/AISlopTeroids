package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RumbleTest {
  private final List<float[]> sent = new ArrayList<>();
  private final Rumble rumble = new Rumble((strong, weak, seconds) -> this.sent.add(new float[] { strong, weak,
      seconds }));

  @Test
  void aWeakRumbleDoesNotCutAStrongOneShort() {
    this.rumble.shake(1f, 0.8f, 0.7f, 10f); // the ship blows up
    this.rumble.shake(0f, 0.15f, 0.06f, 10.1f); // a shot while it shakes
    assertEquals(1, this.sent.size(), "the shot's tick is dropped");
    this.rumble.shake(1f, 0.5f, 0.2f, 10.2f);
    assertEquals(2, this.sent.size(), "as strong goes through");
    this.rumble.shake(0f, 0.15f, 0.06f, 10.5f);
    assertEquals(3, this.sent.size(), "once the strong one is over, a weak one goes through");
    assertEquals(0.15f, this.sent.get(2)[1]);
  }

  @Test
  void offMeansNothingAndStopStopsAtOnce() {
    this.rumble.enabled = false;
    this.rumble.shake(1f, 1f, 1f, 0f);
    assertTrue(this.sent.isEmpty());
    this.rumble.enabled = true;
    this.rumble.shake(1f, 1f, 1f, 0f);
    this.rumble.stop();
    assertEquals(0f, this.sent.get(1)[2], "a zero-length rumble stops the motors");
    this.rumble.shake(0f, 0.1f, 0.1f, 0.1f);
    assertEquals(3, this.sent.size(), "nothing left running to outrank it");
  }

  @Test
  void explosionsShakeLessFurtherAway() {
    assertEquals(1f, Rumble.nearness(0f));
    assertTrue(Rumble.nearness(10f) > Rumble.nearness(20f));
    assertEquals(0f, Rumble.nearness(Rumble.REACH));
    assertEquals(0f, Rumble.nearness(1000f));
  }
}
