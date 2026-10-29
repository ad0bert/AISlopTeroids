package aislopteroids;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import engine.ecs.Entity;
import engine.ecs.Scene;
import engine.render.GpuMesh;
import engine.render.Material;
import engine.render.MeshRenderer;

/**
 * The readouts projected on the cockpit's glass, made of light rather than text: the speed in seven-segment digits
 * over a bar to the left of the middle, the hyperspace charge as a bar of cells to the right with the gun's battery
 * under it, and a dashed ring round
 * the crosshair. Every segment and cell has a faint ghost that is always there, as on a real display.
 *
 * <p>
 * All of it hangs off the cockpit (camera space), just inside the glass, the readouts bent along the curve of the
 * dashboard below them, every light turned to face the pilot.
 */
final class GlassDisplay {
  /** How high a digit is, and how far apart the digits are. */
  static final float DIGIT_HEIGHT = 0.08f;
  static final float DIGIT_WIDTH = DIGIT_HEIGHT * 0.55f;
  static final float DIGIT_STEP = DIGIT_WIDTH * 1.45f;
  /** How thick a segment is. */
  static final float STROKE = DIGIT_HEIGHT * 0.11f;
  static final int SPEED_DIGITS = 3;
  static final int SPEED_CELLS = 10;
  static final int JUMP_CELLS = 8;
  static final int BATTERY_CELLS = 10;
  /** How far round from straight ahead the two readouts are, in degrees, and how high above the sill. */
  static final float READOUT_DEGREES = 22f;
  static final float READOUT_LIFT = 0.13f;
  /** The ring round the crosshair: its distance ahead and radius, and how many dashes. */
  static final float RING_DISTANCE = 1.0f;
  static final float RING_RADIUS = 0.045f;
  static final int RING_DASHES = 16;

  /**
   * Which segments each digit lights, as bits: a (top), b (top right), c (bottom right), d (bottom), e (bottom left),
   * f (top left), g (middle).
   */
  static final int[] DIGITS = { 0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110, 0b1101101, 0b1111101,
      0b0000111, 0b1111111, 0b1101111 };

  private final List<Entity[]> digits = new ArrayList<>(); // per digit: its seven lit segments
  private final List<Entity> speedCells = new ArrayList<>();
  private final List<Entity> jumpCells = new ArrayList<>();
  private final List<Entity> batteryCells = new ArrayList<>();
  /** Lit segments and cells keep their size here while they are switched off (scale 0). */
  private final Map<Entity, Vector3f> sizes = new IdentityHashMap<>();
  private final Material jumpLight;
  private final Vector3f jumpColor = new Vector3f();
  private final Material batteryLight;
  private final Vector3f batteryColor = new Vector3f();

