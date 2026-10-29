package astroids;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import engine.render.UiCanvas;
import engine.render.UiFont;

/**
 * Drawing helpers for the 2D overlay: shadowed text, the crosshair, markers, and score tables.
 *
 * <p>
 * Text sizes are given as a scale of {@link #BASE_SIZE} pixels in a window {@link #BASE_HEIGHT} pixels high, and grow
 * with the window ({@link #screen}), so text keeps its size on screen in fullscreen too. Every size gets a font
 * rasterized at that size, so glyphs stay sharp instead of being stretched from one small font.
 */
final class Hud {
  /** Text at scale 1 is this many pixels high in a window {@link #BASE_HEIGHT} pixels high. */
  static final int BASE_SIZE = 16;
  static final float BASE_HEIGHT = 720f;
  private static final Map<Integer, UiFont> FONTS = new HashMap<>();
  /** How much bigger than at {@link #BASE_HEIGHT} the overlay is drawn: set with {@link #screen} every frame. */
  private static float zoom = 1f;

  private Hud() {
  }

  /** Call at the start of drawing a screen: text and panels grow with the window's height (never shrink). */
  static void screen(int width, int height) {
    zoom = zoomFor(height);
  }

  static float zoomFor(int height) {
    return Math.max(1f, Math.min(3f, height / BASE_HEIGHT));
  }

  /** How much bigger than at {@link #BASE_HEIGHT} the overlay is now. */
  static float zoom() {
    return zoom;
  }

  /** The font for text at this scale: sans-serif at the pixel size it is shown at. */
  static UiFont font(float scale) {
    final int px = Math.max(8, Math.round(BASE_SIZE * scale * zoom));
    return FONTS.computeIfAbsent(px, size -> new UiFont("SansSerif", UiFont.PLAIN, size));
  }

  static float width(UiCanvas ui, String text, float scale) {
    return ui.textWidth(font(scale), text, 1f);
  }

  static float lineHeight(UiCanvas ui, float scale) {
    return ui.lineHeight(font(scale), 1f);
  }

  /** Text with a soft dark shadow below and to the right of it, so it reads on any background. */
  static void text(UiCanvas ui, String text, float x, float y, float scale, float r, float g, float b, float a) {
    final var font = font(scale);
    final float shadow = Math.max(1f, Math.round(font.size() / 11f));
    ui.drawText(font, text, x + shadow, y + shadow, 1f, 0f, 0f, 0f, 0.85f * a);
    ui.drawText(font, text, x, y, 1f, r, g, b, a);
  }

  static void centered(UiCanvas ui, String text, int width, float y, float scale, float r, float g, float b,
      float a) {
    text(ui, text, (width - width(ui, text, scale)) / 2f, y, scale, r, g, b, a);
  }

  /** A dark, see-through panel behind a block of text, so things moving behind it do not cut through the letters. */
  static void backing(UiCanvas ui, float x, float y, float w, float h) {
    ui.drawRect(x, y, w, h, 0f, 0.01f, 0.03f, 0.65f);
  }

  static void crosshair(UiCanvas ui, int width, int height) {
    final float cx = width / 2f;
    final float cy = height / 2f;
    ui.drawRect(cx - 10f, cy - 1f, 7f, 2f, 0.6f, 1f, 0.8f, 0.85f);
    ui.drawRect(cx + 3f, cy - 1f, 7f, 2f, 0.6f, 1f, 0.8f, 0.85f);
    ui.drawRect(cx - 1f, cy - 10f, 2f, 7f, 0.6f, 1f, 0.8f, 0.85f);
    ui.drawRect(cx - 1f, cy + 3f, 2f, 7f, 0.6f, 1f, 0.8f, 0.85f);
  }

  /**
   * Where a point in front of the camera (in camera space, looking along -z) is on the screen: x and y in pixels, or
   * null when it is not in front.
   */
  static float[] project(Vector3f local, int width, int height, float fovDegrees) {
    if (local.z >= -1e-4f) {
      return null;
    }
    final float tanHalf = (float) Math.tan(Math.toRadians(fovDegrees) / 2);
    final float aspect = (float) width / height;
    final float nx = local.x / -local.z / (tanHalf * aspect);
    final float ny = local.y / -local.z / tanHalf;
    return new float[] { (nx * 0.5f + 0.5f) * width, (0.5f - ny * 0.5f) * height };
  }

  /** Width of a score table column at scale 1 (in a window {@link #BASE_HEIGHT} high): rank, name, score, wave. */
  private static final float TABLE_WIDTH = 330f;

