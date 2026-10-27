package astroids;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import engine.audio.AudioClip;
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
  final GpuMesh rock;
  final GpuMesh ball;
  final GpuMesh cube;
  final GpuMesh stars;
  final GpuMesh brightStars;

  final Material rockMaterial = new Material().color(0.48f, 0.44f, 0.40f, 1f);
  final Material smallRockMaterial = new Material().color(0.58f, 0.50f, 0.42f, 1f);
  final Material shotMaterial = glowing(1.8f, 1.5f, 0.5f);
  final Material bossShotMaterial = glowing(2.2f, 0.25f, 0.7f);
  final Material bossHull = new Material().color(0.55f, 0.57f, 0.62f, 1f);
  final Material bossDome = glowing(1.6f, 0.2f, 0.1f);
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

  Art(Engine engine) {
    final var res = engine.resources();
    this.rock = res.mesh(MeshFactory.sphere(1f, 9, 6)); // few faces: rough and simple
    this.ball = res.mesh(MeshFactory.sphere(1f, 10, 6));
    this.cube = res.mesh(MeshFactory.cube(1f));
    this.stars = res.mesh(MeshFactory.points(starField(1400, 1L)));
    this.brightStars = res.mesh(MeshFactory.points(starField(250, 2L)));
    this.rockMaterial.specular = 0.05f;
    this.smallRockMaterial.specular = 0.05f;
    this.bossHull.specular = 0.8f;
    this.bossHull.shininess = 64f;
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

    final var settings = engine.backend().settings();
    settings.sky = null;
    settings.clearColor.set(0.005f, 0.006f, 0.02f);
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
