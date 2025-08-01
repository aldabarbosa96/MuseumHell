package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.player.PlayerController;

import java.util.*;

public class EnemySystem extends BaseAppState {
    private final AssetManager am;
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
        if (enemy == null) {
            timer += tpf;
            if (timer >= 5f) {
                spawnEnemy();
            }
        } else {
            enemy.update(tpf);
            if (enemy.isPatrolFinished()) {
                // Regenera una ruta distinta desde la misma sala de spawn
                List<Vector3f> pts = buildFullPatrolRoute(spawnRoom, spawnFloorIdx);
                enemy.setPatrolPoints(pts);
            }
        }
    }

    private void spawnEnemy() {
        spawnFloorIdx = rnd.nextInt(layout.floors().size());
        List<Room> rooms = layout.floors().get(spawnFloorIdx).rooms();
        spawnRoom = rooms.get(rnd.nextInt(rooms.size()));
        float y = layout.yOf(spawnFloorIdx);

        enemy = new Enemy(am, space, player, world, spawnRoom, y, rootNode);
        enemy.setPatrolPoints(buildFullPatrolRoute(spawnRoom, spawnFloorIdx));

        Vector3f pos = spawnRoom.center3f(y + 0.5f);
        enemy.setLocalTranslation(pos);
        enemy.getControl(com.jme3.bullet.control.CharacterControl.class).setPhysicsLocation(pos);
    }

    private List<Vector3f> buildFullPatrolRoute(Room start, int fIdx) {
        float y = layout.yOf(fIdx) + 0.5f;
        var conns = layout.floors().get(fIdx).conns();
        List<Vector3f> route = new ArrayList<>();
        Set<Room> visited = new HashSet<>();
        Deque<Room> stack = new ArrayDeque<>();

        stack.push(start);
        visited.add(start);

        while (!stack.isEmpty()) {
            Room cur = stack.peek();
            var opts = conns.stream().filter(c -> c.a() == cur || c.b() == cur).filter(c -> !visited.contains(c.a() == cur ? c.b() : c.a())).toList();

            if (!opts.isEmpty()) {
                var sel = opts.get(rnd.nextInt(opts.size()));
                Room next = (sel.a() == cur ? sel.b() : sel.a());

                // centro de la puerta:
                float dx1 = (Math.max(cur.x(), next.x()) + Math.min(cur.x() + cur.w(), next.x() + next.w())) * 0.5f;
                float dz1 = (Math.max(cur.z(), next.z()) + Math.min(cur.z() + cur.h(), next.z() + next.h())) * 0.5f;
                float dx, dz;
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
                    case WEST -> {
                        dz = dz1;
                        dx = cur.x();
                    }
                    default -> throw new IllegalStateException("Dirección desconocida: " + sel.dir());
                }
                route.add(new Vector3f(dx, y, dz));
                // centro de la sala siguiente:
                route.add(next.center3f(y));

                visited.add(next);
                stack.push(next);
            } else {
                stack.pop();
                if (!stack.isEmpty()) {
                    route.add(stack.peek().center3f(y));
                }
            }
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
