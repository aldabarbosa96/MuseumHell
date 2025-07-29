package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import museumhell.utils.GeoUtil.Rect;

import java.util.List;


public class FloorBuilder extends HorizontalBuilder {

    public FloorBuilder(Node root, PhysicsSpace space, AssetManager am) {
        super(root, space, am);
    }


    @Override
    protected Material makeMaterial() {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);

        // Marrón madera, con una leve variación aleatoria para evitar uniformidad.
        float k = 0.08f + (float) Math.random() * 0.05f;
        ColorRGBA base = ColorRGBA.White.mult(k);

        m.setColor("Ambient", base);
        m.setColor("Diffuse", base);
        m.setColor("Specular", ColorRGBA.White.mult(0.3f));
        m.setFloat("Shininess", 1);

        return m;
    }

    public void buildPatches(int x, int z, int w, int d, List<Rect> holes, float y, float thickness) {
        super.build(x, z, w, d, holes, y, thickness);
    }
}
