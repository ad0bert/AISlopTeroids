package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import engine.render.MeshData;

class RockArtTest {
  private static Vector3f position(MeshData m, int vertex) {
    final int o = vertex * MeshData.FLOATS_PER_VERTEX;
    return new Vector3f(m.vertices()[o], m.vertices()[o + 1], m.vertices()[o + 2]);
  }

  private static Vector3f normal(MeshData m, int vertex) {
    final int o = vertex * MeshData.FLOATS_PER_VERTEX + 3;
    return new Vector3f(m.vertices()[o], m.vertices()[o + 1], m.vertices()[o + 2]);
  }

  @Test
  void aRockIsAClosedLumpOfAboutItsCollisionSize() {
    final var m = RockArt.mesh(1000L);
    final int cols = RockArt.SEGMENTS + 1;
    assertEquals(cols * (RockArt.RINGS + 1), m.vertexCount());
    double sum = 0;
    for (int i = 0; i < m.vertexCount(); i++) {
      final float r = position(m, i).length();
      sum += r;
      assertTrue(r > 0.7f && r < 1.3f, "radius " + r + " at vertex " + i); // MAX_BULGE, plus the re-centring
      final var n = normal(m, i);
      assertEquals(1f, n.length(), 1e-3f, "unit normals");
      assertTrue(n.dot(position(m, i)) > 0f, "normals point outwards");
    }
    assertEquals(1.0, sum / m.vertexCount(), 1e-3, "an average radius of 1, like the collision sphere");

    for (int r = 0; r <= RockArt.RINGS; r++) { // the seam's copies are the same point, with the same normal
      final int first = r * cols;
      final int last = first + RockArt.SEGMENTS;
      assertEquals(0f, position(m, first).distance(position(m, last)), 1e-5f, "seam closed on ring " + r);
      assertEquals(0f, normal(m, first).distance(normal(m, last)), 1e-5f, "no seam in the shading on ring " + r);
    }
    for (int s = 1; s < cols; s++) { // all of a pole's copies are one point
      assertEquals(0f, position(m, 0).distance(position(m, s)), 1e-5f);
      final int top = RockArt.RINGS * cols;
      assertEquals(0f, position(m, top).distance(position(m, top + s)), 1e-5f);
    }
  }

  @Test
  void everyShapeTheGameUsesStaysCloseToItsCollisionSize() {
    for (int i = 0; i < Art.ROCK_SHAPES; i++) {
      final var m = RockArt.mesh(Art.ROCK_SEED + i);
      for (int v = 0; v < m.vertexCount(); v++) {
        final float r = position(m, v).length();
        assertTrue(r > 0.7f && r < 1.3f, "shape " + i + ": radius " + r);
      }
    }
  }

  @Test
  void eachSeedIsADifferentShapeAndTheSameSeedTheSameOne() {
    final var a = RockArt.mesh(1000L);
    final var b = RockArt.mesh(1001L);
    assertArrayEquals(a.vertices(), RockArt.mesh(1000L).vertices());
    assertFalse(Arrays.equals(a.vertices(), b.vertices()));
    float most = 0f;
    for (int i = 0; i < a.vertexCount(); i++) {
      most = Math.max(most, Math.abs(position(a, i).length() - position(b, i).length()));
    }
    assertTrue(most > 0.08f, "noticeably different, not just a little: " + most);
  }

  @Test
  void chipsAreSharpEdgedBrokenPiecesOfAboutUnitSize() {
    final var m = RockArt.chip(2000L);
    assertEquals(0, m.vertexCount() % 3, "every triangle has its own corners");
    for (int t = 0; t < m.vertexCount(); t += 3) {
      final var n = normal(m, t);
      assertEquals(0f, n.distance(normal(m, t + 1)), 1e-6f, "flat faces: one normal per triangle");
      assertEquals(0f, n.distance(normal(m, t + 2)), 1e-6f);
      final var centre = position(m, t).add(position(m, t + 1)).add(position(m, t + 2));
      assertTrue(n.dot(centre) > 0f, "facing outwards");
    }
    for (int i = 0; i < m.vertexCount(); i++) {
      assertTrue(position(m, i).length() <= 1.2f);
    }
    assertFalse(Arrays.equals(m.vertices(), RockArt.chip(2001L).vertices()));
  }

  @Test
  void dustFillsABallThickerInTheMiddle() {
    final var points = RockArt.dust(400, 3L);
    assertEquals(400, points.size());
    long inner = points.stream().filter(p -> p.length() < 0.5f).count();
    assertTrue(points.stream().allMatch(p -> p.length() <= 1f));
    assertTrue(inner > 400 * 0.125, "more than an even spread would put in the inner half: " + inner);
  }

  @Test
  void theSurfaceTilesWithoutASeam() {
    final int size = RockArt.TEXTURE_SIZE;
    final float[] h = RockArt.heights(size, 7L);
    float min = 1f;
    float max = 0f;
    float inside = 0f; // the largest step between neighbours inside the texture
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        final float v = h[y * size + x];
        min = Math.min(min, v);
        max = Math.max(max, v);
        if (x + 1 < size) {
          inside = Math.max(inside, Math.abs(v - h[y * size + x + 1]));
        }
      }
    }
    assertEquals(0f, min, 1e-6f);
    assertEquals(1f, max, 1e-6f);
    for (int y = 0; y < size; y++) { // across the left and right edges: no bigger a step than inside
      assertTrue(Math.abs(h[y * size] - h[y * size + size - 1]) <= inside);
    }
    for (int x = 0; x < size; x++) { // across the top and bottom edges
      assertTrue(Math.abs(h[x] - h[(size - 1) * size + x]) <= inside);
    }
    final var albedo = RockArt.albedo(h, size, 8L);
    final var normals = RockArt.normals(h, size);
    assertEquals(size, albedo.width());
    assertEquals(size * size * 4, normals.rgba().length);
  }
}
