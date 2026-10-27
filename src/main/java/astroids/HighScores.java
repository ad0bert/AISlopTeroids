package astroids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import astroids.model.Difficulty;
import engine.core.Settings;

/**
 * The ten best games of one difficulty: name, score and the wave reached, best first. Kept in the engine's
 * {@link Settings} (the scores file in the user's data directory) under {@code scores.<difficulty>.<rank>.*}.
 */
final class HighScores {
  static final int SIZE = 10;
  static final int MAX_NAME = 12;
  static final String DEFAULT_NAME = "PLAYER";

  record Entry(String name, int score, int wave) {
  }

  private final List<Entry> entries = new ArrayList<>();

  List<Entry> entries() {
    return Collections.unmodifiableList(this.entries);
  }

  /** The best score, or 0 for an empty table. */
  int best() {
    return this.entries.isEmpty() ? 0 : this.entries.get(0).score();
  }

  /** Where a game with this score would go (0 = top), or -1 if it does not make the table. */
  int rankFor(int score) {
    if (score <= 0) {
      return -1;
    }
    for (int i = 0; i < this.entries.size(); i++) {
      if (score > this.entries.get(i).score()) {
        return i;
      }
    }
    return this.entries.size() < SIZE ? this.entries.size() : -1; // a tie goes below the one who got there first
  }

  /** Puts a game in the table (dropping the eleventh); returns its rank, or -1 if it did not make it. */
  int add(String name, int score, int wave) {
    final int rank = rankFor(score);
    if (rank >= 0) {
      this.entries.add(rank, new Entry(cleanName(name), score, wave));
      while (this.entries.size() > SIZE) {
        this.entries.remove(SIZE);
      }
    }
    return rank;
  }

  /**
   * A name as the table keeps it: letters, digits, spaces and a few marks, upper case, at most {@link #MAX_NAME}
   * characters; {@link #DEFAULT_NAME} for nothing usable.
   */
  static String cleanName(String name) {
    final var sb = new StringBuilder();
    if (name != null) {
      for (char c : name.toUpperCase(Locale.ROOT).toCharArray()) {
        if (allowed(c) && sb.length() < MAX_NAME) {
          sb.append(c);
        }
      }
    }
    final var clean = sb.toString().trim().replaceAll(" {2,}", " ");
    return clean.isEmpty() ? DEFAULT_NAME : clean;
  }

  /** Whether the name entry takes this character. */
  static boolean allowed(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || " -_.!".indexOf(c) >= 0;
  }

  static HighScores load(Settings settings, Difficulty difficulty) {
    final var table = new HighScores();
    final var prefix = prefix(difficulty);
    for (int i = 1; i <= SIZE; i++) {
      final int score = settings.getInt(prefix + i + ".score", 0);
      if (score <= 0) {
        break;
      }
      table.add(settings.get(prefix + i + ".name", DEFAULT_NAME), score, settings.getInt(prefix + i + ".wave", 0));
    }
    return table;
  }

  void store(Settings settings, Difficulty difficulty) {
    final var prefix = prefix(difficulty);
    for (int i = 1; i <= SIZE; i++) {
      if (i <= this.entries.size()) {
        final var e = this.entries.get(i - 1);
        settings.set(prefix + i + ".name", e.name()).set(prefix + i + ".score", e.score())
            .set(prefix + i + ".wave", e.wave());
      } else {
        settings.remove(prefix + i + ".name").remove(prefix + i + ".score").remove(prefix + i + ".wave");
      }
    }
  }

  /**
   * Moves the single best score of v0.3 and before ({@code highScore}) into the Normal table, which plays like those
   * versions did. Returns whether there was one.
   */
  static boolean migrate(Settings settings) {
    final int old = settings.getInt("highScore", 0);
    if (settings.get("highScore", null) == null) {
      return false;
    }
    settings.remove("highScore");
    if (old > 0) {
      final var normal = load(settings, Difficulty.NORMAL);
      normal.add(DEFAULT_NAME, old, 0);
      normal.store(settings, Difficulty.NORMAL);
    }
    return true;
  }

  private static String prefix(Difficulty difficulty) {
    return "scores." + difficulty.name().toLowerCase(Locale.ROOT) + ".";
  }
}
