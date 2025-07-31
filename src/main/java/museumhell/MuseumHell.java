package museumhell;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import museumhell.engine.world.WorldBuilder;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.generator.MuseumGenerator;
import museumhell.engine.world.levelgen.roomObjects.Camera;
import museumhell.game.ai.SecurityCamSystem;
import museumhell.game.input.InputSystem;
import museumhell.game.interaction.InteractionSystem;
import museumhell.game.loot.LootSystem;
import museumhell.game.player.PlayerController;
import museumhell.ui.Hud;
import museumhell.ui.Prompt;
import museumhell.utils.AudioLoader;
import museumhell.utils.AssetLoader;

import java.awt.*;
import java.util.*;
import java.util.List;

import static museumhell.utils.ConstantManager.*;

public class MuseumHell extends SimpleApplication {
    private Hud hud;
    private AssetLoader visuals;
    private AudioLoader audio;
    private BulletAppState physics;
    private WorldBuilder world;
    private PlayerController player;
    private InputSystem input;
    private Spatial cameraBase;
    private float bobTime = 0f;
    private Vector3f smoothEyePos;
    private Vector3f smoothDirection;
    private float stepTime = 0f;
    private int lastStepCount = 0;
    private final Random random = new Random();

    public static void main(String[] args) {
        DisplayMode dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
        float scale = .75f;
        AppSettings cfg = new AppSettings(true);
        cfg.setResolution(Math.round(dm.getWidth() * scale), Math.round(dm.getHeight() * scale));
        cfg.setTitle("MuseumHell");
        cfg.setVSync(true);
        cfg.setGammaCorrection(true);
        if (dm.getRefreshRate() > 0) cfg.setFrequency(dm.getRefreshRate()); // todo --> revisar si se capan los fps

        MuseumHell app = new MuseumHell();
        app.setSettings(cfg);
        app.setShowSettings(true);
        app.setDisplayStatView(false);
        app.setDisplayFps(true);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        visuals = new AssetLoader(assetManager);
        audio = new AudioLoader(assetManager, rootNode);

        audio.play("ambient1");
        audio.play("ambient2");
        cameraBase = visuals.get("camera1");

        // Luz ambiental tenue
        rootNode.addLight(new AmbientLight(ColorRGBA.White.mult(0.00244f)));

        // Física
        Vector3f worldMin = new Vector3f(-150f, -10f, -150f);
        Vector3f worldMax = new Vector3f(150f, 50f, 150f);

        // Instanciamos BulletAppState usando AxisSweep3 internamente
        physics = new BulletAppState(worldMin, worldMax);
        stateManager.attach(physics);

        physics.setDebugEnabled(false);

        /* ---------- WORLD ---------- */
        MuseumLayout museum = MuseumGenerator.generate(150, 125, 3, System.nanoTime());
        world = new WorldBuilder(assetManager, rootNode, physics.getPhysicsSpace(), visuals);
        world.build(museum);

        float floorH = museum.floorHeight();
        for (int i = 0; i < museum.floors().size(); i++) {
            float y0 = museum.yOf(i);
            List<Room> rooms = museum.floors().get(i).rooms();
            world.getLightPlacer().initRoomBeacons(rooms, y0, floorH);
        }

        cameraBase.scale(0.5f);

        float baseExtrusion = 1.25f;
        float cameraExtrusion = baseExtrusion + WALL_T * 0.5f * FastMath.sqrt(2f);

        var camBuilder = new Camera(rootNode, cameraBase, cameraExtrusion);
        camBuilder.build(museum);

        /* ---------- PLAYER ---------- */
        Room startRoom = museum.floors().get(0).rooms().get(0);
        player = new PlayerController(physics.getPhysicsSpace(), startRoom.center3f(5f));
        rootNode.attachChild(player.getNode());

        stateManager.attach(new SecurityCamSystem(camBuilder, player, rootNode, world.getLightPlacer()));

        /* ---------- SYSTEMS ---------- */
        input = new InputSystem(inputManager, flyCam, physics);
        input.setAudioManager(audio);
        input.setupCameraFollow(cam);
        input.registerPlayerControl(player);
        input.setWorld(world);

        hud = new Hud();
        stateManager.attach(hud);
        Prompt prompt = new Prompt();
        stateManager.attach(prompt);

        LootSystem loot = new LootSystem(assetManager, rootNode, physics.getPhysicsSpace(), player, hud, museum.floorHeight());
        stateManager.attach(loot);
        input.setLootManager(loot);

        stateManager.attach(new InteractionSystem(player, world, loot, prompt));

        // Linterna
        Vector3f initEye = player.getLocation().add(0, 1f, 0).addLocal(cam.getDirection().mult(-0.25f));
        smoothEyePos = initEye.clone();
        smoothDirection = cam.getDirection().clone();
        world.getLightPlacer().initFlashlight(initEye, smoothDirection);

        /* ---------- LOOT SPAWN --------- */
        List<Map.Entry<Room, Integer>> lootRooms = new ArrayList<>();
        for (int f = 0; f < museum.floors().size(); f++) {
            for (Room r : museum.floors().get(f).rooms()) {
                if (!r.equals(startRoom)) {
                    lootRooms.add(new AbstractMap.SimpleEntry<>(r, f));
                }
            }
        }
        loot.distributeAcrossRooms(lootRooms, 20, 35, 5);

        // Ajuste de cámara
        cam.setFrustumNear(0.525f);

    }

    @Override
    public void simpleUpdate(float tpf) {
        player.update(tpf);
        input.update(tpf);
        world.update(tpf);

        Vector3f look = cam.getDirection().clone().setY(0).normalizeLocal();
        String compass;
        if (FastMath.abs(look.z) > FastMath.abs(look.x)) {
            compass = (look.z < 0) ? "NORTE" : "SUR";
        } else {
            compass = (look.x > 0) ? "ESTE" : "OESTE";
        }
        hud.setDirection(compass);

        float bobAmplitude = input.isSprinting() ? SPRINT_BOB_AMPLITUDE : BOB_AMPLITUDE;
        float bobSpeed = input.isSprinting() ? SPRINT_BOB_SPEED : BOB_SPEED;

        if (input.isMovingForwardBack() && !input.isJump()) {
            bobTime += tpf * bobSpeed;
        } else {
            bobTime = 0f;
        }

        if (input.isMoving() && !input.isJump()) {
            stepTime += tpf * bobSpeed;
            int currentStep = (int) (stepTime / 8f);
            if (currentStep > lastStepCount) {
                lastStepCount = currentStep;
                int idx = random.nextInt(2) + 1;
                audio.play("footstep" + idx);
            }
        } else {
            stepTime = 0f;

            lastStepCount = 0;
        }

        float bobOffsetY = FastMath.sin(bobTime) * bobAmplitude;
        float eyeBaseHeight = input.isCrouching() ? 0.8f : 1.75f;

        Vector3f targetEye = player.getLocation().add(0, eyeBaseHeight + bobOffsetY, 0).addLocal(cam.getDirection().mult(-0.25f));

        smoothEyePos.interpolateLocal(targetEye, SMOOTH_FACTOR);
        cam.setLocation(smoothEyePos);

        Vector3f camDir = cam.getDirection();
        smoothDirection.interpolateLocal(camDir, SMOOTH_FACTOR).normalizeLocal();

        world.getLightPlacer().updateFlashlight(smoothEyePos, smoothDirection);
    }
}
