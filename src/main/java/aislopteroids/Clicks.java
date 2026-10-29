package aislopteroids;

import engine.input.InputState;

/** Notices new left clicks: the input counts presses since the start, this remembers the count it last saw. */
final class Clicks {
  private final InputState input;
  private int seen;

  Clicks(InputState input) {
    this.input = input;
    this.seen = input.mousePresses(InputState.BUTTON_LEFT);
  }

  /** Whether the left button was pressed since the last call. */
  boolean clicked() {
    final int now = this.input.mousePresses(InputState.BUTTON_LEFT);
    final boolean clicked = now != this.seen;
    this.seen = now;
    return clicked;
  }
}
