package astroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import engine.core.Settings;
import engine.render.RenderSettings;

class QualityAndCockpitTest {
  @TempDir
  Path dir;

  @Test
  void eachQualityLevelSwitchesTheRendererAndScalesTheBursts() {
    final var settings = new RenderSettings();
    Quality.LOW.apply(settings);
    assertEquals(0f, settings.bloomStrength, "no glow");
    assertFalse(settings.antiAliasing);
    assertFalse(settings.orderIndependentTransparency);
    Quality.HIGH.apply(settings);
    assertEquals(Quality.BLOOM, settings.bloomStrength);
    assertTrue(settings.antiAliasing);
    assertTrue(settings.orderIndependentTransparency);
    Quality.MEDIUM.apply(settings);
    assertTrue(settings.bloomStrength > 0f && settings.antiAliasing && !settings.orderIndependentTransparency);

    assertEquals(26, Quality.HIGH.scale(26));
    assertTrue(Quality.MEDIUM.scale(26) < 26 && Quality.LOW.scale(26) < Quality.MEDIUM.scale(26));
    assertEquals(1, Quality.LOW.scale(1), "never nothing");
    assertFalse(Quality.LOW.explosionLight);
    assertEquals(Quality.LOW, Quality.HIGH.next(), "round from High to Low");
    assertEquals(Quality.MEDIUM, Quality.LOW.next());
    assertEquals(Quality.HIGH, Quality.parse("nonsense"));
  }

  @Test
  void qualityAndTheCockpitAreSavedWithTheOptions() throws Exception {
    final var file = this.dir.resolve("settings.properties");
    final var first = Options.load(Settings.load(file), false, true);
    assertEquals(Quality.HIGH, first.quality, "high by default");
    assertTrue(first.cockpit);
    first.quality = Quality.LOW;
    first.cockpit = false;
    final var settings = Settings.load(file);
    first.store(settings);
    settings.save();
    final var again = Options.load(Settings.load(file), false, true);
    assertEquals(Quality.LOW, again.quality);
    assertFalse(again.cockpit);
  }

  @Test
  void theDeathCameraLooksWhereItIsAimed() {
    final var up = new Vector3f(0f, 1f, 0f);
    for (var dir : new Vector3f[] { new Vector3f(0f, 0f, -1f), new Vector3f(1f, -0.3f, 0.2f),
        new Vector3f(-0.4f, -0.8f, -0.5f) }) {
      final var q = PlayState.aim(dir, up);
      final var looks = q.transform(new Vector3f(0f, 0f, -1f)); // a camera looks along its -z
      assertEquals(0f, looks.distance(new Vector3f(dir).normalize()), 1e-4f, "looking along " + dir);
      final var screenUp = q.transform(new Vector3f(0f, 1f, 0f));
      assertTrue(screenUp.dot(up) > 0f, "upright, not upside down");
    }
  }
}
