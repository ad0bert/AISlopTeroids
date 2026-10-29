package aislopteroids;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import engine.core.UserDirs;

/**
 * Up to v0.6.0 the game was called SimpleAstroids and kept the player's settings, scores and tuning in folders named
 * after that ({@code simple-astroids}). The first time the renamed game starts, it takes them along.
 */
final class OldName {
  private static final Logger log = LoggerFactory.getLogger(OldName.class);
  /** The application id the game had before. */
  static final String OLD_ID = "simple-astroids";

  private OldName() {
  }

  /** Copies the old folders' files into the new ones, if the new ones have nothing yet. The old folders stay. */
  static void carryOver(UserDirs now) {
    final var before = UserDirs.of(OLD_ID);
    carryOver(before.configPath(), now.configPath());
    if (!before.dataPath().equals(before.configPath())) {
      carryOver(before.dataPath(), now.dataPath());
    }
  }

  /**
   * Copies every file directly in {@code from} into {@code to}, unless {@code from} is missing or {@code to} already
   * has files (the new game has been played: its own files win). Returns whether anything was copied.
   */
  static boolean carryOver(Path from, Path to) {
    try {
      if (!Files.isDirectory(from) || hasFiles(to)) {
        return false;
      }
      Files.createDirectories(to);
      boolean copied = false;
      try (Stream<Path> files = Files.list(from)) {
        for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
          Files.copy(f, to.resolve(f.getFileName()));
          copied = true;
        }
      }
      if (copied) {
        log.info("Took the settings and scores of SimpleAstroids along from {} to {}", from, to);
      }
      return copied;
    } catch (IOException e) {
      log.warn("Could not take the old settings and scores along from {}: {}", from, e.getMessage());
      return false;
    }
  }

  private static boolean hasFiles(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return false;
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.anyMatch(Files::isRegularFile);
    }
  }
}
