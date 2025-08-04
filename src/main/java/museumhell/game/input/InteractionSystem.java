package museumhell.game.input;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import museumhell.game.player.PlayerController;
import museumhell.ui.Prompt;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.engine.world.levelgen.Door;
import museumhell.game.loot.LootItem;
import museumhell.game.loot.LootSystem;

public class InteractionSystem extends BaseAppState {
    private final PlayerController player;
    private final WorldBuilder world;
    private final LootSystem loot;
    private final Prompt hud;

    public InteractionSystem(PlayerController pc, WorldBuilder world, LootSystem loot, Prompt hud) {
        this.player = pc;
        this.world = world;
        this.loot = loot;
        this.hud = hud;
    }

    @Override
    public void update(float tpf) {
        Vector3f p = player.getLocation();

        // Loot
        LootItem li = loot.nearestLoot(p, 1.5f);
        if (li != null) {
            hud.show("[ E ] Recoger");
            return;
        }

        // Puerta
        Door d = world.nearestDoor(p, 3.5f);
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
