package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.player.PlayerController;

import java.util.List;
import java.util.Random;

public class EnemySystem extends BaseAppState {
    private final AssetManager am;
    private final PhysicsSpace space;
    private final Node rootNode;
    private final MuseumLayout layout;
    private final WorldBuilder world;
    private final PlayerController player;

    private float timer = 0f;
    private boolean spawned = false;
    private Enemy enemy;
    private final Random rnd = new Random();

    public EnemySystem(AssetManager am, BulletAppState bullet, Node rootNode, MuseumLayout layout, WorldBuilder world, PlayerController player) {
        this.am = am;
        this.space = bullet.getPhysicsSpace();
        this.rootNode = rootNode;
        this.layout = layout;
        this.world = world;
        this.player = player;
    }

    @Override
    public void update(float tpf) {
        if (!spawned) {
            timer += tpf;
            if (timer >= 5f) {
                spawnEnemy();
                spawned = true;
            }
        } else if (enemy != null) {
            enemy.update(tpf);
        }
    }

    private void spawnEnemy() {
        int floorIdx = rnd.nextInt(layout.floors().size());
        List<Room> rooms = layout.floors().get(floorIdx).rooms();
        Room start = rooms.get(rnd.nextInt(rooms.size()));
        float y = layout.yOf(floorIdx);

        enemy = new Enemy(am, space, player, world, start, y, rootNode);

        enemy.setPatrolPoints(buildPatrolRoute(start, floorIdx, 5));

        Vector3f pos = start.center3f(y + 0.5f);
        enemy.setLocalTranslation(pos);
        enemy.getControl(CharacterControl.class).setPhysicsLocation(pos);
    }

    private List<Vector3f> buildPatrolRoute(Room from, int fIdx, int len) {
        float y = layout.yOf(fIdx) + 0.5f;
        var conns = layout.floors().get(fIdx).conns();
        var route = new java.util.ArrayList<Vector3f>();
        var visited = new java.util.ArrayList<Room>();
        Room cur = from;
        visited.add(cur);

        for (int i = 0; i < len; i++) {
            Room finalCur = cur;
            Room finalCur1 = cur;
            var opts = conns.stream().filter(c -> c.a() == finalCur || c.b() == finalCur).filter(c -> !visited.contains(c.a() == finalCur1 ? c.b() : c.a())).toList();
            if (opts.isEmpty()) break;
            var sel = opts.get(rnd.nextInt(opts.size()));
            Room next = sel.a() == cur ? sel.b() : sel.a();
            float dx, dz;
            final float dx1 = (Math.max(cur.x(), next.x()) + Math.min(cur.x() + cur.w(), next.x() + next.w())) * 0.5f;
            final float dz1 = (Math.max(cur.z(), next.z()) + Math.min(cur.z() + cur.h(), next.z() + next.h())) * 0.5f;
            switch (sel.dir()) {
                case NORTH -> {
                    dx = dx1;
                    dz = cur.z();
                }
                case SOUTH -> {
                    dx = dx1;
                    dz = cur.z() + cur.h();
                }
                case EAST -> {
                    dz = dz1;
                    dx = cur.x() + cur.w();
                }
                default -> {
                    dz = dz1;
                    dx = cur.x();
                }
            }
            route.add(new Vector3f(dx, y, dz));
            route.add(next.center3f(y));
            visited.add(next);
            cur = next;
        }
        return route;
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
