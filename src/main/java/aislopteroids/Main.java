package aislopteroids;

import java.io.IOException;

import engine.core.Engine;
import engine.core.EngineConfig;
import engine.core.Settings;
import engine.core.UserDirs;

public final class Main {
  private Main() {
  }

  public static void main(String[] args) throws InterruptedException {
    new Engine(withSavedWindow(EngineConfig.load("application.properties")), new AISlopTeroidsGame()).run();
  }

  /**
   * Opens the window as the player left it (fullscreen or not, vsync), instead of switching it once it is up: on
   * Windows, a switch while the first frames are drawn can time out waiting for the window. {@code -Dengine.fullscreen}
   * and {@code -Dengine.vsync} still win.
   */
  static EngineConfig withSavedWindow(EngineConfig config) {
    final Settings settings;
    try {
      settings = Settings.load(UserDirs.of(config.id()).config().resolve("settings.properties"));
    } catch (IOException e) {
      return config; // no config directory: the game says so later
    }
    var c = config;
    if (System.getProperty("engine.fullscreen") == null) {
      c = c.withFullscreen(settings.getBoolean("video.fullscreen", c.fullscreen()));
    }
    if (System.getProperty("engine.vsync") == null) {
      c = c.withVsync(settings.getBoolean("video.vsync", c.vsync()));
    }
    return c;
  }
}
