package astroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import astroids.model.Difficulty;
import engine.core.Settings;

class HighScoresTest {
  @TempDir
  Path dir;

  @Test
  void theTableKeepsTheTenBestInOrder() {
    final var t = new HighScores();
    assertEquals(0, t.best());
    assertEquals(-1, t.rankFor(0), "nothing for no points");
    for (int i = 1; i <= 12; i++) {
      t.add("P" + i, i * 100, i);
    }
    assertEquals(HighScores.SIZE, t.entries().size());
    assertEquals(1200, t.best());
    assertEquals("P12", t.entries().get(0).name());
    assertEquals(300, t.entries().get(9).score(), "100 and 200 dropped out");

    assertEquals(-1, t.rankFor(300), "a tie with the last does not push it out");
    assertEquals(9, t.rankFor(301));
    assertEquals(0, t.rankFor(5000));
    assertEquals(3, t.add("NEW", 1000, 4), "a tie goes below the earlier one");
    assertEquals("P10", t.entries().get(2).name());
    assertEquals(400, t.entries().get(9).score());
  }

  @Test
  void namesAreCleanedUp() {
    assertEquals("ADA", HighScores.cleanName("  ada "));
    assertEquals("R2-D2 X", HighScores.cleanName("r2-d2   x"));
    assertEquals("ABCDEFGHIJKL", HighScores.cleanName("abcdefghijklmnop"));
    assertEquals("NO", HighScores.cleanName("nöo=\t"));
    assertEquals(HighScores.DEFAULT_NAME, HighScores.cleanName("   "));
    assertEquals(HighScores.DEFAULT_NAME, HighScores.cleanName(null));
    assertTrue(HighScores.allowed('x'));
    assertFalse(HighScores.allowed('\n'));
    assertFalse(HighScores.allowed('\b'));
  }

  @Test
  void eachDifficultyHasItsOwnTableThatSurvivesASave() throws IOException {
    final var file = this.dir.resolve("scores.properties");
    final var settings = Settings.load(file);
    final var hard = HighScores.load(settings, Difficulty.HARD);
    hard.add("ACE", 9000, 7);
    hard.add("BOB", 4000, 4);
    hard.store(settings, Difficulty.HARD);
    final var easy = HighScores.load(settings, Difficulty.EASY);
    easy.add("KID", 500, 2);
    easy.store(settings, Difficulty.EASY);
    settings.save();

    final var again = Settings.load(file);
    assertEquals(List.of(new HighScores.Entry("ACE", 9000, 7), new HighScores.Entry("BOB", 4000, 4)),
        HighScores.load(again, Difficulty.HARD).entries());
    assertEquals(500, HighScores.load(again, Difficulty.EASY).best());
    assertTrue(HighScores.load(again, Difficulty.NORMAL).entries().isEmpty());
  }

  @Test
  void theOldBestScoreMovesIntoTheNormalTable() {
    final var settings = Settings.load(this.dir.resolve("scores.properties")).set("highScore", 3210);
    assertTrue(HighScores.migrate(settings));
    assertEquals(null, settings.get("highScore", null));
    final var normal = HighScores.load(settings, Difficulty.NORMAL);
    assertEquals(List.of(new HighScores.Entry(HighScores.DEFAULT_NAME, 3210, 0)), normal.entries());
    assertFalse(HighScores.migrate(settings), "only once");
    assertEquals(1, HighScores.load(settings, Difficulty.NORMAL).entries().size());
  }
}
