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
  /** Torn-off pieces of hull for the wreckage of an enemy ship. */
  final GpuMesh[] shards = new GpuMesh[6];
  /** A ball of points: the dust of a rock that is hit, scaled up as it spreads. */
  final GpuMesh dust;
  final GpuMesh ball;
  final GpuMesh cube;
  /** A smooth ball, for fireballs (seen close up, the facets of {@link #ball} would show). */
  final GpuMesh smoothBall;
  final GpuMesh stars;
  final GpuMesh brightStars;

  /** How many different rock shapes there are. */
  static final int ROCK_SHAPES = 12;
  /** The seed of the first rock shape; the others follow it. */
  static final long ROCK_SEED = 1000L;

  final Material rockMaterial = new Material().color(0.62f, 0.58f, 0.54f, 1f);
  final Material smallRockMaterial = new Material().color(0.74f, 0.64f, 0.54f, 1f);
  // the shots: laser bolts, a white-hot core in a sheath of coloured light (amber for the player, magenta for the
  // enemy), so they bloom like light rather than look like solid sticks
  final Material shotMaterial = glowing(3.2f, 2.9f, 2.2f);
  final Material shotGlow = holo(1.5f, 0.75f, 0.12f);
  final Material bossShotMaterial = glowing(3f, 1.4f, 2.2f);
  final Material bossShotGlow = holo(1.4f, 0.08f, 0.55f);
  // the enemy ships: polished metal that reflects the space around it, glossy paint, glowing glass and lights
  final Material bossHull = new Material().color(0.92f, 0.93f, 0.96f, 1f).pbr(0.85f, 0.28f);
  // the player's ship: worn chrome and teal paint, the colour of the HUD
  final Material playerHull = new Material().color(0.9f, 0.92f, 0.95f, 1f).pbr(0.85f, 0.28f);
  final Material playerPaint = new Material().color(0.1f, 0.5f, 0.42f, 1f).pbr(0f, 0.2f);
  final Material playerFrame = new Material().color(0.2f, 0.21f, 0.24f, 1f).pbr(0.7f, 0.45f);
  final Material bossPaint = new Material().color(0.55f, 0.03f, 0.02f, 1f).pbr(0f, 0.22f);
  final Material bossDome = glass(0.45f, 0.03f, 0.02f, 0.35f, 0.04f, 0.02f);
  final Material bossLight = glowing(2.6f, 0.35f, 0.2f);
  final Material scoutHull = new Material().color(0.85f, 0.52f, 0.05f, 1f).pbr(0f, 0.18f);
  final Material scoutFin = new Material().color(0.85f, 0.86f, 0.9f, 1f).pbr(0.85f, 0.25f);
  final Material scoutDome = glass(0.4f, 0.28f, 0.03f, 0.45f, 0.3f, 0.03f);
  final Material scoutLight = glowing(2.6f, 1.9f, 0.3f);
  final Material whiteLight = glowing(2.4f, 2.4f, 2.3f);
  final Material engineGlow = glowing(2.4f, 0.8f, 0.25f);
  final Material cockpitLight = glowing(0.6f, 1.6f, 1.2f);
  // the cockpit: a dark dashboard, teal lines glowing along the frame (the HUD's colour), and a bubble of clear glass
  // that reflects the space around it, more towards its edges
  final Material dashMaterial = new Material().color(0.07f, 0.08f, 0.09f, 1f).pbr(0.3f, 0.55f);
  final Material holoLine = glowing(0.15f, 0.75f, 0.6f);
  final Material canopyGlass = new Material().color(0.02f, 0.05f, 0.05f, 1f).pbr(0f, 0.03f).glass(0.035f);
  // the readouts on the glass: light added to the view (lit, the faint ghost of what is unlit, and the jump's bar)
  final Material holoOn = holo(0.25f, 1.3f, 1.0f);
  final Material holoGhost = holo(0.02f, 0.09f, 0.07f);
  final Material holoJump = holo(0.3f, 1.1f, 1.3f);
  /** The gun's battery bar: teal like the rest while charged, turning red as it runs flat. */
  final Material holoBattery = holo(0.25f, 1.3f, 1.0f);
  // the player's ship from outside (seen from behind it): its glass is glossy and dark, like the enemies' domes
  final Material playerDome = glass(0.04f, 0.16f, 0.15f, 0.02f, 0.09f, 0.08f);

  /** One piece of a ship: where it sits on the ship (which has a scale of its radius), and how it looks. */
  record Part(GpuMesh mesh, Material material, Vector3f position, Quaternionf rotation, Vector3f scale) {
  }

  /** The big enemy ship and the scout, as parts. */
  final List<Part> bossParts = new ArrayList<>();
  final List<Part> scoutParts = new ArrayList<>();
  /** The player's ship as seen from inside, in camera space (the camera looks along -z): dashboard, frame, glass. */
  final List<Part> cockpitParts = new ArrayList<>();
  /** The player's whole ship, for the view from behind it: it points along -z and has a radius of about 1. */
  final List<Part> playerParts = new ArrayList<>();
  /**
   * The player's four wings, spread in an X: right or left ({@code WING_X}) and upper or lower ({@code WING_Y}), in the
   * order the guns take turns, from one corner of the X to the opposite one.
   */
  static final int[] WING_X = { 1, -1, -1, 1 };
  static final int[] WING_Y = { 1, -1, 1, -1 };
  /** How far each wing is tilted up or down from level, and how far out along it the tip is. */
  static final float WING_DEGREES = 20f;
  static final float WING_TIP = 1.2f;
  /** Where the player's shots come from: the muzzles of the guns at the wingtips (on the ship, as {@link #playerParts}). */
  static final Vector3f[] PLAYER_GUNS = onWings(WING_TIP, -0.5f);
  /** The end of the player's engine, at the tail, and the steering thrusters at the back of the wingtips. */
  static final Vector3f PLAYER_ENGINE = new Vector3f(0f, 0f, 1.02f);
  static final Vector3f[] PLAYER_THRUSTERS = onWings(WING_TIP, 0.62f);
  /**
   * The same guns seen from the cockpit, in camera space: below and ahead, hidden behind the dashboard, so shots rise
   * over it and close in on the crosshair.
   */
  static final Vector3f[] COCKPIT_GUNS = { new Vector3f(-0.45f, -1.05f, -1.8f), new Vector3f(0.45f, -1.05f, -1.8f) };
  /** The graphics quality in force (set with the options): how much the bursts make. */
  Quality quality = Quality.HIGH;
  final Material fire = glowing(2.4f, 1.1f, 0.2f);
  final Material starMaterial = new Material().color(0.75f, 0.78f, 0.85f, 1f).unlit();
  final Material brightStarMaterial = new Material().color(1f, 1f, 1f, 1f).unlit();

  final AudioClip shot;
  final AudioClip bossShot;
  final AudioClip bigBoom;
  final AudioClip smallBoom;
  final AudioClip shipBoom;
  final AudioClip clang;
  final AudioClip rockBump;
  final AudioClip empty;
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
    this.smoothBall = res.mesh("gen:smooth-ball", MeshFactory.sphere(1f, 28, 18));
    this.stars = res.mesh(MeshFactory.points(starField(1400, 1L)));
    this.brightStars = res.mesh(MeshFactory.points(starField(250, 2L)));
    this.rockMaterial.specular = 0.05f;
    this.smallRockMaterial.specular = 0.05f;
    this.bossPaint.reflectance = 0.06f;
    this.scoutHull.reflectance = 0.06f;
    this.playerPaint.reflectance = 0.06f;
    // the sky is kept dark behind the game; the ships take more of its light, so their polish shows
    for (var m : List.of(this.bossHull, this.bossPaint, this.scoutFin, this.playerHull, this.playerFrame)) {
      m.ambient.set(3f);
    }
    this.playerPaint.ambient.set(2f);
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
    this.rockBump = res.sound("gen:rock-bump", Sounds.knock());
    this.empty = res.sound("gen:empty", Sounds.empty());
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

  /** Its own material for one fireball (it changes colour and fades on its own): see-through and glowing. */
  Material fireballMaterial() {
    final var m = new Material().color(0.13f, 0.12f, 0.11f, 0.65f).transparent(); // smoke, once the fire has gone
    m.specular = 0f;
    m.emissive.set(5f, 3f, 1.5f);
    return m;
  }

  /** Its own material for one cloud of smoke: dark, see-through points. */
  Material smokeMaterial() {
    final var m = new Material().color(0.16f, 0.15f, 0.14f, 0.75f).unlit().transparent();
    m.pointSize = 3.5f;
    return m;
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
    // a used look for all hull surfaces: plates, rivets, grime and scratches
    final var wear = ShipArt.wear(256, 21L);
    final var wearColour = res.texture("gen:wear-albedo", wear.albedo());
    final var wearShine = res.texture("gen:wear-metal-rough", wear.metalRough());
    final var wearBumps = res.texture("gen:wear-normals", wear.normals());
    for (var m : List.of(this.bossHull, this.bossPaint, this.scoutHull, this.scoutFin, this.playerHull,
        this.playerPaint, this.playerFrame)) {
      m.texture = wearColour;
      m.metalRoughMap = wearShine;
      m.normalMap = wearBumps;
      m.uvScale.set(4f, 1f); // eight plates round the hull
      m.roughness = Math.min(1f, m.roughness * 1.6f); // the map scales it: 0.55 on clean metal, up to 1 on grime
    }
    for (int i = 0; i < this.shards.length; i++) {
      this.shards[i] = res.mesh("gen:shard-" + i, ShipArt.shard(3000L + i));
    }

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
    buildCockpit(res);
    buildPlayerShip(res);
    for (int i = 0; i < 6; i++) {
      final double a = 2 * Math.PI * (i + 0.5) / 6;
      this.scoutParts.add(new Part(this.ball, this.scoutLight,
          new Vector3f((float) Math.cos(a) * 0.98f, 0.01f, (float) -Math.sin(a) * 0.98f), none, new Vector3f(0.04f)));
    }
  }

  /**
   * The cockpit, in camera space ({@link CockpitArt}): a bubble of glass over the pilot, the arch of its frame across
   * the view and a low dashboard round the bottom, dark, with thin teal lines glowing along their edges like the HUD.
   */
  private void buildCockpit(engine.resources.ResourceManager res) {
    final var none = new Quaternionf();
    final var one = new Vector3f(1f);
    final var dash = res.mesh("gen:dashboard", CockpitArt.dashboard(64));
    this.cockpitParts.add(new Part(dash, this.dashMaterial, new Vector3f(), none, one));
    // the sill's edge, and a line along the shelf just inside it
    final var sill = res.mesh("gen:sill", CockpitArt.tube(CockpitArt.sillLine(64, 0.004f, 0f), 0.012f, 8));
    this.cockpitParts.add(new Part(sill, this.playerFrame, new Vector3f(), none, one));
    final var sillGlow = res.mesh("gen:sill-glow", CockpitArt.tube(CockpitArt.sillLine(64, 0.0f, 0.03f), 0.0035f, 6));
    this.cockpitParts.add(new Part(sillGlow, this.holoLine, new Vector3f(), none, one));
    // the arch, with a glowing line along the side that faces the pilot
    final var arch = res.mesh("gen:arch", CockpitArt.tube(CockpitArt.arch(48, 0.015f), 0.015f, 10));
    this.cockpitParts.add(new Part(arch, this.playerFrame, new Vector3f(), none, one));
    final var archGlow = res.mesh("gen:arch-glow", CockpitArt.tube(CockpitArt.arch(48, 0.032f), 0.0025f, 6));
    this.cockpitParts.add(new Part(archGlow, this.holoLine, new Vector3f(), none, one));
    // a few lights on the dashboard, either side of the middle
    for (int i = 0; i < 4; i++) {
      final float deg = (i < 2 ? -1 : 1) * (14f + 5f * (i % 2));
      final var at = CockpitArt.sill(deg, 0f);
      final var axis = new Vector3f(CockpitArt.CENTER.x, at.y, CockpitArt.CENTER.z);
      at.sub(axis).mul(0.86f).add(axis).add(0f, -0.012f, 0f);
      this.cockpitParts.add(new Part(this.cube, i % 2 == 0 ? this.cockpitLight : this.whiteLight, at,
          new Quaternionf().rotateY((float) Math.toRadians(-deg)), new Vector3f(0.022f, 0.004f, 0.012f)));
    }
    // last, so it is drawn over the rest when transparency is sorted
    final var glass = res.mesh("gen:canopy", CockpitArt.canopy(64, 20));
    this.cockpitParts.add(new Part(glass, this.canopyGlass, new Vector3f(), none, one));
  }

  /** The way out along wing {@code i} (see {@link #WING_X}), in the ship's x-y plane. */
  static Vector3f wingDirection(int i, Vector3f dest) {
    final double a = Math.toRadians(WING_DEGREES);
    return dest.set(WING_X[i] * (float) Math.cos(a), WING_Y[i] * (float) Math.sin(a), 0f);
  }

  /** One point on each wing: {@code out} along it from the ship's middle line, at {@code z}. */
  private static Vector3f[] onWings(float out, float z) {
    final var points = new Vector3f[WING_X.length];
    for (int i = 0; i < points.length; i++) {
      points[i] = wingDirection(i, new Vector3f()).mul(out);
      points[i].z = z;
    }
    return points;
  }

  /**
   * The player's ship from outside: a long teal fuselage turned on a lathe with a dark glass canopy and a glowing
   * engine at its tail, and four chrome wings spread in an X behind it, with a gun along each tip and a small steering
   * thruster at the back of each tip. It points along -z, about 2.2 long and 2.3 across.
   */
  private void buildPlayerShip(engine.resources.ResourceManager res) {
    final var fuselage = res.mesh("gen:fuselage", ShipArt.lathe(ShipArt.FUSELAGE, 32));
    final var ahead = new Quaternionf().rotateX((float) Math.toRadians(-90)); // the lathe's +y to the ship's -z
    final var none = new Quaternionf();
    this.playerParts.add(new Part(fuselage, this.playerPaint, new Vector3f(0f, 0f, 1f), ahead,
        new Vector3f(0.68f, 1.05f, 0.68f)));
    this.playerParts.add(new Part(this.smoothBall, this.playerDome, new Vector3f(0f, 0.17f, -0.15f), none,
        new Vector3f(0.15f, 0.13f, 0.36f)));
    this.playerParts.add(new Part(this.smoothBall, this.engineGlow, new Vector3f(PLAYER_ENGINE), none,
        new Vector3f(0.13f, 0.13f, 0.03f)));
    final var out = new Vector3f();
    for (int i = 0; i < WING_X.length; i++) {
      wingDirection(i, out);
      final float sx = WING_X[i];
      final float sy = WING_Y[i];
      // the wing from inside the fuselage to the tip, turned about the ship's long axis into its corner of the X
      final var tilt = new Quaternionf().rotateZ((float) Math.toRadians(sx * sy * WING_DEGREES));
      final float span = WING_TIP - 0.15f;
      final var middle = new Vector3f(out).mul(0.15f + span / 2f);
      this.playerParts.add(new Part(this.cube, this.playerHull, new Vector3f(middle.x, middle.y, 0.42f), tilt,
          new Vector3f(span / 2f, 0.022f, 0.24f)));
      // a teal stripe along its outer face
      final var face = new Vector3f(-out.y, out.x, 0f).mul(sx * sy * 0.025f);
      this.playerParts.add(new Part(this.cube, this.playerPaint,
          new Vector3f(middle.x + face.x, middle.y + face.y, 0.3f), tilt, new Vector3f(span / 2f - 0.05f, 0.006f,
              0.05f)));
      // the gun along the tip, and the steering thruster behind it
      final var muzzle = PLAYER_GUNS[i];
      this.playerParts.add(new Part(this.cube, this.playerFrame, new Vector3f(muzzle.x, muzzle.y, 0.05f), none,
          new Vector3f(0.024f, 0.024f, 0.55f)));
      final var thruster = PLAYER_THRUSTERS[i];
      this.playerParts.add(new Part(this.ball, this.cockpitLight, new Vector3f(thruster), none,
          new Vector3f(0.045f)));
    }
  }

  /** Light that is only added to what is behind it: the readouts on the cockpit's glass. */
  private static Material holo(float r, float g, float b) {
    final var m = new Material().color(0f, 0f, 0f, 1f).glass(0.02f);
    m.emissive.set(r, g, b);
    m.specular = 0f;
    return m;
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
