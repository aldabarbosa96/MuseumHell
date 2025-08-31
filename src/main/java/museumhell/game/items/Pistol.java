package museumhell.game.items;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import museumhell.engine.world.builders._6LightPlacer;
import museumhell.utils.media.AssetLoader;

import static com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive;

public class Pistol implements HandItem {
    private final _6LightPlacer lp;
    private final Spatial model;
    private final float scale = 3f;
    private final Quaternion fix = new Quaternion().fromAngles(FastMath.DEG_TO_RAD * -5f, 0f, FastMath.DEG_TO_RAD * -8f);

    private final float ahead = 0.15f;

    public Pistol(_6LightPlacer lp, AssetLoader assets, String modelKey) {
        this.lp = lp;
        this.model = assets.get(modelKey);
        this.model.setShadowMode(CastAndReceive);
    }

    @Override
    public String name() {
        return "Pistola";
    }

    @Override
    public void onEquip() {
        lp.setFlashlightEnabled(false);

        Node holder = new Node("pistolHolder");
        model.setLocalTranslation(0f, -0.03f, 0f);
        holder.attachChild(model);

        lp.attachFlashlightModel(holder, fix, scale, ahead);
    }

    @Override
    public void onUnequip() {
        lp.detachHandModel();
    }
}
