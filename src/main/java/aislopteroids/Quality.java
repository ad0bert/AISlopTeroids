package aislopteroids;

import engine.render.RenderSettings;

/**
 * How much the graphics may cost, picked in the options for slower computers: the renderer's glow (bloom), edge
 * smoothing and exact blending of see-through things, and how many pieces, specks and sparks the bursts make and
 * whether explosions light up their surroundings. The sky's reflections are kept at every level: they are worked out
 * once at start and cost almost nothing per frame.
 */
enum Quality {
  LOW("Low", false, false, false, 0.35f, false),
  MEDIUM("Medium", true, true, false, 0.6f, true),
  HIGH("High", true, true, true, 1f, true);

  /** The bloom strength when glow is on (the engine's default). */
  static final float BLOOM = 0.08f;

  final String label;
  final boolean bloom;
  final boolean antiAliasing;
  final boolean exactBlending;
  /** The share of debris, dust, smoke, sparks and fire puffs that the bursts make. */
  final float effects;
  /** Whether an explosion lights up what is around it. */
  final boolean explosionLight;

  Quality(String label, boolean bloom, boolean antiAliasing, boolean exactBlending, float effects,
      boolean explosionLight) {
    this.label = label;
    this.bloom = bloom;
    this.antiAliasing = antiAliasing;
    this.exactBlending = exactBlending;
    this.effects = effects;
    this.explosionLight = explosionLight;
  }

  /** How many of {@code count} things a burst makes at this level: at least one. */
  int scale(int count) {
    return Math.max(1, Math.round(count * this.effects));
  }

  void apply(RenderSettings settings) {
    settings.bloomStrength = this.bloom ? BLOOM : 0f;
    settings.antiAliasing = this.antiAliasing;
    settings.orderIndependentTransparency = this.exactBlending;
  }

  /** The next level, round from High to Low. */
  Quality next() {
    return values()[(ordinal() + 1) % values().length];
  }

  /** The level with this name (as {@link #name()}), or High. */
  static Quality parse(String name) {
    for (Quality q : values()) {
      if (q.name().equalsIgnoreCase(name)) {
        return q;
      }
    }
    return HIGH;
  }
}
