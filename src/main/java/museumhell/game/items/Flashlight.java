package museumhell.game.items;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.scene.Spatial;
import museumhell.engine.world.builders._6LightPlacer;
import museumhell.utils.media.AssetLoader;

import static com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive;

public class Flashlight implements HandItem {
    private final _6LightPlacer lp;
    private final Spatial model;
    private final Quaternion fix = new Quaternion().fromAngles(FastMath.DEG_TO_RAD * -10f, FastMath.PI, FastMath.DEG_TO_RAD * -15f);
    private final float scale = 0.2f;
    private final float ahead = 0.15f;

    public Flashlight(_6LightPlacer lp, AssetLoader assets) {
        this.lp = lp;
        this.model = assets.get("lantern1");
        this.model.setShadowMode(CastAndReceive);
    }

    @Override
    public String name() {
        return "Linterna";
    }

    @Override
    public void onEquip() {
        lp.attachFlashlightModel(model, fix, scale, ahead);
        lp.setFlashlightEnabled(true);
    }

    @Override
    public void onUnequip() {
        lp.setFlashlightEnabled(false);
        lp.detachHandModel();
    }
}
