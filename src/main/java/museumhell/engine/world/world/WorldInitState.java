package museumhell.engine.world.world;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.generator.MuseumGenerator;
import museumhell.engine.world.levelgen.roomObjects.SecurityCamera;
import museumhell.utils.AssetLoader;

import java.util.List;

import static museumhell.utils.ConstantManager.WALL_T;

public class WorldInitState extends BaseAppState {
    private SecurityCamera securityCameraBuilder;
    private WorldBuilder worldBuilder;
    private MuseumLayout museumLayout;

    public WorldInitState(AssetManager assetManager, Node rootNode, BulletAppState physics, AssetLoader visuals, Spatial cameraBase) {
        // 1) Generar layout
        museumLayout = MuseumGenerator.generate(150, 125, 3, System.nanoTime());

        // 2) Construir mundo
        worldBuilder = new WorldBuilder(assetManager, rootNode, physics.getPhysicsSpace(), visuals);
        worldBuilder.build(museumLayout);

        // 3) Inicializar beacons de cada sala
        float floorH = museumLayout.floorHeight();
        for (int i = 0; i < museumLayout.floors().size(); i++) {
            float y0 = museumLayout.yOf(i);
            List<Room> rooms = museumLayout.floors().get(i).rooms();
            worldBuilder.getLightPlacer().initRoomBeacons(rooms, y0, floorH);
        }

        // 4) Preparar cámaras fijas
        cameraBase.scale(0.5f);
        float baseExtrusion = 1.25f;
        float cameraExtrusion = baseExtrusion + WALL_T * 0.5f * FastMath.sqrt(2f);
        securityCameraBuilder = new SecurityCamera(rootNode, cameraBase, cameraExtrusion);
        securityCameraBuilder.build(museumLayout);
    }

    @Override
    protected void initialize(Application app) {
    }

    public WorldBuilder getWorldBuilder() {
        return worldBuilder;
    }

    public MuseumLayout getMuseumLayout() {
        return museumLayout;
    }

    public SecurityCamera getCameraBuilder() {
        return securityCameraBuilder;
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

