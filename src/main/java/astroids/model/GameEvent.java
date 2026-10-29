package astroids.model;

import org.joml.Vector3f;

/** Things that happened in a step, for sounds, effects and messages. Positions are copies. */
public sealed interface GameEvent {
  record WaveStarted(int wave) implements GameEvent {
  }

  record Shot(Vector3f position) implements GameEvent {
  }

  record BossShot(Vector3f position) implements GameEvent {
  }

  /** Also how the rock was moving: its pieces fly on with it. */
  record AsteroidDestroyed(Vector3f position, Asteroid.Size size, Vector3f velocity) implements GameEvent {
  }

  /** Two rocks bounced off each other, here; {@code strength} is from 0 (a graze) to 1 (head-on at full speed). */
  record AsteroidsBumped(Vector3f position, float strength) implements GameEvent {
  }

  /** Fire was held with the gun's battery flat: nothing came out. */
  record BatteryEmpty() implements GameEvent {
  }

  record BossAppeared(Vector3f position, Boss.Kind kind) implements GameEvent {
  }

  record BossHit(Vector3f position) implements GameEvent {
  }

  record BossDestroyed(Vector3f position, Boss.Kind kind, int points, Vector3f velocity) implements GameEvent {
  }

  record BossLeft() implements GameEvent {
  }

  record ShipDestroyed(Vector3f position) implements GameEvent {
  }

  record ShipRespawned() implements GameEvent {
  }

  /** The score passed another {@code ship.extraLifeEvery} points. */
  record ExtraLife(int lives) implements GameEvent {
  }

  /** The ship jumped through hyperspace. */
  record Hyperspace(Vector3f from, Vector3f to) implements GameEvent {
  }

  record GameOver(int score) implements GameEvent {
  }
}
