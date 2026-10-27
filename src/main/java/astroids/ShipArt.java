package astroids;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import engine.render.HdrImage;
import engine.render.MeshData;

/**
 * The enemy ships' parts, turned on a lathe: a profile (radius and height pairs, from the bottom middle up to the top
 * middle) swept round the vertical axis. And the space around everything, as a sky image for the shiny hulls to
 * reflect.
 */
final class ShipArt {
  private ShipArt() {
  }

  /** The big enemy's hull: a deep saucer with an engine plate below, a raised rim and a deck for the dome. */
  static final float[][] SAUCER = { { 0f, -0.30f }, { 0.34f, -0.30f }, { 0.34f, -0.30f }, { 0.40f, -0.26f },
      { 0.62f, -0.20f }, { 0.86f, -0.11f }, { 1.0f, -0.04f }, { 1.0f, -0.04f }, { 1.06f, 0f }, { 1.0f, 0.04f },
      { 1.0f, 0.04f }, { 0.84f, 0.11f }, { 0.60f, 0.18f }, { 0.44f, 0.21f }, { 0.44f, 0.21f }, { 0.40f, 0.24f },
      { 0f, 0.24f } };
  /** The coloured band round the big enemy's rim. */
  static final float[][] RIM_BAND = { { 0.99f, -0.045f }, { 1.075f, -0.012f }, { 1.075f, -0.012f }, { 1.075f, 0.012f },
      { 1.075f, 0.012f }, { 0.99f, 0.045f } };
  /** A dome: half a squashed sphere, sitting on its rim at height 0. */
  static final float[][] DOME = dome(0.40f, 0.30f);
  /** The scout's hull: flatter, with a sharp edge. */
  static final float[][] SCOUT = { { 0f, -0.17f }, { 0.36f, -0.16f }, { 0.70f, -0.09f }, { 0.96f, -0.02f },
      { 1.06f, 0f }, { 1.06f, 0f }, { 0.95f, 0.04f }, { 0.68f, 0.10f }, { 0.40f, 0.15f }, { 0f, 0.16f } };
  /** A flat disc, facing down: the glowing engine plate under a hull. */
  static final float[][] PLATE = { { 0f, 0f }, { 1f, 0f } };

  private static float[][] dome(float radius, float height) {
    final int steps = 8;
    final float[][] p = new float[steps + 1][];
    for (int i = 0; i <= steps; i++) {
      final double a = Math.PI / 2 * i / steps;
      p[i] = new float[] { (float) (radius * Math.cos(a)), (float) (height * Math.sin(a)) };
    }
    return p;
  }

  /**
   * Sweeps a profile round the vertical axis. A point given twice in a row is a sharp edge (each side keeps its own
   * normal); elsewhere the shading is smooth. u runs round, v along the profile.
   */
  static MeshData lathe(float[][] profile, int segments) {
    final int n = profile.length;
    // the outward normal of each piece of the profile (zero for a repeated point)
    final float[][] pieceNormal = new float[n - 1][];
    final float[] along = new float[n];
    for (int k = 0; k + 1 < n; k++) {
      final float dr = profile[k + 1][0] - profile[k][0];
      final float dy = profile[k + 1][1] - profile[k][1];
      final float len = (float) Math.sqrt(dr * dr + dy * dy);
      pieceNormal[k] = len < 1e-6f ? null : new float[] { dy / len, -dr / len };
      along[k + 1] = along[k] + len;
    }
    final int cols = segments + 1;
    final float[] v = new float[n * cols * MeshData.INPUT_FLOATS_PER_VERTEX];
    int vi = 0;
    for (int i = 0; i < n; i++) {
      final float[] before = i > 0 && !same(profile[i], profile[i - 1]) ? pieceNormal[i - 1] : null;
      final float[] after = i + 1 < n && !same(profile[i], profile[i + 1]) ? pieceNormal[i] : null;
      float nr = 0f;
      float ny = 0f;
      for (float[] pn : new float[][] { before, after }) {
        if (pn != null) {
          nr += pn[0];
          ny += pn[1];
        }
      }
      final float nl = (float) Math.sqrt(nr * nr + ny * ny);
      nr /= Math.max(1e-6f, nl);
      ny /= Math.max(1e-6f, nl);
      for (int s = 0; s < cols; s++) {
        final double a = 2 * Math.PI * s / segments;
        final float c = (float) Math.cos(a);
        final float sn = (float) -Math.sin(a);
        v[vi++] = profile[i][0] * c;
        v[vi++] = profile[i][1];
        v[vi++] = profile[i][0] * sn;
        v[vi++] = nr * c;
        v[vi++] = ny;
        v[vi++] = nr * sn;
        v[vi++] = (float) s / segments;
        v[vi++] = along[i] / Math.max(1e-6f, along[n - 1]);
      }
    }
    final List<Integer> idx = new ArrayList<>();
    for (int i = 0; i + 1 < n; i++) {
      if (pieceNormal[i] == null) {
        continue; // the two copies of a sharp edge: nothing between them
      }
      for (int s = 0; s < segments; s++) {
        final int a = i * cols + s;
        final int b = a + cols;
        idx.addAll(List.of(a, a + 1, b + 1, a, b + 1, b));
      }
    }
    return MeshData.triangles(v, idx.stream().mapToInt(Integer::intValue).toArray());
  }

