package museumhell.game.ai.enemies;

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
        // 1) Planta y sala de aparición
        spawnFloorIdx = rnd.nextInt(layout.floors().size());
        List<Room> rooms = layout.floors().get(spawnFloorIdx).rooms();
        spawnRoom = rooms.get(rnd.nextInt(rooms.size()));
        float baseY = layout.yOf(spawnFloorIdx);

        // 2) Planner global (soporta puertas + escaleras entre plantas)
        planner = new PatrolPlanner(layout, world);

        // 3) Suppliers de rutas
        // 3.1) Ruta de patrulla aleatoria (cuando se agota la actual o se atasca)
        Supplier<List<Vector3f>> patrolSupplier = () -> planner.randomRoute(enemy != null && enemy.currentRoom() != null ? enemy.currentRoom() : spawnRoom);

        // 3.2) Ruta de persecución (sin centros)
        Supplier<List<Vector3f>> chaseSupplier = () -> {
            Room from = (enemy != null && enemy.currentRoom() != null) ? enemy.currentRoom() : spawnRoom;
            Room to = world.whichRoom(player.getLocation());
            Vector3f goal = player.getLocation().clone();
            return planner.routeToLean(from, to, goal);
        };


        // 4) Crear enemigo
        enemy = new Enemy(am, space, player, world, spawnRoom, baseY, rootNode, audio, patrolSupplier);

        // 5) Ruta inicial de patrulla y supplier de persecución
        enemy.setPatrolPoints(planner.randomRoute(spawnRoom));
        enemy.setChasePathSupplier(chaseSupplier);

        // 6) Colocar físicamente en la escena
        Vector3f pos = spawnRoom.center3f(baseY + 0.5f);
        enemy.setLocalTranslation(pos);
        enemy.getControl(CharacterControl.class).setPhysicsLocation(pos);
    }


    public void onAlarm(Room room) {
        if (enemy == null || room == null) return;

        Room start = (enemy.currentRoom() != null) ? enemy.currentRoom() : spawnRoom;

        Supplier<List<Vector3f>> alarmSupplier = () -> {
            Room from = (enemy.currentRoom() != null) ? enemy.currentRoom() : spawnRoom;
            return planner.routeTo(from, room);
        };

        // Ruta inicial lean hacia la sala de la alarma (sin centros intermedios)
        List<Vector3f> path = planner.routeToLean(start, room, null);
        if (path != null && !path.isEmpty()) {
            enemy.setAlarmChase(room, alarmSupplier, path);
        }
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
