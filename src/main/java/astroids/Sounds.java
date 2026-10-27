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

  /** A quick rising arpeggio, twice: an extra ship. */
  static AudioData extraLife() {
    final int n = (int) (RATE * 0.75f);
    final float[] out = new float[n];
    final float[] notes = { 659.25f, 783.99f, 987.77f, 1318.51f };
    double phase = 0;
    for (int i = 0; i < n; i++) {
      final float t = (float) i / RATE;
      final int step = (int) (t / 0.0625f);
      final float freq = notes[step % notes.length] * (step >= notes.length ? 1f : 0.5f);
      phase += 2 * Math.PI * freq / RATE;
      final float within = t - step * 0.0625f;
      out[i] = 0.18f * (float) (Math.sin(phase) + 0.25 * Math.sin(2 * phase)) * (float) Math.exp(-within * 18)
          * attack(i, 20);
    }
    return AudioData.mono(RATE, out);
  }

  /** A sweep up through rushing noise and down again: the hyperspace jump. */
  static AudioData warp() {
    final int n = (int) (RATE * 0.7f);
    final float[] out = new float[n];
    final var rnd = new Random(4L);
    float lp = 0f;
    double phase = 0;
    for (int i = 0; i < n; i++) {
      final float u = (float) i / n;
      final float rise = (float) Math.sin(Math.PI * u); // up, then down
      phase += 2 * Math.PI * (180 + 1400 * rise * rise) / RATE;
      lp += (0.05f + 0.4f * rise) * ((rnd.nextFloat() * 2f - 1f) - lp);
      out[i] = (0.22f * (float) Math.sin(phase) + 0.35f * lp) * rise * attack(i, 30);
    }
    return AudioData.mono(RATE, out);
  }

  // ----- ambience: loops that join without a click (every tone and swell repeats a whole number of times per loop)

  /** One tone of a drone: frequency, loudness, and how many slow swells per loop (with a phase, in loops). */
  private record Tone(double freq, float level, int swells, double phase) {
  }

  /**
   * The title screen: a warm, slowly breathing pad over Am, F, C and G with a soft synth arpeggio low inside it, its
   * echo going from side to side, and a little space wind. 90 bpm, stereo, 16 seconds (four chords of 4 seconds).
   */
  static AudioData titleAmbience() {
    final float seconds = 16f;
    final int n = (int) (RATE * seconds);
    final double chordSeconds = 4.0;
    final double eighth = 60.0 / 90 / 2;
    // root, third, fifth, octave
    final double[][] chords = { { 220, 261.63, 329.63, 440 }, { 174.61, 220, 261.63, 349.23 },
        { 261.63, 329.63, 392, 523.25 }, { 196, 246.94, 293.66, 392 } };
    final float[][] pad = new float[2][n];
    final float[][] arp = new float[2][n];
    final double[][] detune = { { 1, 1.003 }, { 0.998, 1.004 } }; // a slight chorus, different on each side
    for (int k = 0; k < chords.length; k++) {
      final double[] chord = chords[k];
      final double start = k * chordSeconds;
      // the pad: the chord an octave down, each tone on two slightly detuned voices, fading into the next chord
      final double length = chordSeconds + 1.6;
      for (int c = 0; c < 2; c++) {
        final float[] sound = new float[(int) (RATE * length)];
        for (int i = 0; i < sound.length; i++) {
          final double t = (double) i / RATE;
          final double env = Math.sin(Math.PI * Math.min(1, t / 1.6) / 2) * Math.sin(Math.PI
              * Math.min(1, (length - t) / 1.6) / 2);
          double v = 0;
          for (double d : detune[c]) {
            for (int tone = 0; tone < 3; tone++) {
              v += softSaw(2 * Math.PI * chord[tone] / 2 * d * t);
            }
            v += 0.5 * softSaw(2 * Math.PI * chord[0] / 4 * d * t); // a soft bass under it
          }
          sound[i] = (float) (v * env);
        }
        addLooped(pad[c], (int) Math.round((start - 0.8) * RATE), sound, 1f);
      }
      // the arpeggio, low: up and down the chord in eighths, with a ping-pong echo
      final int[] pattern = { 0, 1, 2, 3, 2, 1 };
      for (int step = 0; step < 12; step++) {
        final float[] note = pluck(chord[pattern[step % pattern.length]] / 2);
        final int at = (int) Math.round((start + step * eighth) * RATE);
        addLooped(arp[0], at, note, 1f);
        addLooped(arp[1], at, note, 1f);
        float level = 1f;
        for (int e = 1; e <= 4; e++) {
          level *= 0.42f;
          addLooped(arp[e % 2], at + (int) Math.round(e * 0.5 * RATE), note, level);
        }
      }
    }
    final float[][] out = new float[2][];
    for (int c = 0; c < 2; c++) {
      final int side = c;
      // the pad breathes: a low-pass opening and closing once per loop (a little later on the right)
      final float[] warm = lowpassLoop(pad[c],
          u -> 0.05 + 0.1 * (0.5 + 0.5 * Math.sin(2 * Math.PI * (u + 0.1 * side))));
      final float[] soft = lowpassLoop(arp[c], u -> 0.3);
      final float[] wind = unitRms(loopNoise(n, 20L + c, u -> 0.08f));
      final float padLevel = rms(warm);
      final float arpLevel = 0.55f * padLevel / Math.max(1e-6f, rms(soft));
      out[c] = new float[n];
      for (int i = 0; i < n; i++) {
        out[c][i] = warm[i] + arpLevel * soft[i] + 0.15f * padLevel * wind[i];
      }
    }
    return stereo(out[0], out[1], 0.45f);
  }

  /** One period of a soft sawtooth (eight harmonics, falling off quickly), looked up instead of summed every sample. */
  private static final float[] SOFT_SAW = new float[4096];

  static {
    for (int i = 0; i < SOFT_SAW.length; i++) {
      final double phase = 2 * Math.PI * i / SOFT_SAW.length;
      double v = 0;
      for (int h = 1; h <= 8; h++) {
        v += Math.sin(h * phase) / Math.pow(h, 1.6);
      }
      SOFT_SAW[i] = (float) v;
    }
  }

  private static double softSaw(double phase) {
    final double x = phase / (2 * Math.PI) * SOFT_SAW.length;
    final double floor = Math.floor(x);
    final int i = (int) (floor % SOFT_SAW.length);
    final double f = x - floor;
    return SOFT_SAW[i] + f * (SOFT_SAW[(i + 1) % SOFT_SAW.length] - SOFT_SAW[i]);
  }

  /** A soft synth pluck: a quick attack and a decay of about half a second. */
  private static float[] pluck(double f) {
    final float[] out = new float[(int) (RATE * 1.2)];
    for (int i = 0; i < out.length; i++) {
      final double t = (double) i / RATE;
      final double p = 2 * Math.PI * f * t;
      final double env = Math.min(1, t / 0.005) * Math.exp(-t * 5.5);
      out[i] = (float) ((Math.sin(p) + 0.3 * Math.sin(2 * p) + 0.12 * Math.sin(3 * p)) * env);
    }
    return out;
  }

  /** Adds a sound into a loop at {@code at}, wrapping round its end. */
  private static void addLooped(float[] loop, int at, float[] sound, float level) {
    for (int i = 0; i < sound.length; i++) {
      loop[Math.floorMod(at + i, loop.length)] += sound[i] * level;
    }
  }

  /**
   * A two-pole low-pass over a loop, run twice round so that the filter has settled where the loop starts: the output
   * joins as cleanly as the input. The cutoff (0..1) may follow the loop position.
   */
  private static float[] lowpassLoop(float[] in, java.util.function.DoubleUnaryOperator cutoff) {
    final int n = in.length;
    final float[] out = new float[n];
    double a = 0;
    double b = 0;
    for (int pass = 0; pass < 2; pass++) {
      for (int i = 0; i < n; i++) {
        final double c = cutoff.applyAsDouble((double) i / n);
        a += c * (in[i] - a);
        b += c * (a - b);
        out[i] = (float) b;
      }
    }
    return out;
  }

  private static float rms(float[] samples) {
    double sum = 0;
    for (float s : samples) {
      sum += s * s;
    }
    return (float) Math.sqrt(sum / samples.length);
  }

  /** In the game: darker and lower, with more space wind blowing through it. Stereo, 20 seconds. */
  static AudioData playAmbience() {
    final float seconds = 20f;
    final Tone[] left = { new Tone(41.2, 0.20f, 1, 0.0), new Tone(61.8, 0.14f, 2, 0.4), new Tone(82.4, 0.12f, 3, 0.2),
        new Tone(98, 0.06f, 1, 0.7), new Tone(123.6, 0.06f, 2, 0.15), new Tone(164.8, 0.035f, 3, 0.55),
        new Tone(246.95, 0.02f, 4, 0.85), new Tone(493.9, 0.008f, 5, 0.3) };
    final Tone[] right = { new Tone(41.25, 0.20f, 1, 0.5), new Tone(61.8, 0.14f, 2, 0.9),
        new Tone(82.45, 0.12f, 3, 0.7),
        new Tone(98, 0.06f, 1, 0.2), new Tone(123.6, 0.06f, 2, 0.65), new Tone(164.85, 0.035f, 3, 0.05),
        new Tone(246.95, 0.02f, 4, 0.35), new Tone(493.9, 0.008f, 5, 0.8) };
    return stereo(drone(seconds, left, 0.36f, 0.08f, 7L), drone(seconds, right, 0.36f, 0.08f, 8L), 0.45f);
  }

  /**
   * The ship's engine: a steady rumble with a little hiss and a quiet hum under it, without swells, 4 seconds; the game
   * sets its pitch and volume by speed.
   */
  static AudioData engineHum() {
    final int n = RATE * 4;
    final float[] rumble = unitRms(loopNoise(n, 9L, u -> 0.05f));
    final float[] hiss = unitRms(loopNoise(n, 10L, u -> 0.3f));
    final float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      final double t = (double) i / RATE;
      double hum = 0;
      for (int h = 1; h <= 5; h++) {
        hum += Math.sin(2 * Math.PI * 55 * h * t) / h;
      }
      out[i] = rumble[i] + 0.2f * hiss[i] + 0.25f * (float) hum;
    }
    return AudioData.mono(RATE, normalize(out, 0.5f));
  }

  /** While an enemy ship is about: a low, slightly sour throb, two beats a second. 4 seconds. */
  static AudioData danger() {
    final float seconds = 4f;
    final int n = (int) (RATE * seconds);
    final float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      final double t = (double) i / RATE;
      final double beat = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * 2 * t - Math.PI / 2), 3); // 2 per second
      final double tone = Math.sin(2 * Math.PI * 73.5 * t) + 0.6 * Math.sin(2 * Math.PI * 77.75 * t)
          + 0.7 * Math.sin(2 * Math.PI * 147 * t) + 0.5 * Math.sin(2 * Math.PI * 220.5 * t)
          + 0.3 * Math.sin(2 * Math.PI * 311 * t) + 0.12 * Math.sin(2 * Math.PI * 441 * t);
      out[i] = (float) (tone * beat);
    }
    return AudioData.mono(RATE, normalize(out, 0.5f));
  }

  /**
   * Tones that swell and fade slowly, plus filtered noise (a wind whose brightness drifts). The tones repeat exactly
   * over the loop; the noise is made a little longer and its end faded into its start.
   */
  private static float[] drone(float seconds, Tone[] tones, float wind, float brightness, long seed) {
    final int n = (int) (RATE * seconds);
    final float[] noise = loopNoise(n, seed, u -> brightness * (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * 2 * u)));
    final float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      final double t = (double) i / RATE;
      final double u = (double) i / n;
      double v = 0;
      for (Tone tone : tones) {
        final double swell = 0.55 + 0.45 * Math.sin(2 * Math.PI * (tone.swells() * u + tone.phase()));
        final double phase = 2 * Math.PI * tone.freq() * t;
        // soft overtones, so that low tones are heard on small speakers too
        v += tone.level() * swell * (Math.sin(phase) + 0.35 * Math.sin(2 * phase) + 0.18 * Math.sin(3 * phase)
            + 0.08 * Math.sin(4 * phase));
      }
      out[i] = (float) v + wind * 6f * noise[i];
    }
    return out;
  }

  /**
   * {@code n} samples of noise through a two-pole low-pass whose cutoff (0..1) may change over the loop ({@code u} from
   * 0 to 1), made to loop: it is made a second longer and its end faded into its start (at equal power, so the level
   * does not dip there).
   */
  private static float[] loopNoise(int n, long seed, java.util.function.DoubleFunction<Float> cutoff) {
    final int fade = Math.min(RATE, n / 2);
    final float[] raw = new float[n + fade];
    final var rnd = new Random(seed);
    float lp = 0f;
    float lp2 = 0f;
    for (int i = 0; i < raw.length; i++) {
      final float c = 0.002f + cutoff.apply((double) (i % n) / n);
      lp += c * ((rnd.nextFloat() * 2f - 1f) - lp);
      lp2 += c * (lp - lp2);
      raw[i] = lp2;
    }
    final float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      if (i < fade) { // the loop's start blends in from the noise just after its end
        final double k = (double) i / fade;
        out[i] = (float) (raw[i] * Math.sqrt(k) + raw[i + n] * Math.sqrt(1 - k));
      } else {
        out[i] = raw[i];
      }
    }
    return out;
  }

  /** Scales to a root-mean-square level of 1, so that layers can be mixed by their loudness. */
  private static float[] unitRms(float[] samples) {
    double sum = 0;
    for (float s : samples) {
      sum += s * s;
    }
    final float scale = (float) (1 / Math.sqrt(Math.max(1e-12, sum / samples.length)));
    for (int i = 0; i < samples.length; i++) {
      samples[i] *= scale;
    }
    return samples;
  }

  /** Scales so that the loudest sample is {@code peak}. */
  private static float[] normalize(float[] samples, float peak) {
    float max = 1e-6f;
    for (float s : samples) {
      max = Math.max(max, Math.abs(s));
    }
    for (int i = 0; i < samples.length; i++) {
      samples[i] *= peak / max;
    }
    return samples;
  }

  private static AudioData stereo(float[] left, float[] right, float peak) {
    float max = 1e-6f;
    for (int i = 0; i < left.length; i++) {
      max = Math.max(max, Math.max(Math.abs(left[i]), Math.abs(right[i])));
    }
    final short[] out = new short[left.length * 2];
    for (int i = 0; i < left.length; i++) {
      out[2 * i] = (short) Math.round(left[i] * peak / max * Short.MAX_VALUE);
      out[2 * i + 1] = (short) Math.round(right[i] * peak / max * Short.MAX_VALUE);
    }
    return new AudioData(RATE, 2, out);
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
