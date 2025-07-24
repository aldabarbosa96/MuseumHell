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

    private final List<LootItem> items = new ArrayList<>();
    private int collected = 0;

    public LootSystem(AssetManager am, Node root, PhysicsSpace space, PlayerController player, Hud hud) {
        this.am = am;
        this.root = root;
        this.space = space;
        this.player = player;
        this.hud = hud;
    }


    public void scatter(Room room, int n) {
        float halfSize = 0.25f;                    // mitad del cubo
        float wallThickness = 0.33f;                    // igual que en WallBuilder
        float margin = wallThickness + halfSize + 0.05f;

        // Área de muestreo XZ que garantice que no toquemos muros
        float xMin = room.x() + margin;
        float xMax = room.x() + room.w() - margin;
        float zMin = room.z() + margin;
        float zMax = room.z() + room.h() - margin;
        if (xMax <= xMin || zMax <= zMin) return;

        for (int i = 0; i < n; i++) {
            float x = ThreadLocalRandom.current().nextFloat() * (xMax - xMin) + xMin;
            float z = ThreadLocalRandom.current().nextFloat() * (zMax - zMin) + zMin;

            // 1) Ray‑cast desde muy arriba hacia abajo
            Vector3f from = new Vector3f(x, 50f, z);
            Vector3f to = new Vector3f(x, -50f, z);
            List<PhysicsRayTestResult> hits = space.rayTest(from, to);

            // 2) Buscamos el primer hit (suelo) con la mayor Y
            float bestY = Float.NEGATIVE_INFINITY;
            for (PhysicsRayTestResult hit : hits) {
                float yHit = hit.getHitFraction();
                if (yHit > bestY) {
                    bestY = yHit;
                }
            }
            // si no chocó con nada, lo ponemos a 0.5
            float y = (bestY == Float.NEGATIVE_INFINITY) ? 0.5f : bestY + halfSize + 0.01f;

            LootItem li = new LootItem(am, new Vector3f(x, y, z));
            root.attachChild(li);
            items.add(li);
        }
        hud.set(collected, items.size());
    }


    public void distributeAcrossRooms(List<Room> rooms, int minTotal, int maxTotal, int maxPerRoom) {
        // 1) copia de salas válidas
        List<Room> pool = new ArrayList<>(rooms);
        // 2) total aleatorio [minTotal…maxTotal]
        int remaining = ThreadLocalRandom.current().nextInt(minTotal, maxTotal + 1);
        // 3) conteo por sala
        Map<Room, Integer> count = new HashMap<>();
        while (remaining > 0 && !pool.isEmpty()) {
            Room r = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            int c = count.getOrDefault(r, 0);
            if (c < maxPerRoom) {
                count.put(r, c + 1);
                remaining--;
                if (c + 1 == maxPerRoom) {
                    pool.remove(r);
                }
            } else {
                pool.remove(r);
            }
        }
        // 4) esparcir
        count.forEach(this::scatter);
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
