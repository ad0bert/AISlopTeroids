package aislopteroids;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import engine.render.MeshData;

/**
 * The player's cockpit, in camera space (the camera looks along -z): a bubble of glass over the pilot, the arch of its
 * frame across the view, and a low dashboard that follows the canopy's sill round the bottom of the view.
 *
 * <p>
 * The canopy is the top half of an ellipsoid ({@link #CENTER}, {@link #RADII}) whose centre is below and behind the
 * pilot, so the glass is seen at a slant towards its edges and reflects more there.
 */
final class CockpitArt {
  /** The middle of the canopy's ellipsoid; its sill (where the glass meets the dashboard) is at this height. */
  static final Vector3f CENTER = new Vector3f(0f, -0.72f, 0.35f);
  static final Vector3f RADII = new Vector3f(1.35f, 1.45f, 1.75f);
  /** Where the arch of the frame is: this far ahead of the pilot, close enough that only its sides cross the view. */
  static final float ARCH_Z = -0.52f;
  /** How far round from straight ahead the dashboard reaches on either side, in degrees. */
  static final float DASH_REACH = 105f;
  /** How much of the way from the canopy's axis to the sill the dashboard's inner edge is. */
  static final float DASH_INNER = 0.74f;
  /** The dashboard's top drops by this much from the sill to its inner edge. */
  static final float DASH_DROP = 0.03f;

  private CockpitArt() {
  }

  /**
   * The point on the canopy's sill {@code degrees} round from straight ahead (positive to the right), raised by
   * {@code lift}: where the dashboard meets the glass.
   */
  static Vector3f sill(float degrees, float lift) {
    final double a = Math.toRadians(degrees);
    return new Vector3f((float) (RADII.x * Math.sin(a)), lift, (float) (-RADII.z * Math.cos(a))).add(CENTER.x, CENTER.y,
        CENTER.z);
  }

  /** The glass: the top half of the canopy's ellipsoid, its normals towards the pilot. */
  static MeshData canopy(int segments, int rings) {
    return grid(segments, rings, (u, v, p, n) -> {
      final double around = 2 * Math.PI * u;
      final double up = Math.PI / 2 * v;
      final float x = (float) (Math.sin(around) * Math.cos(up));
      final float y = (float) Math.sin(up);
      final float z = (float) (-Math.cos(around) * Math.cos(up));
      p.set(x * RADII.x, y * RADII.y, z * RADII.z).add(CENTER);
      n.set(-x / RADII.x, -y / RADII.y, -z / RADII.z).normalize(); // inwards
    });
  }

  /**
   * The arch of the frame: where the canopy's ellipsoid crosses the plane {@link #ARCH_Z}, from the sill on one side
   * over the top to the other, pulled in by {@code inset} towards its middle.
   */
  static List<Vector3f> arch(int points, float inset) {
    final float dz = (ARCH_Z - CENTER.z) / RADII.z;
    final float s = (float) Math.sqrt(1 - dz * dz);
    final List<Vector3f> path = new ArrayList<>();
    for (int i = 0; i <= points; i++) {
      final double t = Math.PI * i / points;
      final float x = (float) Math.cos(t) * RADII.x * s;
      final float y = (float) Math.sin(t) * RADII.y * s;
      final var p = new Vector3f(x, y, 0f);
      p.mul(1f - inset / p.length());
      path.add(p.add(CENTER.x, CENTER.y, ARCH_Z));
    }
    return path;
  }

  /** Along the sill, from one end of the dashboard to the other, raised by {@code lift} and pulled in by {@code in}. */
  static List<Vector3f> sillLine(int points, float lift, float in) {
    final List<Vector3f> path = new ArrayList<>();
    for (int i = 0; i <= points; i++) {
      final float deg = -DASH_REACH + 2 * DASH_REACH * i / points;
      final var p = sill(deg, lift);
      final var axis = new Vector3f(CENTER.x, p.y, CENTER.z);
      path.add(p.sub(axis).mul(1f - in).add(axis));
    }
    return path;
  }

  /**
   * The dashboard: a curved shelf from the sill in towards the pilot, dropping a little, and its face from the inner
   * edge down out of sight.
   */
  static MeshData dashboard(int segments) {
    final float bottom = -1.6f;
    // across the shelf, then down the face: the profile from the sill (0) to the bottom of the face (1)
    final float[][] profile = { { 1f, 0f }, { DASH_INNER, -DASH_DROP }, { DASH_INNER, -DASH_DROP },
        { DASH_INNER - 0.02f, bottom - CENTER.y } };
    final int n = profile.length;
    final float[] v = new float[(segments + 1) * n * MeshData.INPUT_FLOATS_PER_VERTEX];
    int vi = 0;
    for (int s = 0; s <= segments; s++) {
      final double a = Math.toRadians(-DASH_REACH + 2 * DASH_REACH * s / segments);
      final float ox = (float) (RADII.x * Math.sin(a));
      final float oz = (float) (-RADII.z * Math.cos(a));
      for (int k = 0; k < n; k++) {
        final boolean face = k >= 2;
        final float x = ox * profile[k][0] + CENTER.x;
        final float y = profile[k][1] + CENTER.y;
        final float z = oz * profile[k][0] + CENTER.z;
        // the shelf faces up; the face looks back at the pilot (towards the canopy's axis)
        final var normal = face ? new Vector3f(-ox, 0f, -oz).normalize() : new Vector3f(0f, 1f, 0f);
        vi = put(v, vi, x, y, z, normal, (float) s / segments, (float) k / (n - 1));
      }
    }
    final List<Integer> idx = new ArrayList<>();
    for (int s = 0; s < segments; s++) {
      for (int k = 0; k + 1 < n; k++) {
        if (k == 1) {
          continue; // the sharp edge between shelf and face: two copies of the same points
        }
        quad(idx, v, s * n + k, s * n + k + 1, (s + 1) * n + k + 1, (s + 1) * n + k);
      }
    }
    return MeshData.triangles(v, idx.stream().mapToInt(Integer::intValue).toArray());
  }