  /**
   * Builds the displays under {@code cockpit}, from {@code cube} (a cube of half-size 1), {@code on} for what is lit
   * and {@code ghost} for the faint unlit segments. {@code jump} is the hyperspace bar's own light, which pulses when
   * the jump is ready.
   */
  GlassDisplay(Scene scene, Entity cockpit, GpuMesh cube, Material on, Material ghost, Material jump,
      Material battery) {
    this.batteryLight = battery;
    this.batteryColor.set(battery.emissive);
    this.jumpLight = jump;
    this.jumpColor.set(jump.emissive);
    final var left = new Panel(cockpit, -READOUT_DEGREES);
    final var right = new Panel(cockpit, READOUT_DEGREES);
    // the speed: digits right-aligned over a bar
    for (int i = 0; i < SPEED_DIGITS; i++) {
      final float x = (i - (SPEED_DIGITS - 1) / 2f) * DIGIT_STEP;
      final var segments = new Entity[7];
      for (int s = 0; s < 7; s++) {
        final var at = segment(s);
        final boolean across = s == 0 || s == 3 || s == 6;
        final var size = across ? new Vector3f(DIGIT_WIDTH * 0.42f, STROKE / 2f, 0.001f)
            : new Vector3f(STROKE / 2f, DIGIT_HEIGHT * 0.21f, 0.001f);
        at.x += x;
        add(scene, left, cube, ghost, at, size);
        segments[s] = add(scene, left, cube, on, at, size);
        this.sizes.put(segments[s], size);
      }
      this.digits.add(segments);
    }
    cells(scene, left, cube, on, ghost, SPEED_CELLS, -DIGIT_HEIGHT * 0.95f, this.speedCells, DIGIT_STEP * 3f);
    // the jump: a chevron, then the bar of cells
    final float bar = DIGIT_STEP * 3f;
    for (int k = -1; k <= 1; k += 2) {
      final var chevron = add(scene, right, cube, jump, new Vector3f(-bar / 2f - DIGIT_HEIGHT * 0.24f, k * DIGIT_HEIGHT * 0.11f, 0f),
          new Vector3f(STROKE / 2f, DIGIT_HEIGHT * 0.15f, 0.001f));
      chevron.transform().rotation().rotateZ((float) Math.toRadians(k * 40));
    }
    cells(scene, right, cube, jump, ghost, JUMP_CELLS, 0f, this.jumpCells, bar);
    // under it, the gun's battery: a bolt, then its bar (like the speed's, across from it)
    final float below = -DIGIT_HEIGHT * 0.95f;
    for (int k = -1; k <= 1; k += 2) {
      final var bolt = add(scene, right, cube, battery,
          new Vector3f(-bar / 2f - DIGIT_HEIGHT * 0.24f + k * DIGIT_HEIGHT * 0.03f, below + k * DIGIT_HEIGHT * 0.08f, 0f),
          new Vector3f(STROKE / 2f, DIGIT_HEIGHT * 0.1f, 0.001f));
      bolt.transform().rotation().rotateZ((float) Math.toRadians(-25));
    }
    cells(scene, right, cube, battery, ghost, BATTERY_CELLS, below, this.batteryCells, bar);
    // the ring: dashes round the crosshair, straight ahead
    for (int i = 0; i < RING_DASHES; i++) {
      final double a = 2 * Math.PI * i / RING_DASHES;
      final var dash = add(scene, cockpit, cube, on,
          new Vector3f((float) Math.cos(a) * RING_RADIUS, (float) Math.sin(a) * RING_RADIUS, -RING_DISTANCE),
          new Vector3f(0.0013f, RING_RADIUS * 0.14f, 0.0005f));
      dash.transform().rotation().rotateZ((float) a);
    }
  }

  /**
   * A readout's place: just inside the glass above the sill, centred {@code degrees} round. Its lights follow the
   * curve of the sill (and the dashboard) rather than a straight line, each one facing the pilot.
   */
  private record Panel(Entity cockpit, float degrees) {
    /** Where a light {@code local.x} along the readout and {@code local.y} above its middle line is, in the cockpit. */
    Vector3f at(Vector3f local) {
      final var middle = onGlass(this.degrees, 0f);
      final float radius = (float) Math.hypot(middle.x - CockpitArt.CENTER.x, middle.z - CockpitArt.CENTER.z);
      return onGlass(this.degrees + (float) Math.toDegrees(local.x / radius), local.y);
    }

    private static Vector3f onGlass(float degrees, float y) {
      final var at = CockpitArt.sill(degrees, READOUT_LIFT + y);
      final var axis = new Vector3f(CockpitArt.CENTER.x, at.y, CockpitArt.CENTER.z);
      return at.sub(axis).mul(0.97f).add(axis); // inside the glass
    }
  }

  /** The middle of segment {@code s} of a digit centred on the origin. */
  static Vector3f segment(int s) {
    final float hx = DIGIT_WIDTH / 2f;
    final float hy = DIGIT_HEIGHT / 2f;
    return switch (s) {
    case 0 -> new Vector3f(0f, hy, 0f);
    case 1 -> new Vector3f(hx, hy / 2f, 0f);
    case 2 -> new Vector3f(hx, -hy / 2f, 0f);
    case 3 -> new Vector3f(0f, -hy, 0f);
    case 4 -> new Vector3f(-hx, -hy / 2f, 0f);
    case 5 -> new Vector3f(-hx, hy / 2f, 0f);
    default -> new Vector3f(0f, 0f, 0f);
    };
  }

