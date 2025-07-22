package museumhell.world.loot;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

/**
 * Cubo naranja que hace de loot. No usa física: lo detectamos por distancia.
 */
public class LootItem extends Node {

    public LootItem(AssetManager am, Vector3f pos) {

        Geometry g = new Geometry("LootGeom", new Box(.5f, .5f, .5f));
        g.setMaterial(makeMat(am, ColorRGBA.Orange));
        attachChild(g);

        setLocalTranslation(pos);
    }

    private static Material makeMat(AssetManager am, ColorRGBA base) {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", base);
        m.setColor("Ambient", base.mult(0.4f));
        return m;
    }
}
