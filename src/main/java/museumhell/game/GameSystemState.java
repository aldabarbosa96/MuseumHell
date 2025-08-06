package museumhell.game;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.InputManager;
import com.jme3.input.FlyByCamera;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.asset.AssetManager;

import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.shadow.SpotLightShadowRenderer;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.game.ai.EnemySystem;
import museumhell.game.ai.SecurityCamera;
import museumhell.game.ai.SecurityCamSystem;
import museumhell.game.input.InputSystem;
import museumhell.game.input.InteractionSystem;
import museumhell.game.loot.LootSystem;
import museumhell.game.player.MoveEffectState;
import museumhell.game.player.PlayerController;
import museumhell.ui.Hud;
import museumhell.ui.Prompt;
import museumhell.utils.media.AssetLoader;
import museumhell.utils.media.AudioLoader;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static museumhell.utils.ConstantManager.*;

public class GameSystemState extends BaseAppState {
    private final AssetLoader assetManager;
    private final AssetManager assets;
    private LootSystem lootSystem;
    private final Node rootNode;
    private final BulletAppState physics;
    private final WorldBuilder world;
    private final MuseumLayout layout;
    private final PlayerController player;
    private final SecurityCamera camBuilder;
    private final AudioLoader audio;

    public GameSystemState(AssetLoader assetManager, AssetManager assets, Node rootNode, BulletAppState physics, WorldBuilder world, MuseumLayout layout, PlayerController player, SecurityCamera camBuilder, AudioLoader audio) {
        this.assetManager = assetManager;
        this.assets = assets;
        this.rootNode = rootNode;
        this.physics = physics;
        this.world = world;
        this.layout = layout;
        this.player = player;
        this.camBuilder = camBuilder;
        this.audio = audio;
    }

    @Override
    protected void initialize(Application app) {
        SimpleApplication sApp = (SimpleApplication) app;
        InputManager im = sApp.getInputManager();
        FlyByCamera fc = sApp.getFlyByCamera();
        Camera camera = sApp.getCamera();

        // 1) Cámaras de seguridad
        getStateManager().attach(new SecurityCamSystem(camBuilder, player, rootNode, world.getLightPlacer(), audio));

        // 2) Sistema de input
        InputSystem input = new InputSystem(im, fc, physics);
        input.setAudioManager(audio);
        input.setupCameraFollow(camera);
        input.registerPlayerControl(player);
        input.setWorld(world);
        getStateManager().attach(input);

        // 3) HUD y prompt
        Hud hud = new Hud();
        Prompt prompt = new Prompt();
        getStateManager().attach(hud);
        getStateManager().attach(prompt);

        // 4) LootSystem + distribución de loot
        lootSystem = new LootSystem(assets, rootNode, physics.getPhysicsSpace(), player, hud, layout.floorHeight());
        getStateManager().attach(lootSystem);
        input.setLootManager(lootSystem);

        // Preparamos lista de salas menos la de inicio
        Room startRoom = layout.floors().get(0).rooms().get(0);
        List<Map.Entry<Room, Integer>> lootRooms = new ArrayList<>();
        for (int f = 0; f < layout.floors().size(); f++) {
            for (Room r : layout.floors().get(f).rooms()) {
                if (!r.equals(startRoom)) {
                    lootRooms.add(new AbstractMap.SimpleEntry<>(r, f));
                }
            }
        }
        lootSystem.distributeAcrossRooms(lootRooms, 23, 35, 5);

        // 5) InteractionSystem
        getStateManager().attach(new InteractionSystem(player, world, lootSystem, prompt));

        // 6) Sistema de guardias
        BulletAppState bullet = getStateManager().getState(BulletAppState.class);
        getStateManager().attach(new EnemySystem(assetManager, bullet, rootNode, layout, world, player, audio));

        // 7) MoveEffectState
        getStateManager().attach(new MoveEffectState(player, input, audio, hud, camera, world.getLightPlacer()));

        // 8) Inicialización de la linterna para evitar NPE en el primer update
        Vector3f eye = player.getLocation().add(0, 1f, 0).add(camera.getDirection().mult(-0.25f));
        Vector3f torchPos = eye.add(camera.getLeft().normalize().mult(FLASH_OFFSET_SIDE)).add(0, -FLASH_OFFSET_DOWN, 0).add(camera.getDirection().normalize().mult(FLASH_OFFSET_FORWARD));
        world.getLightPlacer().initFlashlight(torchPos, camera.getDirection().clone());

        SpotLightShadowRenderer flashSSR = new SpotLightShadowRenderer(app.getAssetManager(), 1024); // todo --> testear rendimiento en diferentes sistemas
        flashSSR.setLight(world.getLightPlacer().getFlashlight());
        flashSSR.setEdgeFilteringMode(EdgeFilteringMode.PCF4);
        flashSSR.setShadowZExtend(200f);
        flashSSR.setShadowIntensity(0.5f);
        flashSSR.setShadowZFadeLength(20f);
        app.getViewPort().addProcessor(flashSSR);


    }

    @Override
    public void update(float tpf) {
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
}
