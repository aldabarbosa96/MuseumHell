package museumhell;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import museumhell.game.player.PlayerController;
import museumhell.engine.world.WorldBuilder;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.generator.MuseumGenerator;
import museumhell.game.input.InputSystem;
import museumhell.game.interaction.InteractionSystem;
import museumhell.game.loot.LootSystem;
import museumhell.ui.Hud;
import museumhell.ui.Prompt;

import java.awt.*;

public class MuseumHell extends SimpleApplication {

    private BulletAppState physics;
    private WorldBuilder world;
    private PlayerController player;
    private InputSystem input;
    private SpotLight flashlight;

    private float bobTime = 0f;
    private static final float BOB_SPEED = 15f;
    private static final float BOB_AMPLITUDE = 0.12f;
    private static final float SPRINT_BOB_SPEED = 20f;
    private static final float SPRINT_BOB_AMPLITUDE = 0.15f;

    private Vector3f smoothEyePos;
    private Vector3f smoothDirection;
    private static final float SMOOTH_FACTOR = 0.12f;

    public static void main(String[] args) {
        DisplayMode dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
        float scale = .75f;
        AppSettings cfg = new AppSettings(true);
        cfg.setResolution(Math.round(dm.getWidth() * scale), Math.round(dm.getHeight() * scale));
        cfg.setTitle("MuseumHell");
        cfg.setVSync(true);
        cfg.setGammaCorrection(true);
        if (dm.getRefreshRate() > 0) cfg.setFrequency(dm.getRefreshRate());

        MuseumHell app = new MuseumHell();
        app.setSettings(cfg);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {

        rootNode.addLight(new AmbientLight(ColorRGBA.White.mult(0.002f)));

        physics = new BulletAppState();
        stateManager.attach(physics);
        physics.setDebugEnabled(false);

        /* ---------- WORLD ---------- */
        MuseumLayout museum = MuseumGenerator.generate(85, 65, 3, System.nanoTime());
        world = new WorldBuilder(assetManager, rootNode, physics.getPhysicsSpace());
        world.build(museum);

        /* ---------- PLAYER ---------- */
        Room startRoom = museum.floors().get(0).rooms().get(0);
        player = new PlayerController(assetManager, physics.getPhysicsSpace(), startRoom.center3f(3f));
        rootNode.attachChild(player.getNode());

        /* ---------- SYSTEMS ---------- */
        input = new InputSystem(inputManager, flyCam);
        input.setupCameraFollow(cam);
        input.registerPlayerControl(player);
        input.setWorld(world);

        Hud hud = new Hud();
        stateManager.attach(hud);
        Prompt prompt = new Prompt();
        stateManager.attach(prompt);

        LootSystem loot = new LootSystem(assetManager, rootNode, player, hud);
        stateManager.attach(loot);
        input.setLootManager(loot);

        stateManager.attach(new InteractionSystem(player, world, loot, prompt));

        // linterna
        flashlight = new SpotLight();
        flashlight.setColor(new ColorRGBA(1f, 0.95f, 0.65f, 1f).multLocal(5));
        flashlight.setSpotRange(50f);
        flashlight.setSpotInnerAngle(FastMath.DEG_TO_RAD * 5);
        flashlight.setSpotOuterAngle(FastMath.DEG_TO_RAD * 25f);
        rootNode.addLight(flashlight);

        Vector3f initEye = player.getLocation().add(0, 1f, 0).addLocal(cam.getDirection().mult(-.25f));
        smoothEyePos = initEye.clone();
        smoothDirection = cam.getDirection().clone();

        /* ---------- LOOT SPAWN ---------- */
        museum.floors().forEach(f -> f.rooms().stream().skip(1).filter(r -> Math.random() > .5).forEach(r -> loot.scatter(r, 1 + (int) (Math.random() * 3))));

        cam.setFrustumNear(.55f);
    }

    @Override
    public void simpleUpdate(float tpf) {
        // 1) Actualiza sistemas
        player.update(tpf);
        input.update(tpf);
        world.update(tpf);

        // 2) Head‑bob solo al avanzar/retroceder
        if (input.isMovingForwardBack()) {
            // elige parámetros según sprint
            float bobSpeed = input.isSprinting() ? SPRINT_BOB_SPEED : BOB_SPEED;
            float bobAmplitude = input.isSprinting() ? SPRINT_BOB_AMPLITUDE : BOB_AMPLITUDE;
            bobTime += tpf * bobSpeed;
        } else {
            bobTime = 0f;
        }

        // 3) Calcula offset vertical
        float bobOffsetY = FastMath.sin(bobTime) * (input.isSprinting() ? SPRINT_BOB_AMPLITUDE : BOB_AMPLITUDE);

        // 4) Posición objetivo de la cámara (incluye bob)
        Vector3f targetEye = player.getLocation().add(0, 1f + bobOffsetY, 0).addLocal(cam.getDirection().mult(-0.25f));

        // 5) Suaviza posición (lerp)
        smoothEyePos.interpolateLocal(targetEye, SMOOTH_FACTOR);
        cam.setLocation(smoothEyePos);

        // 6) Suaviza dirección para la linterna
        Vector3f camDir = cam.getDirection();
        smoothDirection.interpolateLocal(camDir, SMOOTH_FACTOR).normalizeLocal();

        // 7) Aplica al SpotLight
        flashlight.setPosition(smoothEyePos);
        flashlight.setDirection(smoothDirection);
    }


}
