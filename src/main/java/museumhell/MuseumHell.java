package museumhell;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import museumhell.engine.player.PlayerController;
import museumhell.engine.world.WorldBuilder;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.generator.MuseumGenerator;
import museumhell.game.input.InputSystem;
import museumhell.game.interaction.InteractionSystem;
import museumhell.game.loot.LootSystem;
import museumhell.ui.Hud;
import museumhell.ui.Prompt;
import org.codehaus.groovy.util.FastArray;

import java.awt.*;

public class MuseumHell extends SimpleApplication {

    private BulletAppState physics;
    private WorldBuilder world;
    private PlayerController player;
    private InputSystem input;

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

        rootNode.addLight(new AmbientLight(ColorRGBA.White.mult(.1f)));

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

        /* ---------- LOOT SPAWN ---------- */
        museum.floors().forEach(f -> f.rooms().stream().skip(1).filter(r -> Math.random() > .5).forEach(r -> loot.scatter(r, 1 + (int) (Math.random() * 3))));

        cam.setFrustumNear(.55f);
    }

    @Override
    public void simpleUpdate(float tpf) {
        player.update(tpf);
        input.update(tpf);
        world.update(tpf);

        Vector3f eye = player.getLocation().add(0, .4f, 0).addLocal(cam.getDirection().mult(-.25f));
        cam.setLocation(eye);
    }
}
