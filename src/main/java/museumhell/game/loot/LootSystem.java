package museumhell.game.loot;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.ui.Hud;
import museumhell.game.player.PlayerController;
import museumhell.engine.world.levelgen.Room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


public class LootSystem extends BaseAppState {

    private final AssetManager am;
    private final Node root;
    private final PhysicsSpace space;
    private final PlayerController player;
    private final Hud hud;
    private final float floorHeight;

    private final List<LootItem> items = new ArrayList<>();
    private int collected = 0;

    public LootSystem(AssetManager am, Node root, PhysicsSpace space, PlayerController player, Hud hud, float floorHeight) {
        this.am = am;
        this.root = root;
        this.space = space;
        this.player = player;
        this.hud = hud;
        this.floorHeight = floorHeight;
    }


    public void scatter(Room room, int floorIdx, int n) {
        float halfSize = 0.25f;
        float wallThickness = 0.33f;
        float margin = wallThickness + halfSize + 0.05f;
        float baseY = floorIdx * floorHeight;
        float y = baseY + halfSize + 0.01f; // un pelín por encima del suelo

        // Área XZ con margen
        float xMin = room.x() + margin;
        float xMax = room.x() + room.w() - margin;
        float zMin = room.z() + margin;
        float zMax = room.z() + room.h() - margin;

        boolean hasSpace = xMax > xMin && zMax > zMin;

        for (int i = 0; i < n; i++) {
            float x, z;

            if (hasSpace) {
                x = ThreadLocalRandom.current().nextFloat() * (xMax - xMin) + xMin;
                z = ThreadLocalRandom.current().nextFloat() * (zMax - zMin) + zMin;
            } else {
                // SI NO HAY ESPACIO, caemos al centro de la sala para que algo
                Vector3f c = room.center3f(baseY);
                // Opcional: añade un poquitín de jitter para no stackear todo en el mismo punto
                x = c.x + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.2f;
                z = c.z + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.2f;
            }

            LootItem li = new LootItem(am, new Vector3f(x, y, z));
            root.attachChild(li);
            items.add(li);
        }

        hud.set(collected, items.size());
    }


    public void distributeAcrossRooms(List<Map.Entry<Room, Integer>> roomsWithFloor, int minTotal, int maxTotal, int maxPerRoom) {
        // 1) Creamos una copia mutable para extraer al azar
        List<Map.Entry<Room, Integer>> pool = new ArrayList<>(roomsWithFloor);

        // 2) Elegimos un total aleatorio entre minTotal y maxTotal (inclusive)
        int remaining = ThreadLocalRandom.current().nextInt(minTotal, maxTotal + 1);

        // 3) Contador de ítems asignados por sala
        Map<Map.Entry<Room, Integer>, Integer> count = new HashMap<>();

        // 4) Mientras queden ítems por asignar y salas disponibles
        while (remaining > 0 && !pool.isEmpty()) {
            int idx = ThreadLocalRandom.current().nextInt(pool.size());
            Map.Entry<Room, Integer> entry = pool.get(idx);
            int c = count.getOrDefault(entry, 0);

            if (c < maxPerRoom) {
                // Asignamos uno más a esta sala
                count.put(entry, c + 1);
                remaining--;
                // Si alcanza el máximo, la retiramos del pool
                if (c + 1 == maxPerRoom) {
                    pool.remove(idx);
                }
            } else {
                // Ya llenó su cupo, la retiramos
                pool.remove(idx);
            }
        }

        // 5) Finalmente, para cada sala asignada, esparcimos su número de ítems
        for (var e : count.entrySet()) {
            Room room = e.getKey().getKey();
            int floorIdx = e.getKey().getValue();
            int num = e.getValue();
            scatter(room, floorIdx, num);
        }
    }

    @Override
    public void update(float tpf) {
    }

    public void tryPickUp(Vector3f playerPos) {
        final float MAX2 = 1.2f * 1.2f;
        LootItem target = null;
        float best = MAX2;

        for (LootItem li : items) {
            Vector3f w = li.getWorldTranslation();
            float dx = w.x - playerPos.x;
            float dz = w.z - playerPos.z;
            float d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                target = li;
            }
        }

        if (target != null) {
            root.detachChild(target);
            items.remove(target);
            collected++;
            hud.set(collected, collected + items.size());
        }
    }

    public LootItem nearestLoot(Vector3f pos, float maxDist) {
        LootItem best = null;
        float best2 = maxDist * maxDist;
        for (LootItem li : items) {
            Vector3f w = li.getWorldTranslation();
            float dx = w.x - pos.x, dz = w.z - pos.z;
            float d2 = dx * dx + dz * dz;
            if (d2 < best2) {
                best2 = d2;
                best = li;
            }
        }
        return best;
    }

    @Override
    protected void initialize(Application app) {
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        hud.set(collected, collected + items.size());

    }

    @Override
    protected void onDisable() {
    }
}
