package astroids;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import engine.render.UiCanvas;

/** Drawing helpers for the 2D overlay: shadowed text, the crosshair, and the pointer to the boss. */
final class Hud {
  private Hud() {
  }

  static void text(UiCanvas ui, String text, float x, float y, float scale, float r, float g, float b, float a) {
    ui.drawText(text, x + scale, y + scale, scale, 0f, 0f, 0f, 0.7f * a);
    ui.drawText(text, x, y, scale, r, g, b, a);
  }

  static void centered(UiCanvas ui, String text, int width, float y, float scale, float r, float g, float b,
      float a) {
    text(ui, text, (width - ui.textWidth(text, scale)) / 2f, y, scale, r, g, b, a);
  }

  static void crosshair(UiCanvas ui, int width, int height) {
    final float cx = width / 2f;
    final float cy = height / 2f;
    ui.drawRect(cx - 10f, cy - 1f, 7f, 2f, 0.6f, 1f, 0.8f, 0.85f);
    ui.drawRect(cx + 3f, cy - 1f, 7f, 2f, 0.6f, 1f, 0.8f, 0.85f);
    ui.drawRect(cx - 1f, cy - 10f, 2f, 7f, 0.6f, 1f, 0.8f, 0.85f);
    ui.drawRect(cx - 1f, cy + 3f, 2f, 7f, 0.6f, 1f, 0.8f, 0.85f);
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
        text(ui, label, x - ui.textWidth(label, 1f) / 2f, y + s + 4f, 1f, r, g, b, 1f);
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
    text(ui, label, x - ui.textWidth(label, 1f) / 2f - dx * 22f, y - dy * 22f - 7f, 1f, r, g, b, 1f);
  }

  private static void corner(UiCanvas ui, float x, float y, int sx, int sy, float r, float g, float b) {
    ui.drawRect(sx > 0 ? x : x - 10f, sy > 0 ? y : y - 2f, 10f, 2f, r, g, b, 0.9f);
    ui.drawRect(sx > 0 ? x : x - 2f, sy > 0 ? y : y - 10f, 2f, 10f, r, g, b, 0.9f);
  }
}
