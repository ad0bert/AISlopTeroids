package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import engine.render.MeshData;

class CockpitArtTest {
  private static Vector3f at(MeshData m, int vertex, int offset) {
    final int o = vertex * MeshData.FLOATS_PER_VERTEX + offset;
    return new Vector3f(m.vertices()[o], m.vertices()[o + 1], m.vertices()[o + 2]);
  }

  /** Every triangle is wound the way its normals point (the renderer turns a triangle's normal seen from behind). */
  private static void facesItsNormals(MeshData m) {
    final int[] idx = m.indices();
    assertTrue(idx.length > 0);
    for (int t = 0; t < idx.length; t += 3) {
      final var a = at(m, idx[t], 0);
      final var face = at(m, idx[t + 1], 0).sub(a).cross(at(m, idx[t + 2], 0).sub(a));
      final var normal = at(m, idx[t], 3).add(at(m, idx[t + 1], 3)).add(at(m, idx[t + 2], 3));
      assertTrue(face.dot(normal) > 0f, "triangle " + t / 3 + " faces its normals");
    }
  }

  @Test
  void theGlassIsAroundThePilotAndFacesThem() {
    final var glass = CockpitArt.canopy(32, 10);
    facesItsNormals(glass);
    for (int i = 0; i < glass.vertexCount(); i++) {
      final var p = at(glass, i, 0);
      final var n = at(glass, i, 3);
      assertEquals(1f, n.length(), 1e-3f, "unit normals");
      assertTrue(p.y >= CockpitArt.CENTER.y - 1e-4f, "only above the sill");
      assertTrue(p.length() > 0.5f, "not in the pilot's face");
      assertTrue(n.dot(new Vector3f(p).negate()) > 0f, "the inside, where the pilot is, is the front");
    }
  }

  @Test
  void theFrameAndTheDashboardKeepTheMiddleOfTheViewClear() {
    final int width = 1280;
    final int height = 720;
    facesItsNormals(CockpitArt.dashboard(32));
    final var arch = CockpitArt.arch(48, 0.02f);
    facesItsNormals(CockpitArt.tube(arch, 0.02f, 8));
    for (var p : arch) {
      final var s = Hud.project(p, width, height, 75f);
      assertNotNull(s, "the arch is ahead");
      final boolean onScreen = s[0] >= 0f && s[0] <= width && s[1] >= 0f && s[1] <= height;
      if (onScreen) {
        // only across the top corners
        assertTrue(Math.abs(s[0] - width / 2f) > width * 0.15f && s[1] < height * 0.4f, "the arch keeps to the corners");
      }
    }
    final var sill = Hud.project(CockpitArt.sill(0f, 0f), width, height, 75f);
    assertTrue(sill[1] > height * 0.8f && sill[1] < height, "the dashboard is low in the view, but in it");
    final var readout = Hud.project(CockpitArt.sill(GlassDisplay.READOUT_DEGREES, GlassDisplay.READOUT_LIFT), width,
        height, 75f);
    assertTrue(readout[0] > width / 2f && readout[0] < width * 0.8f, "the readouts are clear of the radar");
    assertTrue(readout[1] < sill[1], "the readouts are above the dashboard");
  }

  @Test
  void shotsInTheCockpitComeFromBehindTheDashboard() {
    final int width = 1280;
    final int height = 720;
    for (var gun : Art.COCKPIT_GUNS) {
      final var s = Hud.project(gun, width, height, 75f);
      // the sill straight ahead of the gun: the gun is lower on the screen, so the dashboard hides it
      final double deg = Math.toDegrees(Math.atan2(gun.x, -gun.z));
      final var sill = Hud.project(CockpitArt.sill((float) deg, 0f), width, height, 75f);
      assertTrue(s[1] > sill[1] + 10f, "the gun is below the dashboard's edge");
    }
  }

  @Test
  void digitsLightTheirSegmentsWithoutLeadingZeros() {
    final int[] seven = GlassDisplay.segments(7);
    assertEquals(0, seven[0]);
    assertEquals(0, seven[1]);
    assertEquals(GlassDisplay.DIGITS[7], seven[2]);
    final int[] zero = GlassDisplay.segments(0);
    assertEquals(GlassDisplay.DIGITS[0], zero[2], "a lone zero is shown");
    final int[] big = GlassDisplay.segments(105);
    assertEquals(GlassDisplay.DIGITS[1], big[0]);
    assertEquals(GlassDisplay.DIGITS[0], big[1], "a zero inside a number is shown");
    assertEquals(Integer.bitCount(GlassDisplay.DIGITS[8]), 7);
  }
}
