package astroids;

import java.util.Random;

import org.joml.Vector3f;

import engine.render.ImageData;
import engine.render.MeshData;
import engine.resources.NormalMaps;

/**
 * The asteroids' looks, generated from seeds so the repository needs no art: lumpy, cratered meshes (each seed a
 * different shape), and a seamless rock surface as a colour texture and a bump (normal) map made from the same heights.
 */
final class RockArt {
  /** Around the rock and from pole to pole; the texture repeats twice around and once from pole to pole. */
  static final int SEGMENTS = 28;
  static final int RINGS = 16;
  static final int TEXTURE_SIZE = 256;
  /** How far a rock's surface may stand out from (or sink below) its average radius, as a fraction of it. */
  static final double MAX_BULGE = 0.25;

  private RockArt() {
  }

  // ----- shapes

  /**
   * A rock of average radius 1: a sphere pushed in and out by a few broad lumps and ridges, with some craters. The
   * displacement depends only on the direction, so the sphere's seam and poles stay closed.
   */
  static MeshData mesh(long seed) {
    final var shape = new Shape(seed);
    final int cols = SEGMENTS + 1; // the seam is doubled so u can run from 0 to 1
    final int count = cols * (RINGS + 1);
    final float[] radius = new float[count];
    final Vector3f[] dir = new Vector3f[count];
    double sum = 0;
    for (int r = 0; r <= RINGS; r++) {
      final double phi = Math.PI * r / RINGS;
      for (int s = 0; s < cols; s++) {
        final double theta = 2 * Math.PI * s / SEGMENTS;
        final var d = new Vector3f((float) (Math.cos(theta) * Math.sin(phi)), (float) -Math.cos(phi),
            (float) (-Math.sin(theta) * Math.sin(phi)));
        final int i = r * cols + s;
        dir[i] = d;
        radius[i] = shape.radius(d);
        sum += radius[i];
      }
    }
    // an average radius of 1, so the rock matches its collision size, with its highs and lows eased to within about
    // a quarter of that: a shot or the ship must not pass through a lump that sticks far out
    double eased = 0;
    for (int i = 0; i < count; i++) {
      final double off = radius[i] * count / sum - 1;
      radius[i] = (float) (1 + MAX_BULGE * Math.tanh(off / MAX_BULGE));
      eased += radius[i];
    }
    final float scale = (float) (count / eased);
    final Vector3f[] pos = new Vector3f[count];
    for (int i = 0; i < count; i++) {
      pos[i] = new Vector3f(dir[i]).mul(radius[i] * scale);
    }

    final int[] idx = new int[SEGMENTS * RINGS * 6];
    int ii = 0;
    for (int r = 0; r < RINGS; r++) {
      for (int s = 0; s < SEGMENTS; s++) {
        final int a = r * cols + s;
        final int b = a + cols;
        idx[ii++] = a;
        idx[ii++] = a + 1;
        idx[ii++] = b + 1;
        idx[ii++] = a;
        idx[ii++] = b + 1;
        idx[ii++] = b;
      }
    }
    // smooth normals: the triangles' normals summed at each point; the seam's and the poles' copies share theirs
    final Vector3f[] normal = new Vector3f[count];
    for (int i = 0; i < count; i++) {
      normal[i] = new Vector3f();
    }
    final var e1 = new Vector3f();
    final var e2 = new Vector3f();
    for (int t = 0; t < idx.length; t += 3) {
      final var p0 = pos[idx[t]];
      pos[idx[t + 1]].sub(p0, e1);
      pos[idx[t + 2]].sub(p0, e2);
      final var n = e1.cross(e2); // its length weights it by the triangle's area
      for (int k = 0; k < 3; k++) {
        normal[same(idx[t + k], cols)].add(n);
      }
    }
    final float[] v = new float[count * MeshData.INPUT_FLOATS_PER_VERTEX];
    int vi = 0;
    for (int r = 0; r <= RINGS; r++) {
      for (int s = 0; s < cols; s++) {
        final int i = r * cols + s;
        final var p = pos[i];
        final var n = new Vector3f(normal[same(i, cols)]);
        if (n.lengthSquared() < 1e-12f) {
          n.set(dir[i]);
        }
        n.normalize();
        if (n.dot(dir[i]) < 0f) {
          n.negate(); // the triangles wind the other way round: normals point outwards
        }
        v[vi++] = p.x;
        v[vi++] = p.y;
        v[vi++] = p.z;
        v[vi++] = n.x;
        v[vi++] = n.y;
        v[vi++] = n.z;
        v[vi++] = (float) s / SEGMENTS;
        v[vi++] = (float) r / RINGS;
      }
    }
    return MeshData.triangles(v, idx);
  }

