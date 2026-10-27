package astroids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class HudTextTest {
  @Test
  void textGrowsWithTheWindowButNeverShrinks() {
    assertEquals(1f, Hud.zoomFor(720));
    assertEquals(1.5f, Hud.zoomFor(1080));
    assertEquals(2f, Hud.zoomFor(1440));
    assertEquals(1f, Hud.zoomFor(480), "a small window keeps the text readable");
    assertEquals(3f, Hud.zoomFor(10000), "with a limit");
  }

  @Test
  void eachSizeHasItsOwnSharpFont() {
    Hud.screen(1280, 720);
    assertEquals(16, Hud.font(1f).size());
    assertEquals(19, Hud.font(1.2f).size(), "rasterized at the size it is shown at, not stretched");
    Hud.screen(1920, 1080);
    assertEquals(29, Hud.font(1.2f).size());
    assertSame(Hud.font(1.2f), Hud.font(1.2f), "made once per size");
    Hud.screen(1280, 720);
  }
}
