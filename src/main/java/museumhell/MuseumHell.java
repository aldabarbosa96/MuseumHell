package museumhell;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import museumhell.input.GameInputManager;
import museumhell.player.PlayerController;
import museumhell.world.WorldBuilder;
import museumhell.world.levelgen.BspGenerator;
import museumhell.world.levelgen.LevelLayout;
import museumhell.world.levelgen.Room;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.List;

public class MuseumHell extends SimpleApplication {
    private BulletAppState physics;
    private PlayerController playerCtrl;
    private GameInputManager inputMgr;
    private WorldBuilder world;

    public static void main(String[] args) {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        DisplayMode desktop = device.getDisplayMode();

        final float SCALE = 0.75f;
        int winWidth = Math.round(desktop.getWidth() * SCALE);
        int winHeight = Math.round(desktop.getHeight() * SCALE);

        AppSettings cfg = new AppSettings(true);
        cfg.setTitle("Museum Hell");
        cfg.setFullscreen(false);
        cfg.setResolution(winWidth, winHeight);
        int hz = desktop.getRefreshRate();
        if (hz > 0) cfg.setFrequency(hz);
        cfg.setVSync(true);
        cfg.setFrameRate(-1);
        cfg.setGammaCorrection(true);

        MuseumHell app = new MuseumHell();
        app.setSettings(cfg);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -2, -3).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        rootNode.addLight(sun);

        AmbientLight amb = new AmbientLight();
        amb.setColor(ColorRGBA.White.mult(0.3f));
        rootNode.addLight(amb);

        physics = new BulletAppState();
        stateManager.attach(physics);
        physics.setDebugEnabled(true);

        LevelLayout layout = BspGenerator.generate(60, 60, System.nanoTime());
        world = new WorldBuilder(assetManager, rootNode, physics.getPhysicsSpace());
        world.build(layout, 6f);

        List<Room> rooms = layout.rooms();
        for (int i = 0; i < rooms.size(); i++) {
            if (i == 0) continue;
            if (Math.random() < 0.5) continue;
            world.addLootToRoom(rooms.get(i), 1 + (int) (Math.random() * 3)); // 1-3 objetos
        }


        Vector3f startPos = layout.rooms().get(0).center3f(3f);
        playerCtrl = new PlayerController(assetManager, physics.getPhysicsSpace(), startPos);
        rootNode.attachChild(playerCtrl.getNode());

        inputMgr = new GameInputManager(inputManager, flyCam);
        inputMgr.setupCameraFollow(cam);
        inputMgr.registerPlayerControl(playerCtrl);

        cam.setFrustumNear(0.55f);
    }


    @Override
    public void simpleUpdate(float tpf) {
        playerCtrl.update(tpf);
        inputMgr.update(tpf);
        Vector3f lookBack = cam.getDirection().mult(-0.25f);
        Vector3f eye = playerCtrl.getLocation().add(0, 0.4f, 0).addLocal(lookBack);
        cam.setLocation(eye);
    }
}