  /** The point that vertex {@code i} stands for: the seam's copies are one point, and so is each pole's ring. */
  private static int same(int i, int cols) {
    final int r = i / cols;
    final int s = i % cols;
    if (r == 0) {
      return 0;
    }
    if (r == RINGS) {
      return RINGS * cols;
    }
    return r * cols + (s == SEGMENTS ? 0 : s);
  }

  /** The random parts of one rock's shape: broad lumps and ridges, and craters with a raised rim. */
  static final class Shape {
    private final Vector3f[] lumpAxis;
    private final float[] lumpFreq;
    private final float[] lumpPhase;
    private final float[] lumpHeight;
    private final Vector3f[] craterAt;
    private final float[] craterSize;
    private final float[] craterDepth;

    Shape(long seed) {
      final var rnd = new Random(seed);
      final int lumps = 5 + rnd.nextInt(3);
      this.lumpAxis = new Vector3f[lumps];
      this.lumpFreq = new float[lumps];
      this.lumpPhase = new float[lumps];
      this.lumpHeight = new float[lumps];
      for (int i = 0; i < lumps; i++) {
        this.lumpAxis[i] = randomDirection(rnd);
        this.lumpFreq[i] = 1.2f + 3f * rnd.nextFloat();
        this.lumpPhase[i] = (float) (2 * Math.PI * rnd.nextFloat());
        this.lumpHeight[i] = (0.04f + 0.07f * rnd.nextFloat()) / (0.6f + 0.25f * this.lumpFreq[i]); // finer, lower
      }
      final int craters = 2 + rnd.nextInt(5);
      this.craterAt = new Vector3f[craters];
      this.craterSize = new float[craters];
      this.craterDepth = new float[craters];
      for (int i = 0; i < craters; i++) {
        this.craterAt[i] = randomDirection(rnd);
        this.craterSize[i] = 0.25f + 0.35f * rnd.nextFloat(); // radians
        this.craterDepth[i] = 0.05f + 0.08f * rnd.nextFloat();
      }
    }

    /** The rock's radius (around 1) in direction {@code d} (a unit vector). */
    float radius(Vector3f d) {
      double r = 1;
      for (int i = 0; i < this.lumpAxis.length; i++) {
        r += this.lumpHeight[i] * Math.sin(this.lumpFreq[i] * d.dot(this.lumpAxis[i]) * Math.PI + this.lumpPhase[i]);
      }
      for (int i = 0; i < this.craterAt.length; i++) {
        final double angle = Math.acos(Math.max(-1, Math.min(1, d.dot(this.craterAt[i]))));
        final double t = angle / this.craterSize[i];
        if (t < 1) {
          r -= this.craterDepth[i] * (1 - t * t); // a bowl
        }
        r += 0.35 * this.craterDepth[i] * Math.exp(-Math.pow((t - 1) / 0.25, 2)); // its rim
      }
      return (float) r;
    }
  }

  private static Vector3f randomDirection(Random rnd) {
    final float z = rnd.nextFloat() * 2f - 1f;
    final double a = 2 * Math.PI * rnd.nextFloat();
    final float r = (float) Math.sqrt(1f - z * z);
    return new Vector3f(r * (float) Math.cos(a), r * (float) Math.sin(a), z);
  }

  // ----- surface

