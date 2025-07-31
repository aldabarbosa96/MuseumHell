// src/main/java/museumhell/game/ai/EnemySystem.java
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
import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.player.PlayerController;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;

public class EnemySystem extends BaseAppState {
    private final AssetManager am;
    private final PhysicsSpace space;
    private final Node rootNode;
    private final MuseumLayout layout;
    private final WorldBuilder world;
    private final PlayerController player;

    private float timeElapsed;
    private boolean spawned;
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
        int floorIdx = rnd.nextInt(layout.floors().size());
        List<Room> rooms = layout.floors().get(floorIdx).rooms();
        Room startRoom = rooms.get(rnd.nextInt(rooms.size()));
        float baseY = layout.yOf(floorIdx);

        enemy = new Enemy(am, space, player, world, startRoom, baseY, rootNode);

        List<Vector3f> patrol = buildPatrolRoute(startRoom, floorIdx, 5);
        enemy.setPatrolPoints(patrol);

        Vector3f spawnPos = startRoom.center3f(baseY + 0.5f);
        enemy.setLocalTranslation(spawnPos);
        CharacterControl cc = enemy.getControl(CharacterControl.class);
        cc.setPhysicsLocation(spawnPos);
    }

    private List<Vector3f> buildPatrolRoute(Room from, int floorIdx, int length) {
        float y = layout.yOf(floorIdx) + 0.5f;
        List<Connection> conns = layout.floors().get(floorIdx).conns();
        List<Room> visited = new ArrayList<>();
        List<Vector3f> route = new ArrayList<>();

        Room current = from;
        visited.add(current);

        for (int i = 0; i < length; i++) {
            Room finalCurrent = current;
            Room finalCurrent1 = current;
            List<Connection> options = conns.stream().filter(c -> c.a() == finalCurrent || c.b() == finalCurrent).filter(c -> {
                Room other = (c.a() == finalCurrent1) ? c.b() : c.a();
                return !visited.contains(other);
            }).toList();
            if (options.isEmpty()) break;
            Connection sel = options.get(rnd.nextInt(options.size()));
            Room next = (sel.a() == current) ? sel.b() : sel.a();

            float doorX, doorZ;
            switch (sel.dir()) {
                case NORTH -> {
                    float minX = Math.max(current.x(), next.x());
                    float maxX = Math.min(current.x() + current.w(), next.x() + next.w());
                    doorX = (minX + maxX) * 0.5f;
                    doorZ = current.z();
                }
                case SOUTH -> {
                    float minX = Math.max(current.x(), next.x());
                    float maxX = Math.min(current.x() + current.w(), next.x() + next.w());
                    doorX = (minX + maxX) * 0.5f;
                    doorZ = current.z() + current.h();
                }
                case EAST -> {
                    float minZ = Math.max(current.z(), next.z());
                    float maxZ = Math.min(current.z() + current.h(), next.z() + next.h());
                    doorZ = (minZ + maxZ) * 0.5f;
                    doorX = current.x() + current.w();
                }
                case WEST -> {
                    float minZ = Math.max(current.z(), next.z());
                    float maxZ = Math.min(current.z() + current.h(), next.z() + next.h());
                    doorZ = (minZ + maxZ) * 0.5f;
                    doorX = current.x();
                }
                default -> throw new IllegalStateException();
            }

            route.add(new Vector3f(doorX, y, doorZ));
            route.add(next.center3f(y));

            visited.add(next);
            current = next;
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
