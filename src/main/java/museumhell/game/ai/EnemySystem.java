package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.game.player.PlayerController;

import java.util.List;
import java.util.Random;

public class EnemySystem extends BaseAppState {

    private final AssetManager am;
    private final PhysicsSpace space;
    private final Node rootNode;
    private final MuseumLayout layout;
    private final PlayerController player;

    private float timeElapsed = 0f;
    private boolean spawned = false;
    private Enemy enemy;

    public EnemySystem(AssetManager am, BulletAppState bullet, Node rootNode, MuseumLayout layout, PlayerController player) {
        this.am = am;
        this.space = bullet.getPhysicsSpace();
        this.rootNode = rootNode;
        this.layout = layout;
        this.player = player;
    }

    @Override
    public void update(float tpf) {
        if (!spawned) {
            timeElapsed += tpf;
            if (timeElapsed >= 5f) {
                spawnEnemy();
                spawned = true;
            }
        } else if (enemy != null) {
            enemy.update(tpf);
        }
    }

    private void spawnEnemy() {
        Random rnd = new Random();
        int floorIdx = rnd.nextInt(layout.floors().size());        // 0,1 o 2
        List<Room> rooms = layout.floors().get(floorIdx).rooms();
        Room r = rooms.get(rnd.nextInt(rooms.size()));
        float baseY = layout.yOf(floorIdx);
        System.out.println("[EnemySystem] Spawning guard en piso " + floorIdx + " sala " + r);
        enemy = new Enemy(am, space, player, r, baseY, rootNode);
        spawned = true;
    }


    @Override
    protected void initialize(Application application) {

    }

    @Override
    protected void cleanup(Application application) {

    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
}