  /** Width of a score table column at this scale, as drawn now. */
  static float tableWidth(float scale) {
    return TABLE_WIDTH * scale * zoom;
  }

  /**
   * Rows {@code from} to {@code to} (exclusive) of a score table, one under the other, starting at the top left
   * {@code x, y}; the row {@code highlight} (or none, -1) stands out. Returns the y below the last row.
   */
  static float scoreRows(UiCanvas ui, List<HighScores.Entry> entries, int from, int to, int highlight, float x,
      float y, float scale) {
    final float u = scale * zoom; // the column positions grow with the text
    for (int i = from; i < to && i < entries.size(); i++) {
      final var e = entries.get(i);
      final boolean mine = i == highlight;
      final float r = mine ? 1f : 0.85f;
      final float g = mine ? 0.85f : 0.86f;
      final float b = mine ? 0.4f : 0.9f;
      final var rank = (i + 1) + ".";
      text(ui, rank, x + 34f * u - width(ui, rank, scale), y, scale, r, g, b, 1f);
      text(ui, e.name(), x + 44f * u, y, scale, r, g, b, 1f);
      final var score = Integer.toString(e.score());
      text(ui, score, x + 260f * u - width(ui, score, scale), y, scale, r, g, b, 1f);
      final var wave = e.wave() > 0 ? "w" + e.wave() : "";
      text(ui, wave, x + 272f * u, y, scale * 0.85f, r * 0.8f, g * 0.8f, b * 0.8f, 1f);
      y += lineHeight(ui, scale) + 3f * zoom;
    }
    return y;
  }

  static void dim(UiCanvas ui, int width, int height, float alpha) {
    ui.drawRect(0f, 0f, width, height, 0f, 0f, 0f, alpha);
  }

  /**
   * Marks something: brackets around it when it is on screen, otherwise an arrow at the edge of the screen in its
   * direction. {@code toTarget} is the shortest way from the camera to it, {@code view} the camera's rotation.
   */
  static void marker(UiCanvas ui, int width, int height, Vector3f toTarget, Quaternionf view, float fovDegrees,
      String label, float r, float g, float b) {
    final var local = view.conjugate(new Quaternionf()).transform(new Vector3f(toTarget));
    final float tanHalf = (float) Math.tan(Math.toRadians(fovDegrees) / 2);
    final float aspect = (float) width / height;
    if (local.z < 0f) {
      final float nx = local.x / -local.z / (tanHalf * aspect);
      final float ny = local.y / -local.z / tanHalf;
      if (Math.abs(nx) < 0.95f && Math.abs(ny) < 0.9f) {
        final float x = (nx * 0.5f + 0.5f) * width;
        final float y = (0.5f - ny * 0.5f) * height;
        final float s = Math.max(14f, Math.min(60f, 300f / local.length() * 4f));
        corner(ui, x - s, y - s, 1, 1, r, g, b);
        corner(ui, x + s, y - s, -1, 1, r, g, b);
        corner(ui, x - s, y + s, 1, -1, r, g, b);
        corner(ui, x + s, y + s, -1, -1, r, g, b);
        text(ui, label, x - width(ui, label, 1f) / 2f, y + s + 4f, 1f, r, g, b, 1f);
        return;
      }
    }
    // off screen (or behind): an arrow on a ring around the middle, pointing its way
    float dx = local.x;
    float dy = -local.y;
    final float len = (float) Math.sqrt(dx * dx + dy * dy);
    if (len < 1e-4f) {
      dx = 0f;
      dy = 1f;
    } else {
      dx /= len;
      dy /= len;
    }
    final float ring = Math.min(width, height) * 0.4f;
    final float x = width / 2f + dx * ring;
    final float y = height / 2f + dy * ring;
    for (int i = 0; i < 4; i++) { // a short dotted arrow
      final float t = i * 7f;
      final float size = 8f - i * 1.5f;
      ui.drawRect(x + dx * t - size / 2f, y + dy * t - size / 2f, size, size, r, g, b, 0.9f);
    }
    text(ui, label, x - width(ui, label, 1f) / 2f - dx * 22f, y - dy * 22f - 7f, 1f, r, g, b, 1f);
  }

  private static void corner(UiCanvas ui, float x, float y, int sx, int sy, float r, float g, float b) {
    ui.drawRect(sx > 0 ? x : x - 10f, sy > 0 ? y : y - 2f, 10f, 2f, r, g, b, 0.9f);
    ui.drawRect(sx > 0 ? x : x - 2f, sy > 0 ? y : y - 10f, 2f, 10f, r, g, b, 0.9f);
  }
}
