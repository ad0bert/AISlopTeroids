package astroids;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import engine.render.HdrImage;
import engine.render.ImageData;
import engine.render.MeshData;
import engine.resources.NormalMaps;

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

  // ----- wear: the hulls are not new

  /** A hull's worn surface: colour (grime, streaks, scratches), roughness and metal (glTF layout), and bumps. */
  record Wear(ImageData albedo, ImageData metalRough, ImageData normals) {
  }

  /**
   * A used look for the hulls, for their lathe coordinates (u round the hull, v along its profile), tiling in both:
   * plates with grooves between them and rivets along the seams, patches and downward streaks of grime (darker and
   * duller), and fine scratches (brighter and shinier, as worn metal is).
   */
  static Wear wear(int size, long seed) {
    final var rnd = new Random(seed);
    final float[] height = new float[size * size];
    final float[] grime = new float[size * size];
    final float[] scratch = new float[size * size];
    java.util.Arrays.fill(height, 0.5f);
    // plates: two across each tile, seams at a few heights along the profile
    final int[] across = { 0, size / 2 };
    final int[] down = { (int) (size * 0.12f), (int) (size * 0.47f), (int) (size * 0.78f) };
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        float groove = 0f;
        for (int sx : across) {
          groove = Math.max(groove, seam(Math.abs(Math.floorMod(x - sx + size / 2, size) - size / 2)));
        }
        for (int sy : down) {
          groove = Math.max(groove, seam(Math.abs(y - sy)));
        }
        height[y * size + x] -= 0.35f * groove;
      }
    }
    // rivets along the seams
    for (int sx : across) {
      for (int y = 6; y < size; y += 12) {
        bump(height, size, sx + 5, y, 1.6f, 0.25f);
        bump(height, size, sx - 5, y, 1.6f, 0.25f);
      }
    }
    for (int sy : down) {
      for (int x = 6; x < size; x += 12) {
        bump(height, size, x, sy + 5, 1.6f, 0.25f);
      }
    }
    // grime: soft patches, and streaks running down the hull
    final float[] patches = noiseGrid(rnd, 8);
    final float[] streaks = noiseGrid(rnd, 12);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        final float patch = gridNoise(patches, 8, (float) x * 8 / size, (float) y * 8 / size);
        final float streak = gridNoise(streaks, 12, (float) x * 12 / size, (float) y * 2 / size);
        grime[y * size + x] = Math.max(0f, Math.min(1f, (patch - 0.5f) * 1.4f + (streak - 0.65f) * 0.6f));
      }
    }
    // scratches: short thin lines
    for (int i = 0; i < 70; i++) {
      float x = rnd.nextFloat() * size;
      float y = rnd.nextFloat() * size;
      final double a = 2 * Math.PI * rnd.nextFloat();
      final int length = 6 + rnd.nextInt(30);
      for (int k = 0; k < length; k++) {
        final int o = Math.floorMod((int) y, size) * size + Math.floorMod((int) x, size);
        scratch[o] = 1f;
        height[o] -= 0.06f;
        x += (float) Math.cos(a);
        y += (float) Math.sin(a);
      }
    }
    final byte[] albedo = new byte[size * size * 4];
    final byte[] metalRough = new byte[size * size * 4];
    for (int i = 0; i < size * size; i++) {
      final float dirt = grime[i];
      final float shine = scratch[i];
      final float groove = Math.max(0f, 0.5f - height[i]) * 2f; // dirt gathers in the grooves too
      final float light = (1f - 0.3f * dirt - 0.3f * groove) * (1f + 0.2f * shine);
      albedo[i * 4] = toByte(light * (1f - 0.05f * dirt));
      albedo[i * 4 + 1] = toByte(light * (1f - 0.08f * dirt));
      albedo[i * 4 + 2] = toByte(light * (1f - 0.14f * dirt));
      albedo[i * 4 + 3] = (byte) 255;
      // roughness (green) scales the material's: dull where dirty, polished where scratched
      final float rough = 0.55f + 0.45f * dirt + 0.3f * groove - 0.35f * shine;
      metalRough[i * 4] = (byte) 255;
      metalRough[i * 4 + 1] = toByte(rough);
      metalRough[i * 4 + 2] = toByte(1f - 0.4f * dirt); // grime is not metal
      metalRough[i * 4 + 3] = (byte) 255;
    }
    return new Wear(new ImageData(size, size, albedo), new ImageData(size, size, metalRough),
        NormalMaps.fromHeights(height, size, size, 3f));
  }

  /** How deep a groove is this many texels from its middle. */
  private static float seam(int distance) {
    return distance == 0 ? 1f : distance == 1 ? 0.6f : distance == 2 ? 0.15f : 0f;
  }

  private static void bump(float[] height, int size, int cx, int cy, float radius, float amount) {
    final int reach = (int) Math.ceil(radius * 2);
    for (int dy = -reach; dy <= reach; dy++) {
      for (int dx = -reach; dx <= reach; dx++) {
        final float t = (float) Math.sqrt(dx * dx + dy * dy) / radius;
        if (t < 1.5f) {
          height[Math.floorMod(cy + dy, size) * size + Math.floorMod(cx + dx, size)] += amount
              * (float) Math.exp(-t * t * 2);
        }
      }
    }
  }

  private static float[] noiseGrid(Random rnd, int cells) {
    final float[] g = new float[cells * cells];
    for (int i = 0; i < g.length; i++) {
      g[i] = rnd.nextFloat();
    }
    return g;
  }

  /** Smooth value noise on a grid that wraps round in both directions. */
  private static float gridNoise(float[] grid, int cells, float x, float y) {
    final int x0 = (int) Math.floor(x);
    final int y0 = (int) Math.floor(y);
    final float fx = smooth(x - x0);
    final float fy = smooth(y - y0);
    final float a = grid[Math.floorMod(y0, cells) * cells + Math.floorMod(x0, cells)];
    final float b = grid[Math.floorMod(y0, cells) * cells + Math.floorMod(x0 + 1, cells)];
    final float c = grid[Math.floorMod(y0 + 1, cells) * cells + Math.floorMod(x0, cells)];
    final float d = grid[Math.floorMod(y0 + 1, cells) * cells + Math.floorMod(x0 + 1, cells)];
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
  }

  private static byte toByte(float v) {
    return (byte) Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
  }

  // ----- wreckage

  /**
   * A torn-off piece of hull, about radius 1: an irregular plate with jagged edges, a little thickness, and bent, as a
   * curved hull panel would be. Flat faces, so its edges catch the light.
   */
  static MeshData shard(long seed) {
    final var rnd = new Random(seed);
    final int corners = 5 + rnd.nextInt(4);
    final float[][] rim = new float[corners][];
    for (int i = 0; i < corners; i++) {
      final double a = 2 * Math.PI * (i + 0.6 * rnd.nextFloat()) / corners;
      final float r = 0.45f + 0.55f * rnd.nextFloat();
      rim[i] = new float[] { (float) Math.cos(a) * r, (float) Math.sin(a) * r * (0.5f + 0.5f * rnd.nextFloat()) };
    }
    final float bend = 0.1f + 0.25f * rnd.nextFloat();
    final float thick = 0.05f;
    final var top = new Vector3f[corners];
    final var bottom = new Vector3f[corners];
    for (int i = 0; i < corners; i++) {
      final float x = rim[i][0];
      final float z = rim[i][1];
      final float y = bend * x * x;
      top[i] = new Vector3f(x, y + thick, z);
      bottom[i] = new Vector3f(x, y - thick, z);
    }
    final var centreTop = new Vector3f(0f, thick, 0f);
    final var centreBottom = new Vector3f(0f, -thick, 0f);
    final List<Vector3f> tris = new ArrayList<>();
    for (int i = 0; i < corners; i++) {
      final int j = (i + 1) % corners;
      tris.addAll(List.of(centreTop, top[j], top[i])); // up
      tris.addAll(List.of(centreBottom, bottom[i], bottom[j])); // down
      tris.addAll(List.of(top[i], top[j], bottom[j], top[i], bottom[j], bottom[i])); // the torn edge
    }
    final float[] v = new float[tris.size() * MeshData.INPUT_FLOATS_PER_VERTEX];
    final int[] idx = new int[tris.size()];
    final var e1 = new Vector3f();
    final var e2 = new Vector3f();
    for (int t = 0; t < tris.size(); t += 3) {
      final var a = tris.get(t);
      tris.get(t + 1).sub(a, e1);
      tris.get(t + 2).sub(a, e2);
      final var n = e1.cross(e2, new Vector3f());
      if (n.lengthSquared() < 1e-12f) {
        n.set(0f, 1f, 0f);
      }
      n.normalize();
      for (int k = 0; k < 3; k++) {
        final var p = tris.get(t + k);
        final int o = (t + k) * MeshData.INPUT_FLOATS_PER_VERTEX;
        v[o] = p.x;
        v[o + 1] = p.y;
        v[o + 2] = p.z;
        v[o + 3] = n.x;
        v[o + 4] = n.y;
        v[o + 5] = n.z;
        v[o + 6] = 0.25f + 0.25f * p.x; // a patch of the worn hull surface
        v[o + 7] = 0.25f + 0.25f * p.z;
        idx[t + k] = t + k;
      }
    }
    return MeshData.triangles(v, idx);
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
