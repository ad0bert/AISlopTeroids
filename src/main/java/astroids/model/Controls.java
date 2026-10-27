package astroids.model;

import org.joml.Vector3f;

/**
 * What the player does in one step, already in world space: {@code thrust} is the direction to accelerate in (length up
 * to 1), {@code aim} the unit direction the ship looks and shoots in.
 */
public record Controls(Vector3f thrust, Vector3f aim, boolean fire) {
  public static final Controls NONE = new Controls(new Vector3f(), new Vector3f(0f, 0f, -1f), false);
}
