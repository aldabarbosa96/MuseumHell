package museumhell;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import museumhell.game.ai.SecurityCamera;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.world.WorldInitState;
import museumhell.game.GameSystemState;
import museumhell.game.player.PlayerController;
import museumhell.utils.media.AudioLoader;
import museumhell.utils.media.AssetLoader;

import java.awt.*;

public class MuseumHell extends SimpleApplication {
    private AssetLoader visuals;
    private AudioLoader audio;
    private BulletAppState physics;
    private WorldBuilder world;
    private PlayerController player;
    private MuseumLayout museumLayout;
    private Spatial cameraBase;

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
        app.setDisplayStatView(true);
        app.setDisplayFps(true);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        // 1) Carga de assets y audio
        visuals = new AssetLoader(assetManager);
        audio = new AudioLoader(assetManager, rootNode);
        audio.play("ambient1");
        audio.play("ambient2");
        cameraBase = visuals.get("camera1");

        // 2) Luz ambiental tenue
        rootNode.addLight(new AmbientLight(ColorRGBA.White.mult(0.003f)));

        // 3) Física
        Vector3f worldMin = new Vector3f(-150f, -10f, -150f);
        Vector3f worldMax = new Vector3f(150f, 50f, 150f);
        physics = new BulletAppState(worldMin, worldMax);
        stateManager.attach(physics);
        physics.setDebugEnabled(false);

        // 4) Mundo
        WorldInitState worldState = new WorldInitState(assetManager, rootNode, physics, visuals, cameraBase);
        stateManager.attach(worldState);
        world = worldState.getWorldBuilder();
        museumLayout = worldState.getMuseumLayout();
        SecurityCamera camBuilder = worldState.getCameraBuilder();

        // 5) Jugador
        Room startRoom = museumLayout.floors().get(0).rooms().get(0);
        player = new PlayerController(physics.getPhysicsSpace(), startRoom.center3f(5f));
        rootNode.attachChild(player.getNode());

        // 6) GameSystemState
        GameSystemState gameState = new GameSystemState(visuals,assetManager, rootNode, physics, world, museumLayout, player, camBuilder, audio);
        stateManager.attach(gameState);

        // 7) Ajuste final de cámara (FOV)
        cam.setFrustumNear(0.525f);
    }


    @Override
    public void simpleUpdate(float tpf) {
        player.update(tpf);
        world.update(tpf);
    }
}