  private static boolean same(float[] a, float[] b) {
    return Math.abs(a[0] - b[0]) < 1e-6f && Math.abs(a[1] - b[1]) < 1e-6f;
  }

  // ----- the space around everything

  /**
   * Space as an equirectangular sky (longitude across, the top row straight up): nearly black, with a dim band of
   * nebula, a glow round the sun (in direction {@code toSun}) and the soft blue shine of a planet off to the side
   * ({@code toPlanet}), so that polished hulls always have something to reflect. The stars are left to the game's
   * star field, which is sharper. Linear light.
   */
  static HdrImage spaceSky(int width, int height, Vector3f toSun, Vector3f toPlanet, long seed) {
    final var rnd = new Random(seed);
    final float[] rgb = new float[width * height * 3];
    final var sun = new Vector3f(toSun).normalize();
    final var planet = new Vector3f(toPlanet).normalize();
    final var band = new Vector3f(0.3f, 1f, 0.45f).normalize(); // the nebula lies round the great circle across this
    final float[] cloud = new float[16 * 8]; // where the nebula is thicker (wraps round in longitude)
    for (int i = 0; i < cloud.length; i++) {
      cloud[i] = rnd.nextFloat();
    }
    final var d = new Vector3f();
    for (int y = 0; y < height; y++) {
      final double theta = Math.PI * (y + 0.5) / height;
      for (int x = 0; x < width; x++) {
        final double lon = 2 * Math.PI * ((x + 0.5) / width - 0.5);
        d.set((float) (Math.sin(theta) * Math.sin(lon)), (float) Math.cos(theta),
            (float) (-Math.sin(theta) * Math.cos(lon)));
        final int o = (y * width + x) * 3;
        // deep space, a touch of blue
        float r = 0.002f;
        float g = 0.0025f;
        float b = 0.005f;
        // the nebula: thickest along the band, patchy, purple to teal
        final float off = d.dot(band);
        final float thick = (float) Math.exp(-off * off / 0.04)
            * patch(cloud, 16, 8, (float) x / width * 16, (float) y / height * 8);
        final float mix = patch(cloud, 16, 8, (float) x / width * 16 + 7.3f, (float) y / height * 8 + 3.1f);
        r += thick * (0.025f * (1 - mix) + 0.006f * mix);
        g += thick * (0.006f * (1 - mix) + 0.02f * mix);
        b += thick * (0.035f * (1 - mix) + 0.025f * mix);
        // planet shine: broad and soft, blue
        final float shine = (float) Math.pow(Math.max(0f, d.dot(planet)), 3);
        r += 0.035f * shine;
        g += 0.055f * shine;
        b += 0.09f * shine;
        // the sun: a bright core and a wide soft halo
        final float toward = Math.max(0f, d.dot(sun));
        final float glow = 60f * (float) Math.pow(toward, 3000) + 0.6f * (float) Math.pow(toward, 40)
            + 0.05f * (float) Math.pow(toward, 6);
        r += glow;
        g += glow * 0.95f;
        b += glow * 0.85f;
        rgb[o] = r;
        rgb[o + 1] = g;
        rgb[o + 2] = b;
      }
    }
    return new HdrImage(width, height, rgb);
  }

  /** Smooth value noise on a grid that wraps round in x. */
  private static float patch(float[] grid, int cols, int rows, float x, float y) {
    final int x0 = (int) Math.floor(x);
    final int y0 = Math.min(rows - 1, (int) Math.floor(y));
    final int y1 = Math.min(rows - 1, y0 + 1);
    final float fx = smooth(x - x0);
    final float fy = smooth(Math.max(0f, Math.min(1f, y - y0)));
    final float a = grid[y0 * cols + Math.floorMod(x0, cols)];
    final float b = grid[y0 * cols + Math.floorMod(x0 + 1, cols)];
    final float c = grid[y1 * cols + Math.floorMod(x0, cols)];
    final float e = grid[y1 * cols + Math.floorMod(x0 + 1, cols)];
    return (a + (b - a) * fx) * (1 - fy) + (c + (e - c) * fx) * fy;
  }

  private static float smooth(float t) {
    return t * t * (3 - 2 * t);
  }
}
