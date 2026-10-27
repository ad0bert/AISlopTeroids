package astroids;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import engine.audio.AudioBus;
import engine.audio.AudioClip;
import engine.audio.AudioSource;
import engine.core.Engine;
import engine.ecs.Entity;
import engine.ecs.Scene;
import engine.render.Camera;
import engine.render.GpuMesh;
import engine.render.Light;
import engine.render.Material;
import engine.render.MeshFactory;
import engine.render.MeshRenderer;
import engine.audio.AudioListener;

/**
 * Everything the game draws and plays, made once at start-up from code (no asset files): a few generated meshes, flat
 * colours, glowing shots and synthesized sounds. Created outside any level, so it lives as long as the game.
 */
final class Art {
  /** Rock shapes, all different; each asteroid gets one ({@link astroids.model.Asteroid#variant}). */
  final GpuMesh[] rocks = new GpuMesh[ROCK_SHAPES];
  /** Broken-off chips for the debris of a rock that is hit. */
  final GpuMesh[] chips = new GpuMesh[8];
  /** A ball of points: the dust of a rock that is hit, scaled up as it spreads. */
  final GpuMesh dust;
  final GpuMesh ball;
  final GpuMesh cube;
  final GpuMesh stars;
  final GpuMesh brightStars;

  /** How many different rock shapes there are. */
  static final int ROCK_SHAPES = 12;
  /** The seed of the first rock shape; the others follow it. */
  static final long ROCK_SEED = 1000L;

  final Material rockMaterial = new Material().color(0.62f, 0.58f, 0.54f, 1f);
  final Material smallRockMaterial = new Material().color(0.74f, 0.64f, 0.54f, 1f);
  final Material shotMaterial = glowing(1.8f, 1.5f, 0.5f);
  final Material bossShotMaterial = glowing(2.2f, 0.25f, 0.7f);
  final Material bossHull = new Material().color(0.55f, 0.57f, 0.62f, 1f);
  final Material bossDome = glowing(1.6f, 0.2f, 0.1f);
  final Material scoutHull = new Material().color(0.3f, 0.32f, 0.36f, 1f);
  final Material scoutDome = glowing(2f, 1.2f, 0.1f);
  final Material fire = glowing(2.4f, 1.1f, 0.2f);
  final Material starMaterial = new Material().color(0.75f, 0.78f, 0.85f, 1f).unlit();
  final Material brightStarMaterial = new Material().color(1f, 1f, 1f, 1f).unlit();

  final AudioClip shot;
  final AudioClip bossShot;
  final AudioClip bigBoom;
  final AudioClip smallBoom;
  final AudioClip shipBoom;
  final AudioClip clang;
  final AudioClip alarm;
  final AudioClip chime;
  final AudioClip extraLife;
  final AudioClip warp;
  final AudioClip titleAmbience;
  final AudioClip playAmbience;
  final AudioClip engineHum;
  final AudioClip danger;

  Art(Engine engine) {
    final var res = engine.resources();
    for (int i = 0; i < this.rocks.length; i++) {
      this.rocks[i] = res.mesh("gen:rock-" + i, RockArt.mesh(ROCK_SEED + i));
    }
    for (int i = 0; i < this.chips.length; i++) {
      this.chips[i] = res.mesh("gen:chip-" + i, RockArt.chip(2000L + i));
    }
    this.dust = res.mesh("gen:dust", MeshFactory.points(RockArt.dust(260, 3L)));
    // one rock surface for all of them: a colour texture and a bump map from the same heights
    final float[] heights = RockArt.heights(RockArt.TEXTURE_SIZE, 7L);
    final var rockTexture = res.texture("gen:rock-albedo", RockArt.albedo(heights, RockArt.TEXTURE_SIZE, 8L));
    final var rockBumps = res.texture("gen:rock-normals", RockArt.normals(heights, RockArt.TEXTURE_SIZE));
    for (var m : List.of(this.rockMaterial, this.smallRockMaterial)) {
      m.texture = rockTexture;
      m.normalMap = rockBumps;
      m.uvScale.set(2f, 1f); // twice round the rock, once from pole to pole: about square
    }
    this.ball = res.mesh(MeshFactory.sphere(1f, 10, 6));
    this.cube = res.mesh(MeshFactory.cube(1f));
    this.stars = res.mesh(MeshFactory.points(starField(1400, 1L)));
    this.brightStars = res.mesh(MeshFactory.points(starField(250, 2L)));
    this.rockMaterial.specular = 0.05f;
    this.smallRockMaterial.specular = 0.05f;
    this.bossHull.specular = 0.8f;
    this.bossHull.shininess = 64f;
    this.scoutHull.specular = 0.9f;
    this.scoutHull.shininess = 96f;
    this.starMaterial.pointSize = 1.5f;
    this.brightStarMaterial.pointSize = 2.5f;

    this.shot = res.sound("gen:shot", Sounds.shot());
    this.bossShot = res.sound("gen:boss-shot", Sounds.bossShot());
    this.bigBoom = res.sound("gen:big-boom", Sounds.explosion(0.9f, 1L));
    this.smallBoom = res.sound("gen:small-boom", Sounds.explosion(0.45f, 2L));
    this.shipBoom = res.sound("gen:ship-boom", Sounds.explosion(1.6f, 3L));
    this.clang = res.sound("gen:clang", Sounds.clang());
    this.alarm = res.sound("gen:alarm", Sounds.alarm());
    this.chime = res.sound("gen:chime", Sounds.chime());
    this.extraLife = res.sound("gen:extra-life", Sounds.extraLife());
    this.warp = res.sound("gen:warp", Sounds.warp());
    this.titleAmbience = res.sound("gen:title-ambience", Sounds.titleAmbience());
    this.playAmbience = res.sound("gen:play-ambience", Sounds.playAmbience());
    this.engineHum = res.sound("gen:engine-hum", Sounds.engineHum());
    this.danger = res.sound("gen:danger", Sounds.danger());

    final var settings = engine.backend().settings();
    settings.sky = null;
    settings.clearColor.set(0.005f, 0.006f, 0.02f);
  }