  /**
   * A seamless height field for the rock surface, about 0..1: rough noise at several scales with small craters
   * pressed into it. Rows and columns wrap round, so the texture tiles without seams.
   */
  static float[] heights(int size, long seed) {
    final var rnd = new Random(seed);
    final float[] h = new float[size * size];
    float amplitude = 0.5f;
    for (int cells = 4; cells <= size / 4; cells *= 2) { // octaves
      final float[] grid = new float[cells * cells];
      for (int i = 0; i < grid.length; i++) {
        grid[i] = rnd.nextFloat();
      }
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          h[y * size + x] += amplitude * smoothNoise(grid, cells, (float) x * cells / size, (float) y * cells / size);
        }
      }
      amplitude *= 0.5f;
    }
    // small craters: a bowl with a raised rim, wrapping round the edges
    final int craters = 40;
    for (int c = 0; c < craters; c++) {
      final float cx = rnd.nextFloat() * size;
      final float cy = rnd.nextFloat() * size;
      final float r = size * (0.015f + 0.05f * rnd.nextFloat() * rnd.nextFloat());
      final float depth = 0.25f + 0.25f * rnd.nextFloat();
      final int reach = (int) Math.ceil(r * 1.6f);
      for (int dy = -reach; dy <= reach; dy++) {
        for (int dx = -reach; dx <= reach; dx++) {
          final float t = (float) Math.sqrt(dx * dx + dy * dy) / r;
          final int x = Math.floorMod((int) cx + dx, size);
          final int y = Math.floorMod((int) cy + dy, size);
          float change = 0.3f * depth * (float) Math.exp(-Math.pow((t - 1) / 0.25, 2)); // the rim
          if (t < 1) {
            change -= depth * (1 - t * t);
          }
          h[y * size + x] += change;
        }
      }
    }
    // stretch to 0..1
    float min = Float.MAX_VALUE;
    float max = -Float.MAX_VALUE;
    for (float v : h) {
      min = Math.min(min, v);
      max = Math.max(max, v);
    }
    for (int i = 0; i < h.length; i++) {
      h[i] = (h[i] - min) / Math.max(1e-6f, max - min);
    }
    return h;
  }

  /** Value noise on a wrapping grid, smoothly interpolated. */
  private static float smoothNoise(float[] grid, int cells, float x, float y) {
    final int x0 = (int) Math.floor(x);
    final int y0 = (int) Math.floor(y);
    final float fx = smooth(x - x0);
    final float fy = smooth(y - y0);
    final float a = grid[Math.floorMod(y0, cells) * cells + Math.floorMod(x0, cells)];
    final float b = grid[Math.floorMod(y0, cells) * cells + Math.floorMod(x0 + 1, cells)];
    final float c = grid[Math.floorMod(y0 + 1, cells) * cells + Math.floorMod(x0, cells)];
    final float d = grid[Math.floorMod(y0 + 1, cells) * cells + Math.floorMod(x0 + 1, cells)];
    return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
  }

  private static float smooth(float t) {
    return t * t * (3 - 2 * t);
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  /**
   * The rock's colours from its heights: grey and brown patches, darker in hollows and crater floors, a few light
   * specks. Fairly light, so that the material's colour can tint it.
   */
  static ImageData albedo(float[] heights, int size, long seed) {
    final var rnd = new Random(seed);
    final float[] tone = new float[8 * 8]; // where it is more brown and where more grey
    for (int i = 0; i < tone.length; i++) {
      tone[i] = rnd.nextFloat();
    }
    final byte[] rgba = new byte[size * size * 4];
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        final int i = y * size + x;
        final float h = heights[i];
        final float brown = smoothNoise(tone, 8, (float) x * 8 / size, (float) y * 8 / size);
        float light = 0.55f + 0.45f * h; // hollows darker
        if (rnd.nextFloat() < 0.004f) {
          light = Math.min(1.1f, light + 0.35f); // a glinting speck
        }
        final float r = light * (0.80f + 0.12f * brown);
        final float g = light * (0.78f + 0.05f * brown);
        final float b = light * (0.76f - 0.06f * brown);
        rgba[i * 4] = toByte(r);
        rgba[i * 4 + 1] = toByte(g);
        rgba[i * 4 + 2] = toByte(b);
        rgba[i * 4 + 3] = (byte) 255;
      }
    }
    return new ImageData(size, size, rgba);
  }

  /** The bump map: the heights as a tangent-space normal map. */
  static ImageData normals(float[] heights, int size) {
    return NormalMaps.fromHeights(heights, size, size, 6f);
  }

  private static byte toByte(float v) {
    return (byte) Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
  }
}
