package museumhell.manager.interaction;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import museumhell.player.PlayerController;
import museumhell.ui.PromptHud;
import museumhell.world.WorldBuilder;
import museumhell.world.levelgen.Door;
import museumhell.world.loot.LootItem;
import museumhell.world.loot.LootManager;

public class InteractionManager extends BaseAppState {

    private final PlayerController player;
    private final WorldBuilder world;
    private final LootManager loot;
    private final PromptHud hud;

    public InteractionManager(PlayerController pc, WorldBuilder world, LootManager loot, PromptHud hud) {
        this.player = pc;
        this.world = world;
        this.loot = loot;
        this.hud = hud;
    }

    @Override
    public void update(float tpf) {
        Vector3f p = player.getLocation();

        // 1) ¿hay loot?
        LootItem li = loot.nearestLoot(p, 1.2f);
        if (li != null) {
            hud.show("[ E ] Recoger");
            return;
        }

        // 2) ¿hay puerta?
        Door d = world.nearestDoor(p, 2.0f);
        if (d != null) {
            hud.show(d.isOpen() ? "[ E ] Cerrar puerta" : "[ E ] Abrir puerta");
            return;
        }

        hud.hide();
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
