package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import aislopteroids.model.World;
import engine.render.MeshData;

class ShipArtTest {
  private static Vector3f at(MeshData m, int vertex, int offset) {
    final int o = vertex * MeshData.FLOATS_PER_VERTEX + offset;
    return new Vector3f(m.vertices()[o], m.vertices()[o + 1], m.vertices()[o + 2]);
  }

  @Test
  void theHullsAreClosedSmoothSolidsThatFitTheirShip() {
    for (float[][] profile : new float[][][] { ShipArt.SAUCER, ShipArt.SCOUT }) {
      final int segments = 32;
      final var m = ShipArt.lathe(profile, segments);
      float widest = 0f;
      for (int i = 0; i < m.vertexCount(); i++) {
        final var p = at(m, i, 0);
        final var n = at(m, i, 3);
        assertEquals(1f, n.length(), 1e-3f, "unit normals");
        widest = Math.max(widest, (float) Math.hypot(p.x, p.z));
        // outward: away from the hull's middle axis, or up on top, or down below
        final var out = new Vector3f(p.x, 0f, p.z);
        assertTrue(n.dot(out) > -1e-3f || Math.abs(n.y) > 0.9f, "normal " + n + " at " + p);
      }
      assertTrue(widest <= 1.08f, "the rim fits the ship's radius: " + widest);
      final int cols = segments + 1;
      for (int r = 0; r < profile.length; r++) { // the seam is closed
        assertEquals(0f, at(m, r * cols, 0).distance(at(m, r * cols + segments, 0)), 1e-5f);
      }
    }
  }

  @Test
  void aPointGivenTwiceIsASharpEdge() {
    final float[][] cylinder = { { 0f, 0f }, { 1f, 0f }, { 1f, 0f }, { 1f, 1f }, { 1f, 1f }, { 0f, 1f } };
    final var m = ShipArt.lathe(cylinder, 8);
    final int cols = 9;
    assertEquals(0f, at(m, 1 * cols, 3).distance(0f, -1f, 0f), 1e-5f, "the bottom edge, seen from below: straight down");
    assertEquals(1f, at(m, 2 * cols, 3).x, 1e-5f, "the same point, seen from the side: straight out");
    assertEquals(0f, at(m, 2 * cols, 3).y, 1e-5f);
    // no faces between the two copies of an edge: 3 bands of quads, not 5
    assertEquals(3 * 8 * 6, m.indices().length);
  }

  @Test
  void spaceIsDarkWithASunWhereTheLightComesFrom() {
    final var toSun = new Vector3f(0.3f, 0.5f, -0.8f).normalize();
    final var sky = ShipArt.spaceSky(256, 128, toSun, new Vector3f(-0.3f, -0.6f, 0.8f), 11L);
    final float[] lum = new float[sky.width() * sky.height()];
    int brightest = 0;
    for (int i = 0; i < lum.length; i++) {
      lum[i] = sky.rgb()[i * 3] + sky.rgb()[i * 3 + 1] + sky.rgb()[i * 3 + 2];
      if (lum[i] > lum[brightest]) {
        brightest = i;
      }
    }
    final float[] sorted = lum.clone();
    Arrays.sort(sorted);
    assertTrue(sorted[sorted.length / 2] < 0.1f, "mostly dark: median " + sorted[sorted.length / 2]);
    // the brightest pixel looks towards the sun (the sky's mapping: longitude across, the top row straight up)
    final double lon = 2 * Math.PI * ((brightest % sky.width() + 0.5) / sky.width() - 0.5);
    final double theta = Math.PI * (brightest / sky.width() + 0.5) / sky.height();
    final var d = new Vector3f((float) (Math.sin(theta) * Math.sin(lon)), (float) Math.cos(theta),
        (float) (-Math.sin(theta) * Math.cos(lon)));
    assertTrue(d.dot(toSun) > 0.99f, "sun at " + d);
  }

  @Test
  void theWearTilesAndVariesTheShine() {
    final int size = 128;
    final var wear = ShipArt.wear(size, 21L);
    assertEquals(size * size * 4, wear.albedo().rgba().length);
    assertEquals(size * size * 4, wear.normals().rgba().length);
    int least = 255;
    int most = 0;
    for (int i = 0; i < size * size; i++) {
      final int rough = wear.metalRough().rgba()[i * 4 + 1] & 0xff;
      least = Math.min(least, rough);
      most = Math.max(most, rough);
    }
    assertTrue(most - least > 80, "polished and dull places: " + least + " to " + most);
    // the colour wraps round (u round the hull): there is a panel seam at the edge, as in the middle, and across
    // the edge it looks like across the middle one, not like a cut
    double edge = 0;
    double middle = 0;
    for (int y = 0; y < size; y++) {
      edge += Math.abs((wear.albedo().rgba()[(y * size) * 4] & 0xff)
          - (wear.albedo().rgba()[(y * size + size - 1) * 4] & 0xff));
      middle += Math.abs((wear.albedo().rgba()[(y * size + size / 2) * 4] & 0xff)
          - (wear.albedo().rgba()[(y * size + size / 2 - 1) * 4] & 0xff));
    }
    assertTrue(edge <= middle * 1.5 + size, "no cut where the texture wraps: " + edge + " vs " + middle);
  }

  @Test
  void wreckageIsABentJaggedPlateOfAboutUnitSize() {
    final var m = ShipArt.shard(3000L);
    float minY = Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (int i = 0; i < m.vertexCount(); i++) {
      final var p = at(m, i, 0);
      assertTrue(Math.hypot(p.x, p.z) <= 1.01, "within radius 1");
      assertEquals(1f, at(m, i, 3).length(), 1e-3f);
      minY = Math.min(minY, p.y);
      maxY = Math.max(maxY, p.y);
    }
    assertTrue(maxY - minY < 0.5f, "a plate, not a lump");
    assertTrue(maxY - minY > 0.1f, "but a bent one");
  }

  @Test
  void theEnemyShipsAreAFifthBiggerThanBeforeAndTheScoutAFifthMoreAgain() {
    assertEquals(2.4f, World.BOSS_RADIUS, 1e-6f);
    assertEquals(1.2f * 1.44f, World.SCOUT_RADIUS, 1e-6f);
  }
}
