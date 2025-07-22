package museumhell.game.loot;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.ui.Hud;
import museumhell.engine.player.PlayerController;
import museumhell.engine.world.levelgen.Room;
import java.util.ArrayList;
import java.util.List;


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
    }

    public void tryPickUp(Vector3f playerPos) {
        final float MAX2 = 1.2f * 1.2f;
        LootItem target = null;
        float best = MAX2;

        for (LootItem li : items) {
            Vector3f w = li.getWorldTranslation();
            float dx = w.x - playerPos.x;
            float dz = w.z - playerPos.z;
            float d2 = dx*dx + dz*dz;
            if (d2 < best) { best = d2; target = li; }
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
            float d2 = dx*dx + dz*dz;          // plano XZ
            if (d2 < best2) { best2 = d2; best = li; }
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
