package aislopteroids;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import engine.audio.AudioData;

class SoundsTest {
  /** The loops must join without a click: the step from the last sample to the first is like any other step. */
  @Test
  void theAmbienceLoopsJoinWithoutAClick() {
    final Map<String, AudioData> loops = Map.of("title", Sounds.titleAmbience(), "play", Sounds.playAmbience(), "hum",
        Sounds.engineHum(), "danger", Sounds.danger());
    loops.forEach((name, data) -> {
      final int ch = data.channels();
      final short[] s = data.samples();
      final int frames = data.frames();
      for (int c = 0; c < ch; c++) {
        int largestStep = 1;
        int peak = 0;
        for (int i = 0; i + 1 < frames; i++) {
          largestStep = Math.max(largestStep, Math.abs(s[(i + 1) * ch + c] - s[i * ch + c]));
          peak = Math.max(peak, Math.abs(s[i * ch + c]));
        }
        final int wrap = Math.abs(s[c] - s[(frames - 1) * ch + c]);
        assertTrue(wrap <= largestStep, name + " channel " + c + ": a jump of " + wrap + " where it loops (largest "
            + "step inside: " + largestStep + ")");
        assertTrue(peak > Short.MAX_VALUE * 0.2 && peak < Short.MAX_VALUE * 0.6, name + ": peak " + peak);
      }
    });
  }
}
