package museumhell.world.loot;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.ui.Hud;
import museumhell.player.PlayerController;
import museumhell.world.levelgen.Room;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gestiona lista de loot y su recogida mediante distancia.
 */
public class LootManager extends BaseAppState {

    private final AssetManager am;
    private final Node root;
    private final PlayerController player;
    private final Hud hud;

    private final List<LootItem> items = new ArrayList<>();
    private int collected = 0;

    public LootManager(AssetManager am, Node root, PlayerController player, Hud hud) {
        this.am = am;
        this.root = root;
        this.player = player;
        this.hud = hud;
    }

    public void scatter(Room room, int n) {
        for (int i = 0; i < n; i++) {
            float x = room.x() + 1f + (float) Math.random() * (room.w() - 2f);
            float z = room.z() + 1f + (float) Math.random() * (room.h() - 2f);
            LootItem li = new LootItem(am, new Vector3f(x, .5f, z));
            root.attachChild(li);
            items.add(li);
        }
        hud.set(collected, items.size());
    }

    /* ---------- BaseAppState ---------- */

    @Override
    public void update(float tpf) {
        Vector3f p = player.getLocation();
        Iterator<LootItem> it = items.iterator();
        final float R2 = 1.0f;      // distancia de recogida (m)
        while (it.hasNext()) {
            LootItem li = it.next();
            if (li.getWorldTranslation().distanceSquared(p) < R2) {
                root.detachChild(li);
                it.remove();
                collected++;
                hud.set(collected, collected + items.size());
            }
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
        hud.set(collected, collected + items.size());

    }

    @Override
    protected void onDisable() {
    }
}
