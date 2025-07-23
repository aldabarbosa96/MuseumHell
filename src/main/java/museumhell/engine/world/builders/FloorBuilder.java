package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.PhysicsSpace;
import museumhell.engine.world.WorldBuilder.Rect;

import java.util.List;

public class FloorBuilder {
    private final Node root;
    private final PhysicsSpace space;
    private final AssetManager assetManager;

    // Ahora sólo root, space y assetManager (sin wallThickness)
    public FloorBuilder(Node root, PhysicsSpace space, AssetManager assetManager) {
        this.root = root;
        this.space = space;
        this.assetManager = assetManager;
    }

    /**
     * Igual firma de antes, con tag y color.
     */
    public void buildPatches(int x, int z, int w, int d, List<Rect> holes, float y, float t, String tag, ColorRGBA col) {
        // Material “oscuro” idéntico al antiguo makeGeometry para suelos
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        // Generamos el “dark” que tenías antes:
        ColorRGBA dark = col.mult(0.05f + (float) Math.random() * 0.07f);
        mat.setColor("Ambient", dark);
        mat.setColor("Diffuse", dark);
        mat.setColor("Specular", ColorRGBA.Black);
        mat.setFloat("Shininess", 1f);
        // (sin polyOffset ni specular brillante)

        // Cortamos por huecos tal cual hacías
        for (Rect v : holes) {
            float hx1 = Math.max(v.x1(), x);
            float hx2 = Math.min(v.x2(), x + w);
            float hz1 = Math.max(v.z1(), z);
            float hz2 = Math.min(v.z2(), z + d);
            if (hx1 < hx2 && hz1 < hz2) {
                if (hx1 > x) makePatch(x, z, hx1 - x, d, y, t, tag + "_L", mat);
                if (hx2 < x + w) makePatch(hx2, z, x + w - hx2, d, y, t, tag + "_R", mat);
                if (hz1 > z) makePatch(hx1, z, hx2 - hx1, hz1 - z, y, t, tag + "_F", mat);
                if (hz2 < z + d) makePatch(hx1, hz2, hx2 - hx1, z + d - hz2, y, t, tag + "_B", mat);
                return;
            }
        }
        // sin hueco → parche completo
        makePatch(x, z, w, d, y, t, tag, mat);
    }

    private void makePatch(float x, float z, float w, float d, float y, float t, String name, Material mat) {
        Box shape = new Box(w * .5f, t * .5f, d * .5f);
        Geometry g = new Geometry(name, shape);
        g.setMaterial(mat.clone()); // clonar para que cada parche sea independiente
        g.setLocalTranslation(x + w * .5f, y, z + d * .5f);
        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
