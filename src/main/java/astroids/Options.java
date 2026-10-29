package astroids;

import astroids.model.Difficulty;
import engine.audio.AudioBus;
import engine.core.Engine;
import engine.core.Settings;

/**
 * The player's choices: mouse sensitivity, inverted look, volumes (all, effects, ambience), fullscreen, vsync,
 * graphics quality, the radar and the view (cockpit, none or behind the ship), and the difficulty (picked on the
 * title screen). Kept in the engine's {@link Settings} file in the user's config directory, so they survive restarts.
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
  float ambienceVolume = 1f;
  boolean fullscreen;
  boolean vsync = true;
  boolean radar = true;
  View view = View.COCKPIT;
  Quality quality = Quality.HIGH;
  Difficulty difficulty = Difficulty.NORMAL;

  /** The options in {@code settings}, with the window's current state as the default for fullscreen and vsync. */
  static Options load(Settings settings, boolean fullscreen, boolean vsync) {
    final var o = new Options();
    o.mouseSensitivity = clamp(settings.getFloat("controls.mouseSensitivity", DEFAULT_SENSITIVITY), MIN_SENSITIVITY,
        MAX_SENSITIVITY);
    o.invertY = settings.getBoolean("controls.invertY", false);
    o.masterVolume = clamp(settings.getFloat("audio.master", 1f), 0f, 1f);
    o.effectsVolume = clamp(settings.getFloat("audio.effects", 1f), 0f, 1f);
    o.ambienceVolume = clamp(settings.getFloat("audio.ambience", 1f), 0f, 1f);
    o.fullscreen = settings.getBoolean("video.fullscreen", fullscreen);
    o.vsync = settings.getBoolean("video.vsync", vsync);
    o.radar = settings.getBoolean("hud.radar", true);
    // before the views there was only the cockpit, on or off
    final var before = settings.getBoolean("hud.cockpit", true) ? View.COCKPIT : View.CLEAR;
    o.view = View.parse(settings.get("hud.view", before.name()), before);
    o.quality = Quality.parse(settings.get("video.quality", Quality.HIGH.name()));
    o.difficulty = Difficulty.parse(settings.get("game.difficulty", Difficulty.NORMAL.name()));
    return o;
  }

  void store(Settings settings) {
    settings.set("controls.mouseSensitivity", this.mouseSensitivity)
        .set("controls.invertY", this.invertY)
        .set("audio.master", this.masterVolume)
        .set("audio.effects", this.effectsVolume)
        .set("audio.ambience", this.ambienceVolume)
        .set("video.fullscreen", this.fullscreen)
        .set("video.vsync", this.vsync)
        .set("hud.radar", this.radar)
        .set("hud.view", this.view.name())
        .set("video.quality", this.quality.name())
        .set("game.difficulty", this.difficulty.name());
  }

  /** Puts the volumes, fullscreen and vsync into effect. */
  void apply(Engine engine) {
    final var mixer = engine.audio().mixer();
    mixer.setVolume(AudioBus.MASTER, this.masterVolume);
    mixer.setVolume(AudioBus.EFFECTS, this.effectsVolume);
    mixer.setVolume(AudioBus.MUSIC, this.ambienceVolume);
    if (engine.isFullscreen() != this.fullscreen) {
      engine.setFullscreen(this.fullscreen);
    }
    engine.setVsync(this.vsync);
    this.quality.apply(engine.backend().settings());
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
