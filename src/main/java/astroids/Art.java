package astroids;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Quaternionf;
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
import engine.render.Sky;
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
  // the enemy ships: polished metal that reflects the space around it, glossy paint, glowing glass and lights
  final Material bossHull = new Material().color(0.92f, 0.93f, 0.96f, 1f).pbr(0.85f, 0.28f);
  final Material bossPaint = new Material().color(0.55f, 0.03f, 0.02f, 1f).pbr(0f, 0.22f);
  final Material bossDome = glass(0.45f, 0.03f, 0.02f, 0.35f, 0.04f, 0.02f);
  final Material bossLight = glowing(2.6f, 0.35f, 0.2f);
  final Material scoutHull = new Material().color(0.85f, 0.52f, 0.05f, 1f).pbr(0f, 0.18f);
  final Material scoutFin = new Material().color(0.85f, 0.86f, 0.9f, 1f).pbr(0.85f, 0.25f);
  final Material scoutDome = glass(0.4f, 0.28f, 0.03f, 0.45f, 0.3f, 0.03f);
  final Material scoutLight = glowing(2.6f, 1.9f, 0.3f);
  final Material whiteLight = glowing(2.4f, 2.4f, 2.3f);
  final Material engineGlow = glowing(2.4f, 0.8f, 0.25f);

  /** One piece of a ship: where it sits on the ship (which has a scale of its radius), and how it looks. */
  record Part(GpuMesh mesh, Material material, Vector3f position, Quaternionf rotation, Vector3f scale) {
  }

  /** The big enemy ship and the scout, as parts. */
  final List<Part> bossParts = new ArrayList<>();
  final List<Part> scoutParts = new ArrayList<>();
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
      m.ambient.set(2.5f); // space gives little fill light: keep the shadowed side of a rock visible
    }
    this.ball = res.mesh(MeshFactory.sphere(1f, 10, 6));
    this.cube = res.mesh(MeshFactory.cube(1f));
    this.stars = res.mesh(MeshFactory.points(starField(1400, 1L)));
    this.brightStars = res.mesh(MeshFactory.points(starField(250, 2L)));
    this.rockMaterial.specular = 0.05f;
    this.smallRockMaterial.specular = 0.05f;
    this.bossPaint.reflectance = 0.06f;
    this.scoutHull.reflectance = 0.06f;
    // the sky is kept dark behind the game; the ships take more of its light, so their polish shows
    for (var m : List.of(this.bossHull, this.bossPaint, this.scoutFin)) {
      m.ambient.set(3f);
    }
    this.scoutHull.ambient.set(1.5f); // paint: its colour takes the sky's light too, not only its gloss
    buildShips(res);
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
    // space all round: dark, but with a sun, a faint nebula and stars for the polished hulls to reflect
    final var sunward = new Entity("sun direction");
    sunward.transform().setEulerDegrees(SUN_PITCH, SUN_YAW, 0f);
    final var toSun = sunward.transform().forward(new Vector3f()).negate();
    final var toPlanet = new Vector3f(-toSun.x, -0.6f, -toSun.z); // low, on the far side from the sun
    settings.sky = Sky.image(ShipArt.spaceSky(1024, 512, toSun, toPlanet, 11L));
    settings.clearColor.set(0.005f, 0.006f, 0.02f);
  }

  /** Its own material for one dust cloud (it fades on its own): unlit, see-through rock colour. */
  Material dustMaterial(boolean big) {
    final var m = new Material().color(big ? 0.62f : 0.70f, big ? 0.58f : 0.62f, big ? 0.54f : 0.52f, 0.8f).unlit()
        .transparent();
    m.pointSize = 3f;
    return m;
  }

  /** Where the sunlight comes from (the light's rotation), and so where the sky has its sun. */
  private static final float SUN_PITCH = -35f;
  private static final float SUN_YAW = -40f;

  private void buildShips(engine.resources.ResourceManager res) {
    final var saucer = res.mesh("gen:saucer", ShipArt.lathe(ShipArt.SAUCER, 40));
    final var band = res.mesh("gen:rim-band", ShipArt.lathe(ShipArt.RIM_BAND, 40));
    final var dome = res.mesh("gen:dome", ShipArt.lathe(ShipArt.DOME, 28));
    final var scout = res.mesh("gen:scout", ShipArt.lathe(ShipArt.SCOUT, 36));
    final var plate = res.mesh("gen:plate", ShipArt.lathe(ShipArt.PLATE, 24));
    final var none = new Quaternionf();
    // the big one: a chrome saucer, a red band round its rim, a red glass dome, rim lights and an engine below
    this.bossParts.add(new Part(saucer, this.bossHull, new Vector3f(), none, new Vector3f(1f)));
    this.bossParts.add(new Part(band, this.bossPaint, new Vector3f(), none, new Vector3f(1f)));
    this.bossParts.add(new Part(dome, this.bossDome, new Vector3f(0f, 0.22f, 0f), none, new Vector3f(1f)));
    this.bossParts.add(new Part(plate, this.engineGlow, new Vector3f(0f, -0.305f, 0f), none,
        new Vector3f(0.3f, 1f, 0.3f)));
    final int lights = 12;
    for (int i = 0; i < lights; i++) {
      final double a = 2 * Math.PI * i / lights;
      this.bossParts.add(new Part(this.ball, i % 2 == 0 ? this.bossLight : this.whiteLight,
          new Vector3f((float) Math.cos(a) * 0.92f, -0.075f, (float) -Math.sin(a) * 0.92f), none,
          new Vector3f(0.045f)));
    }
    // the scout: a flatter gold hull with three swept fins, a yellow canopy and yellow lights
    this.scoutParts.add(new Part(scout, this.scoutHull, new Vector3f(), none, new Vector3f(1f)));
    this.scoutParts.add(new Part(dome, this.scoutDome, new Vector3f(0f, 0.15f, 0f), none,
        new Vector3f(0.8f, 0.9f, 0.8f)));
    this.scoutParts.add(new Part(plate, this.engineGlow, new Vector3f(0f, -0.175f, 0f), none,
        new Vector3f(0.28f, 1f, 0.28f)));
    for (int i = 0; i < 3; i++) {
      final float a = (float) (2 * Math.PI * i / 3);
      // swept back from the rim and tilted up a little
      final var turn = new Quaternionf().rotateY(a + 0.45f).rotateZ((float) Math.toRadians(10));
      final var at = new Quaternionf().rotateY(a).transform(new Vector3f(0.95f, 0.03f, 0f));
      this.scoutParts.add(new Part(this.cube, this.scoutFin, at, turn, new Vector3f(0.34f, 0.03f, 0.1f)));
    }
    for (int i = 0; i < 6; i++) {
      final double a = 2 * Math.PI * (i + 0.5) / 6;
      this.scoutParts.add(new Part(this.ball, this.scoutLight,
          new Vector3f((float) Math.cos(a) * 0.98f, 0.01f, (float) -Math.sin(a) * 0.98f), none, new Vector3f(0.04f)));
    }
  }

  /** Tinted glass that glows from inside and still shows a reflection. */
  private static Material glass(float r, float g, float b, float er, float eg, float eb) {
    final var m = new Material().color(r, g, b, 1f).pbr(0f, 0.05f);
    m.emissive.set(er, eg, eb);
    m.reflectance = 0.08f;
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
    sun.transform().setEulerDegrees(SUN_PITCH, SUN_YAW, 0f);
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
