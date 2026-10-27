package astroids;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import astroids.model.Asteroid;
import astroids.model.World;
import engine.render.UiCanvas;

/**
 * A round radar in the corner of the screen: what is around the ship, seen from above with the view direction pointing
 * up. A line from each blip shows whether the thing is above (line up) or below (line down) the ship's plane. Covers the
 * whole arena, whatever its size (the farthest point of the wrap-around cube is {@code sqrt(3)} half-sizes away).
 */
final class Radar {
  /** How far the radar reaches, in arena half-sizes: the farthest point of the cube is sqrt(3) of them away. */
  static final float RANGE_IN_ARENAS = 1.75f;

  /** Where something appears on the radar: x to the right, y ahead, height up; each -1..1 of the radar's radius. */
  record Blip(float x, float y, float height) {
  }

  private Radar() {
  }

  /** Places something {@code delta} away from the ship (shortest way through the arena) as seen with {@code view}. */
  static Blip project(Vector3f delta, Quaternionf view, float range) {
    final var local = view.conjugate(new Quaternionf()).transform(new Vector3f(delta)).div(range);
    float x = local.x;
    float y = -local.z; // the camera looks along -Z
    final float flat = (float) Math.sqrt(x * x + y * y);
    if (flat > 1f) { // beyond the range: on the rim, in its direction
      x /= flat;
      y /= flat;
    }
    return new Blip(x, y, Math.max(-1f, Math.min(1f, local.y)));
  }

  static void draw(UiCanvas ui, int width, int height, World world, Vector3f ship, Quaternionf view) {
    final float r = Math.min(width, height) * 0.13f;
    final float cx = width - r - 22f;
    final float cy = height - r - 22f;
    // backdrop, rim, the ship's place and its heading
    ui.drawRect(cx - r - 6f, cy - r - 6f, 2f * r + 12f, 2f * r + 12f, 0f, 0.02f, 0.05f, 0.45f);
    for (int i = 0; i < 64; i++) {
      final double a = i * Math.PI * 2 / 64;
      ui.drawRect(cx + (float) Math.cos(a) * r - 1f, cy + (float) Math.sin(a) * r - 1f, 2f, 2f, 0.4f, 0.8f, 0.6f, 0.6f);
    }
    ui.drawRect(cx - r, cy - 0.5f, 2f * r, 1f, 0.4f, 0.8f, 0.6f, 0.2f);
    ui.drawRect(cx - 0.5f, cy - r, 1f, 2f * r, 0.4f, 0.8f, 0.6f, 0.2f);
    ui.drawRect(cx - 3f, cy - 3f, 6f, 6f, 0.6f, 1f, 0.8f, 1f);
    ui.drawRect(cx - 0.5f, cy - 12f, 1f, 9f, 0.6f, 1f, 0.8f, 0.8f);

    final var delta = new Vector3f();
    final float range = world.arena() * RANGE_IN_ARENAS;
    for (Asteroid a : world.asteroids()) {
      final var b = project(world.delta(ship, a.position, delta), view, range);
      final boolean big = a.size == Asteroid.Size.BIG;
      blip(ui, cx, cy, r, b, big ? 4f : 3f, 0.8f, 0.78f, 0.72f);
    }
    if (world.boss() != null) {
      blip(ui, cx, cy, r, project(world.delta(ship, world.boss().position, delta), view, range), 5f, 1f, 0.3f, 0.25f);
    }
  }

  private static void blip(UiCanvas ui, float cx, float cy, float r, Blip b, float size, float red, float green,
      float blue) {
    final float x = cx + b.x() * r;
    final float y = cy - b.y() * r;
    final float stalk = -b.height() * r * 0.5f; // screen y grows downwards
    ui.drawRect(x - 1f, Math.min(y, y + stalk), 1.5f, Math.abs(stalk), red, green, blue, 0.6f);
    ui.drawRect(x - size / 2f, y + stalk - size / 2f, size, size, red, green, blue, 1f);
    ui.drawRect(x - 1.5f, y - 0.5f, 3f, 1f, red, green, blue, 0.5f); // its foot on the ship's plane
  }
}
