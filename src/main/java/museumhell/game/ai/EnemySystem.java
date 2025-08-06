package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.player.PlayerController;
import museumhell.utils.media.AssetLoader;
import museumhell.utils.media.AudioLoader;

import java.util.*;
import java.util.function.Supplier;

public class EnemySystem extends BaseAppState {
    private final AssetLoader am;
    private final AudioLoader audio;
    private PatrolPlanner planner;
    private final PhysicsSpace space;
    private final Node rootNode;
    private final MuseumLayout layout;
    private final WorldBuilder world;
    private final PlayerController player;
private final Random rnd = new Random();
    private Enemy enemy;
    private Room spawnRoom;
    private int spawnFloorIdx;
    private float timer = 0f;

    public EnemySystem(AssetLoader am, BulletAppState bullet, Node rootNode, MuseumLayout layout, WorldBuilder world, PlayerController player, AudioLoader audio) {
        this.am = am;
        this.space = bullet.getPhysicsSpace();
        this.rootNode = rootNode;
        this.layout = layout;
        this.world = world;
        this.player = player;
        this.audio = audio;
    }

    @Override
    public void update(float tpf) {
        if (enemy == null) {
            timer += tpf;
            if (timer >= 5f) {
                spawnEnemy();
            }
        } else {
            enemy.update(tpf);

        }
    }

    private void spawnEnemy() {

        // 1) planta y sala de aparición  ---------------------------
        spawnFloorIdx = rnd.nextInt(layout.floors().size());
        List<Room> rooms = layout.floors().get(spawnFloorIdx).rooms();
        spawnRoom = rooms.get(rnd.nextInt(rooms.size()));
        float baseY = layout.yOf(spawnFloorIdx);

        // 2) Planner para esa planta ------------------------------
        planner = new PatrolPlanner(layout, spawnFloorIdx);

        // 3) LAMBDA que Enemy usará cuando necesite un camino nuevo
        Supplier<List<Vector3f>> pathSupplier = () -> planner.randomRoute(enemy != null && enemy.currentRoom() != null ? enemy.currentRoom() : spawnRoom);

        // 4) Crear el enemigo -------------------------------------
        enemy = new Enemy(am, space, player, world, spawnRoom, baseY, rootNode, audio, pathSupplier);

        enemy.setPatrolPoints(planner.randomRoute(spawnRoom));

        Vector3f pos = spawnRoom.center3f(baseY + 0.5f);
        enemy.setLocalTranslation(pos);
        enemy.getControl(CharacterControl.class).setPhysicsLocation(pos);
    }

    @Override
    protected void initialize(Application app) {
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
