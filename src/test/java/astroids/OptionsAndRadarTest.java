package astroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import engine.core.Settings;

class OptionsAndRadarTest {
  @TempDir
  Path dir;

  @Test
  void optionsSurviveASaveAndBadValuesAreClamped() throws IOException {
    final var file = this.dir.resolve("settings.properties");
    final var first = Options.load(Settings.load(file), false, true);
    assertEquals(Options.DEFAULT_SENSITIVITY, first.mouseSensitivity);
    assertFalse(first.invertY);
    assertTrue(first.radar);
    assertTrue(first.vsync, "the window's state is the default");

    first.mouseSensitivity = 0.2f;
    first.invertY = true;
    first.masterVolume = 0.4f;
    first.radar = false;
    first.fullscreen = true;
    final var settings = Settings.load(file);
    first.store(settings);
    settings.save();

    final var again = Options.load(Settings.load(file), false, true);
    assertEquals(0.2f, again.mouseSensitivity, 1e-6f);
    assertTrue(again.invertY);
    assertEquals(0.4f, again.masterVolume, 1e-6f);
    assertFalse(again.radar);
    assertTrue(again.fullscreen, "the saved choice wins over the window's state");

    final var wild = Settings.load(file).set("controls.mouseSensitivity", 50f).set("audio.master", -3f);
    final var clamped = Options.load(wild, false, true);
    assertEquals(Options.MAX_SENSITIVITY, clamped.mouseSensitivity);
    assertEquals(0f, clamped.masterVolume);
  }

  @Test
  void invertYFlipsTheVerticalLook() {
    final var o = new Options();
    assertTrue(o.pitchFor(10) < 0f, "moving the mouse down looks down");
    o.invertY = true;
    assertTrue(o.pitchFor(10) > 0f, "inverted: moving the mouse down looks up");
    assertTrue(o.yawFor(10) < 0f, "moving the mouse right turns right (negative yaw)");
  }

  @Test
  void theRadarShowsWhatIsAheadAtTheTopAndKeepsFarThingsOnTheRim() {
    final var view = new Quaternionf(); // looking along -Z
    final var ahead = Radar.project(new Vector3f(0f, 0f, -50f), view, 100f);
    assertEquals(0f, ahead.x(), 1e-5f);
    assertEquals(0.5f, ahead.y(), 1e-5f);

    final var right = Radar.project(new Vector3f(30f, 0f, 0f), view, 100f);
    assertEquals(0.3f, right.x(), 1e-5f);
    final var above = Radar.project(new Vector3f(0f, 40f, 0f), view, 100f);
    assertEquals(0.4f, above.height(), 1e-5f);

    final var far = Radar.project(new Vector3f(300f, 0f, -400f), view, 100f);
    assertEquals(1f, (float) Math.hypot(far.x(), far.y()), 1e-5f);
    assertEquals(0.6f, far.x(), 1e-5f);

    // turned 90 degrees to the left: what was on the left is now ahead
    final var turned = new Quaternionf().rotateY((float) Math.toRadians(90));
    final var left = Radar.project(new Vector3f(-50f, 0f, 0f), turned, 100f);
    assertEquals(0.5f, left.y(), 1e-5f);
    assertEquals(0f, left.x(), 1e-5f);
  }
}