  /** A round tube along a path, {@code radius} thick, for the frame and the glowing lines along it. */
  static MeshData tube(List<Vector3f> path, float radius, int sides) {
    final int m = path.size();
    final float[] v = new float[m * (sides + 1) * MeshData.INPUT_FLOATS_PER_VERTEX];
    int vi = 0;
    final var side = new Vector3f();
    final var up = new Vector3f();
    final var normal = new Vector3f();
    for (int i = 0; i < m; i++) {
      final var along = new Vector3f(path.get(Math.min(m - 1, i + 1))).sub(path.get(Math.max(0, i - 1))).normalize();
      // a frame round the path that does not twist: start from whichever axis is least along it
      final var ref = Math.abs(along.z) < 0.9f ? new Vector3f(0f, 0f, 1f) : new Vector3f(0f, 1f, 0f);
      along.cross(ref, side).normalize();
      side.cross(along, up).normalize();
      for (int k = 0; k <= sides; k++) {
        final double a = 2 * Math.PI * k / sides;
        normal.set(side).mul((float) Math.cos(a)).fma((float) Math.sin(a), up);
        final var p = path.get(i);
        vi = put(v, vi, p.x + normal.x * radius, p.y + normal.y * radius, p.z + normal.z * radius, normal,
            (float) k / sides, (float) i / (m - 1));
      }
    }
    final List<Integer> idx = new ArrayList<>();
    final int cols = sides + 1;
    for (int i = 0; i + 1 < m; i++) {
      for (int k = 0; k < sides; k++) {
        quad(idx, v, i * cols + k, i * cols + k + 1, (i + 1) * cols + k + 1, (i + 1) * cols + k);
      }
    }
    return MeshData.triangles(v, idx.stream().mapToInt(Integer::intValue).toArray());
  }

  interface Surface {
    void at(double u, double v, Vector3f position, Vector3f normal);
  }

  private static MeshData grid(int us, int vs, Surface surface) {
    final float[] v = new float[(us + 1) * (vs + 1) * MeshData.INPUT_FLOATS_PER_VERTEX];
    int vi = 0;
    final var p = new Vector3f();
    final var n = new Vector3f();
    for (int j = 0; j <= vs; j++) {
      for (int i = 0; i <= us; i++) {
        surface.at((double) i / us, (double) j / vs, p, n);
        vi = put(v, vi, p.x, p.y, p.z, n, (float) i / us, (float) j / vs);
      }
    }
    final List<Integer> idx = new ArrayList<>();
    for (int j = 0; j < vs; j++) {
      for (int i = 0; i < us; i++) {
        final int a = j * (us + 1) + i;
        quad(idx, v, a, a + 1, a + us + 2, a + us + 1);
      }
    }
    return MeshData.triangles(v, idx.stream().mapToInt(Integer::intValue).toArray());
  }

  private static int put(float[] v, int vi, float x, float y, float z, Vector3f n, float tu, float tv) {
    v[vi++] = x;
    v[vi++] = y;
    v[vi++] = z;
    v[vi++] = n.x;
    v[vi++] = n.y;
    v[vi++] = n.z;
    v[vi++] = tu;
    v[vi++] = tv;
    return vi;
  }

  /**
   * Two triangles for the quad a-b-c-d, wound so that they face the way the vertex normals point (the renderer turns
   * the normal of a triangle seen from behind round).
   */
  private static void quad(List<Integer> idx, float[] v, int a, int b, int c, int d) {
    triangle(idx, v, a, b, c);
    triangle(idx, v, a, c, d);
  }

  private static void triangle(List<Integer> idx, float[] v, int a, int b, int c) {
    final var pa = vertex(v, a, 0);
    final var face = vertex(v, b, 0).sub(pa).cross(vertex(v, c, 0).sub(pa));
    if (face.lengthSquared() < 1e-14f) {
      return; // collapsed (the top of the canopy, where the rings meet)
    }
    final var normal = vertex(v, a, 3).add(vertex(v, b, 3)).add(vertex(v, c, 3));
    if (face.dot(normal) >= 0f) {
      idx.addAll(List.of(a, b, c));
    } else {
      idx.addAll(List.of(a, c, b));
    }
  }

  private static Vector3f vertex(float[] v, int index, int offset) {
    final int i = index * MeshData.INPUT_FLOATS_PER_VERTEX + offset;
    return new Vector3f(v[i], v[i + 1], v[i + 2]);
  }
}
