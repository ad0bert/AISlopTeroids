package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OldNameTest {
  @TempDir
  Path dir;

  @Test
  void theFirstStartTakesTheOldFilesAlongAndLeavesThemThere() throws IOException {
    final var old = Files.createDirectories(this.dir.resolve(OldName.OLD_ID));
    Files.writeString(old.resolve("scores.properties"), "best=1200");
    Files.writeString(old.resolve("tuning.properties"), "ship.lives=5");
    final var now = this.dir.resolve("ai-slop-teroids");

    assertTrue(OldName.carryOver(old, now));
    assertEquals("best=1200", Files.readString(now.resolve("scores.properties")));
    assertEquals("ship.lives=5", Files.readString(now.resolve("tuning.properties")));
    assertTrue(Files.exists(old.resolve("scores.properties")), "the old folder stays as it was");
  }

  @Test
  void onceTheNewGameHasItsOwnFilesTheOldOnesAreLeftAlone() throws IOException {
    final var old = Files.createDirectories(this.dir.resolve(OldName.OLD_ID));
    Files.writeString(old.resolve("scores.properties"), "best=1200");
    final var now = Files.createDirectories(this.dir.resolve("ai-slop-teroids"));
    Files.writeString(now.resolve("scores.properties"), "best=3000");

    assertFalse(OldName.carryOver(old, now));
    assertEquals("best=3000", Files.readString(now.resolve("scores.properties")));
  }

  @Test
  void withoutAnOldFolderNothingHappens() {
    assertFalse(OldName.carryOver(this.dir.resolve("missing"), this.dir.resolve("ai-slop-teroids")));
  }
}
