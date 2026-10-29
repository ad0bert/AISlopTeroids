package aislopteroids;

/**
 * Where the player watches from: inside the cockpit, from the pilot's seat with nothing around (a clear view), or
 * from behind the ship. Picked in the options or switched in the game with V.
 */
enum View {
  COCKPIT("Cockpit"),
  CLEAR("No cockpit"),
  CHASE("Behind the ship");

  final String label;

  View(String label) {
    this.label = label;
  }

  /** The next view, round from the last to the first. */
  View next() {
    return values()[(ordinal() + 1) % values().length];
  }

  /** Whether the camera is at the pilot's seat (the cockpit, or none). */
  boolean firstPerson() {
    return this != CHASE;
  }

  /** The view with this name (as {@link #name()}), or {@code fallback}. */
  static View parse(String name, View fallback) {
    for (View v : values()) {
      if (v.name().equalsIgnoreCase(name)) {
        return v;
      }
    }
    return fallback;
  }
}
