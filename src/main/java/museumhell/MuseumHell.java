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
import museumhell.game.input.InputSystem;
import museumhell.game.interaction.InteractionSystem;
import museumhell.game.loot.LootSystem;
import museumhell.game.player.PlayerController;
import museumhell.ui.Hud;
import museumhell.ui.Prompt;

import java.awt.*;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MuseumHell extends SimpleApplication {

    private BulletAppState physics;
    private WorldBuilder world;
    private PlayerController player;
    private InputSystem input;
    private Spatial cameraBase;

    private float bobTime = 0f;
    private static final float BOB_SPEED = 15f;
    private static final float BOB_AMPLITUDE = 0.12f;
    private static final float SPRINT_BOB_SPEED = 25f;
    private static final float SPRINT_BOB_AMPLITUDE = 0.3f;

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
        app.setDisplayStatView(false);
        app.setDisplayFps(true);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        cameraBase = assetManager.loadModel("Models/camara.glb"); // todo --> mover a gestor dedicado
        cameraBase.scale(0.5f);

        // Luz ambiental tenue
        rootNode.addLight(new AmbientLight(ColorRGBA.White.mult(0.00244f)));

        // Física
        physics = new BulletAppState();
        stateManager.attach(physics);
        physics.setDebugEnabled(false);

        inputManager.addMapping("ToggleDebug", new KeyTrigger(KeyInput.KEY_TAB)); // todo --> mover a InputSystem
        inputManager.addListener(new ActionListener() {
            @Override
            public void onAction(String name, boolean isPressed, float tpf) {
                if (name.equals("ToggleDebug") && isPressed) {
                    physics.setDebugEnabled(!physics.isDebugEnabled());
                }
            }
        }, "ToggleDebug");

        /* ---------- WORLD ---------- */
        MuseumLayout museum = MuseumGenerator.generate(100, 75, 3, System.nanoTime());
        world = new WorldBuilder(assetManager, rootNode, physics.getPhysicsSpace());
        world.build(museum);

        cameraBase.scale(0.5f);

        float cameraExtrusion = 1f;
        var camBuilder = new Camera(rootNode, cameraBase, cameraExtrusion);
        camBuilder.build(museum);

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
        loot.distributeAcrossRooms(lootRooms, 17, 33, 5);

        // Ajuste de cámara
        cam.setFrustumNear(0.525f);
    }

    @Override
    public void simpleUpdate(float tpf) {
        player.update(tpf);
        input.update(tpf);
        world.update(tpf);

        float bobAmplitude = input.isSprinting() ? SPRINT_BOB_AMPLITUDE : BOB_AMPLITUDE;
        float bobSpeed = input.isSprinting() ? SPRINT_BOB_SPEED : BOB_SPEED;

        if (input.isMovingForwardBack()) {
            bobTime += tpf * bobSpeed;
        } else {
            bobTime = 0f;
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