  /** Its own material for one dust cloud (it fades on its own): unlit, see-through rock colour. */
  Material dustMaterial(boolean big) {
    final var m = new Material().color(big ? 0.62f : 0.70f, big ? 0.58f : 0.62f, big ? 0.54f : 0.52f, 0.8f).unlit()
        .transparent();
    m.pointSize = 3f;
    return m;
  }

  /** Black with a bright emissive colour: shots and engine glow, picked up by the bloom. */
  private static Material glowing(float r, float g, float b) {
    final var m = new Material().color(0f, 0f, 0f, 1f);
    m.emissive.set(r, g, b);
    m.specular = 0f;
    return m;
  }

  /**
   * The camera, lights and stars every screen shares; returns the camera's entity. The stars are far away and follow
   * the camera, so they look infinitely distant and give a sense of turning.
   */
  Entity addBackdrop(Scene scene) {
    final var camera = new Entity("camera");
    final var cam = camera.add(new Camera());
    cam.fovDegrees = 75f;
    cam.far = 600f;
    camera.add(new AudioListener());
    scene.add(camera);

    final var sun = new Entity("sun");
    sun.transform().setEulerDegrees(-35f, -40f, 0f);
    final var light = sun.add(new Light());
    light.color.set(1f, 0.96f, 0.9f);
    light.intensity = 1.6f;
    light.ambient.set(0.12f, 0.12f, 0.16f);
    light.castShadows = false;
    scene.add(sun);

    final var fill = new Entity("fill");
    fill.transform().setEulerDegrees(30f, 140f, 0f);
    final var fillLight = fill.add(new Light());
    fillLight.color.set(0.35f, 0.45f, 0.8f);
    fillLight.intensity = 0.35f;
    fillLight.ambient.set(0f);
    fillLight.castShadows = false;
    scene.add(fill);

    for (var layer : List.of(new Entity("stars").add(new MeshRenderer(this.stars, this.starMaterial)).entity(),
        new Entity("bright-stars").add(new MeshRenderer(this.brightStars, this.brightStarMaterial)).entity())) {
      layer.add(new Follow(camera));
      scene.add(layer);
    }
    return camera;
  }

  /**
   * A sound that loops for as long as the scene lasts, not positioned (it is everywhere): ambience on the music bus,
   * which the Ambience slider sets. Change the returned source's volume and pitch to make it follow the game.
   */
  AudioSource addLoop(Scene scene, String name, AudioClip clip, float volume) {
    final var e = new Entity(name);
    final var source = e.add(new AudioSource(clip));
    source.loop = true;
    source.spatial = false;
    source.bus = AudioBus.MUSIC;
    source.volume = volume;
    source.priority = 100; // never pushed out by explosions
    source.playOnStart = true;
    scene.add(e);
    return source;
  }

  /** Points spread evenly over a far sphere. */
  private static List<Vector3f> starField(int count, long seed) {
    final var rnd = new Random(seed);
    final List<Vector3f> points = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      final float z = rnd.nextFloat() * 2f - 1f;
      final float a = rnd.nextFloat() * (float) (2 * Math.PI);
      final float r = (float) Math.sqrt(1f - z * z);
      points.add(new Vector3f(r * (float) Math.cos(a), r * (float) Math.sin(a), z).mul(450f));
    }
    return points;
  }
}
