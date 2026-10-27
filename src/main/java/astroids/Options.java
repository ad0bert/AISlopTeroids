package astroids;

import engine.audio.AudioBus;
import engine.core.Engine;
import engine.core.Settings;

/**
 * The player's choices: mouse sensitivity, inverted look, volumes, fullscreen, vsync and the radar. Kept in the
 * engine's {@link Settings} file in the user's config directory, so they survive restarts.
 */
final class Options {
  static final float MIN_SENSITIVITY = 0.02f;
  static final float MAX_SENSITIVITY = 0.3f;
  /** Degrees of turn per pixel of mouse movement. */
  static final float DEFAULT_SENSITIVITY = 0.1f;

  float mouseSensitivity = DEFAULT_SENSITIVITY;
  boolean invertY;
  float masterVolume = 1f;
  float effectsVolume = 1f;
  boolean fullscreen;
  boolean vsync = true;
  boolean radar = true;

  /** The options in {@code settings}, with the window's current state as the default for fullscreen and vsync. */
  static Options load(Settings settings, boolean fullscreen, boolean vsync) {
    final var o = new Options();
    o.mouseSensitivity = clamp(settings.getFloat("controls.mouseSensitivity", DEFAULT_SENSITIVITY), MIN_SENSITIVITY,
        MAX_SENSITIVITY);
    o.invertY = settings.getBoolean("controls.invertY", false);
    o.masterVolume = clamp(settings.getFloat("audio.master", 1f), 0f, 1f);
    o.effectsVolume = clamp(settings.getFloat("audio.effects", 1f), 0f, 1f);
    o.fullscreen = settings.getBoolean("video.fullscreen", fullscreen);
    o.vsync = settings.getBoolean("video.vsync", vsync);
    o.radar = settings.getBoolean("hud.radar", true);
    return o;
  }

  void store(Settings settings) {
    settings.set("controls.mouseSensitivity", this.mouseSensitivity)
        .set("controls.invertY", this.invertY)
        .set("audio.master", this.masterVolume)
        .set("audio.effects", this.effectsVolume)
        .set("video.fullscreen", this.fullscreen)
        .set("video.vsync", this.vsync)
        .set("hud.radar", this.radar);
  }

  /** Puts the volumes, fullscreen and vsync into effect. */
  void apply(Engine engine) {
    final var mixer = engine.audio().mixer();
    mixer.setVolume(AudioBus.MASTER, this.masterVolume);
    mixer.setVolume(AudioBus.EFFECTS, this.effectsVolume);
    if (engine.isFullscreen() != this.fullscreen) {
      engine.setFullscreen(this.fullscreen);
    }
    engine.setVsync(this.vsync);
  }

  /** Pitch change in degrees for a vertical mouse movement (screen y grows downwards). */
  float pitchFor(int mouseDy) {
    return (this.invertY ? mouseDy : -mouseDy) * this.mouseSensitivity;
  }

  float yawFor(int mouseDx) {
    return -mouseDx * this.mouseSensitivity;
  }

  private static float clamp(float v, float min, float max) {
    return Float.isNaN(v) ? min : Math.max(min, Math.min(max, v));
  }
}