  private void cells(Scene scene, Panel panel, GpuMesh cube, Material on, Material ghost, int count, float y,
      List<Entity> into, float width) {
    final float step = width / count;
    for (int i = 0; i < count; i++) {
      final var at = new Vector3f(-width / 2f + step * (i + 0.5f), y, 0f);
      final var size = new Vector3f(step * 0.36f, DIGIT_HEIGHT * 0.12f, 0.001f);
      add(scene, panel, cube, ghost, at, size);
      final var cell = add(scene, panel, cube, on, at, size);
      into.add(cell);
      this.sizes.put(cell, size);
    }
  }

  private static Entity add(Scene scene, Entity parent, GpuMesh cube, Material material, Vector3f at, Vector3f size) {
    final var e = new Entity("glass light");
    e.add(new MeshRenderer(cube, material)).castShadows = false;
    e.transform().position().set(at);
    e.transform().scale().set(size);
    e.transform().setParent(parent.transform());
    scene.add(e);
    return e;
  }

  /** A light on a readout, at {@code local} along its curve, facing the pilot. */
  private static Entity add(Scene scene, Panel panel, GpuMesh cube, Material material, Vector3f local,
      Vector3f size) {
    final var at = panel.at(local);
    final var e = add(scene, panel.cockpit(), cube, material, at, size);
    e.transform().rotation().set(PlayState.aim(at, new Vector3f(0f, 1f, 0f)));
    return e;
  }

  /**
   * Shows {@code speed} (and how much of {@code maxSpeed} it is) and the jump's charge: {@code charge} from 0 just
   * after a jump to 1 when the next is ready, when the bar pulses ({@code time} in seconds). {@code battery} is what is
   * left in the gun's battery, 0 to 1: its bar turns from teal to red as it runs low, and blinks when it is flat.
   */
  void show(float speed, float maxSpeed, float charge, float battery, float time) {
    final int value = Math.max(0, Math.min(999, Math.round(speed)));
    final int[] lights = segments(value);
    for (int i = 0; i < SPEED_DIGITS; i++) {
      for (int s = 0; s < 7; s++) {
        light(this.digits.get(i)[s], (lights[i] & (1 << s)) != 0);
      }
    }
    final int speedLit = Math.round(SPEED_CELLS * Math.min(1f, speed / Math.max(1e-3f, maxSpeed)));
    for (int i = 0; i < SPEED_CELLS; i++) {
      light(this.speedCells.get(i), i < speedLit);
    }
    final int jumpLit = charge >= 1f ? JUMP_CELLS : (int) (JUMP_CELLS * Math.max(0f, charge));
    for (int i = 0; i < JUMP_CELLS; i++) {
      light(this.jumpCells.get(i), i < jumpLit);
    }
    final float pulse = charge >= 1f ? 0.75f + 0.25f * (float) Math.sin(time * 5f) : 0.55f;
    this.jumpLight.emissive.set(this.jumpColor).mul(pulse);
    final int batteryLit = (int) Math.ceil(BATTERY_CELLS * Math.max(0f, Math.min(1f, battery)) - 1e-3f);
    for (int i = 0; i < BATTERY_CELLS; i++) {
      light(this.batteryCells.get(i), i < batteryLit);
    }
    final float low = Math.max(0f, Math.min(1f, 1f - battery / 0.4f)); // red below 40%
    this.batteryLight.emissive.set(this.batteryColor).lerp(LOW_BATTERY, low);
    if (batteryLit == 0) {
      this.batteryLight.emissive.mul((int) (time * 6) % 2 == 0 ? 1f : 0.3f); // the bolt blinks
    }
  }

  /** The battery bar's colour when it is nearly flat. */
  private static final Vector3f LOW_BATTERY = new Vector3f(1.5f, 0.2f, 0.12f);

  /** The segments each of the {@link #SPEED_DIGITS} digits lights for {@code value}: no leading zeros. */
  static int[] segments(int value) {
    final int[] out = new int[SPEED_DIGITS];
    int v = value;
    for (int i = SPEED_DIGITS - 1; i >= 0; i--) {
      final boolean blank = v == 0 && i < SPEED_DIGITS - 1;
      out[i] = blank ? 0 : DIGITS[v % 10];
      v /= 10;
    }
    return out;
  }

  private void light(Entity e, boolean on) {
    if (on) {
      e.transform().scale().set(this.sizes.get(e));
    } else {
      e.transform().scale().zero();
    }
  }
}
