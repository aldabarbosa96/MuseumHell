package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import museumhell.utils.GeoUtil.Rect;

import java.util.List;


public class _5CeilBuilder0 extends _0HorizontalBuilder {

    public _5CeilBuilder0(Node root, PhysicsSpace space, AssetManager am) {
        super(root, space, am);
    }

    @Override
    protected Material makeMaterial() {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);

        // Gris muy tenue con ligera variación aleatoria
        float k = 0.04f + (float) Math.random() * 0.03f;
        ColorRGBA base = ColorRGBA.Brown.mult(k);

        m.setColor("Ambient", base);
        m.setColor("Diffuse", base);
        m.setColor("Specular", ColorRGBA.Brown.mult(0.1f));
        m.setFloat("Shininess", 1f);

        return m;
    }


    public void buildPatches(int x, int z, int w, int d, List<Rect> holes, float y, float thickness) {
        super.build(x, z, w, d, holes, y, thickness);
    }
}
