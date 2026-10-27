package astroids;

import java.util.Random;

import engine.audio.AudioData;

/** The game's sounds, synthesized in code so the repository needs no audio files. */
final class Sounds {
  private static final int RATE = 22050;

  private Sounds() {
  }

  /** The player's gun: a short zap falling in pitch. */
  static AudioData shot() {
    return zap(0.12f, 1400f, 500f, 0.35f);
  }

  /** The boss's gun: lower and longer, so it is told apart at once. */
  static AudioData bossShot() {
    return zap(0.3f, 520f, 160f, 0.45f);
  }

  /** A rock bursting: filtered noise with a low thump; {@code seconds} sets the size. */
  static AudioData explosion(float seconds, long seed) {
    final int n = (int) (RATE * seconds);
    final float[] out = new float[n];
    final var rnd = new Random(seed);
    float lp = 0f;
    double phase = 0;
    for (int i = 0; i < n; i++) {
      final float t = (float) i / RATE;
      final float decay = (float) Math.exp(-t * 5f / seconds);
      final float cutoff = 0.02f + 0.3f * decay;
      lp += cutoff * ((rnd.nextFloat() * 2f - 1f) - lp);
      phase += 2 * Math.PI * (45 + 60 * decay) / RATE;
      out[i] = (1.8f * lp + 0.5f * (float) Math.sin(phase)) * decay * attack(i, 30);
    }
    return AudioData.mono(RATE, out);
  }

  /** Metal struck: the boss is hit but still flying. */
  static AudioData clang() {
    final int n = (int) (RATE * 0.35f);
    final float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      final float t = (float) i / RATE;
      final double ring = Math.sin(2 * Math.PI * 610 * t) + 0.6 * Math.sin(2 * Math.PI * 1530 * t)
          + 0.3 * Math.sin(2 * Math.PI * 2390 * t);
      out[i] = 0.3f * (float) ring * (float) Math.exp(-t * 12) * attack(i, 15);
    }
    return AudioData.mono(RATE, out);
  }

  /** Two alternating tones: the boss has arrived. */
  static AudioData alarm() {
    final int n = (int) (RATE * 0.9f);
    final float[] out = new float[n];
    double phase = 0;
    for (int i = 0; i < n; i++) {
      final float t = (float) i / RATE;
      final double freq = ((int) (t * 8) % 2 == 0) ? 660 : 880;
      phase += 2 * Math.PI * freq / RATE;
      final float square = Math.sin(phase) >= 0 ? 1f : -1f;
      out[i] = 0.12f * square * Math.min(1f, (0.9f - t) * 10f) * attack(i, 40);
    }
    return AudioData.mono(RATE, out);
  }

  /** A rising chime: a new wave. */
  static AudioData chime() {
    final int n = (int) (RATE * 0.6f);
    final float[] out = new float[n];
    final float[] notes = { 523.25f, 659.25f, 783.99f };
    for (int i = 0; i < n; i++) {
      final float t = (float) i / RATE;
      float v = 0f;
      for (int k = 0; k < notes.length; k++) {
        final float start = k * 0.12f;
        if (t >= start) {
          v += (float) (Math.sin(2 * Math.PI * notes[k] * (t - start)) * Math.exp(-(t - start) * 6));
        }
      }
      out[i] = 0.25f * v * attack(i, 20);
    }
    return AudioData.mono(RATE, out);
  }

  private static AudioData zap(float seconds, float from, float to, float volume) {
    final int n = (int) (RATE * seconds);
    final float[] out = new float[n];
    double phase = 0;
    for (int i = 0; i < n; i++) {
      final float u = (float) i / n;
      phase += 2 * Math.PI * (from + (to - from) * u) / RATE;
      final float tone = (float) (Math.sin(phase) + 0.3 * Math.sin(3 * phase));
      out[i] = volume * tone * (1f - u) * (1f - u) * attack(i, 20);
    }
    return AudioData.mono(RATE, out);
  }

  /** A short fade-in against clicks. */
  private static float attack(int sample, int length) {
    return sample < length ? (float) sample / length : 1f;
  }
}
