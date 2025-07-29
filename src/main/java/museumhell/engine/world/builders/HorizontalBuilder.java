package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.utils.GeoUtil.Rect;

import java.util.List;

abstract class HorizontalBuilder {
    protected final Node root;
    protected final PhysicsSpace space;
    protected final AssetManager am;

    protected HorizontalBuilder(Node root, PhysicsSpace space, AssetManager am) {
        this.root = root;
        this.space = space;
        this.am = am;
    }

    public void build(int x, int z, int w, int d, List<Rect> holes, float y, float thickness) {
        var mat = makeMaterial();
        buildPatches(x, z, w, d, holes, y, thickness, mat);
    }

    protected abstract Material makeMaterial();


    private void buildPatches(int x, int z, int w, int d, List<Rect> holes, float y, float t, Material mat) {

        for (Rect v : holes) {
            float hx1 = Math.max(v.x1(), x);
            float hx2 = Math.min(v.x2(), x + w);
            float hz1 = Math.max(v.z1(), z);
            float hz2 = Math.min(v.z2(), z + d);

            if (hx1 < hx2 && hz1 < hz2) {
                if (hx1 > x) makePatch(x, z, hx1 - x, d, y, t, mat);
                if (hx2 < x + w) makePatch(hx2, z, x + w - hx2, d, y, t, mat);
                if (hz1 > z) makePatch(hx1, z, hx2 - hx1, hz1 - z, y, t, mat);
                if (hz2 < z + d) makePatch(hx1, hz2, hx2 - hx1, z + d - hz2, y, t, mat);
                return;
            }
        }
        makePatch(x, z, w, d, y, t, mat);
    }


    private void makePatch(float x, float z, float w, float d, float y, float t, Material mat) {
        Geometry g = new Geometry("Patch", new Box(w * .5f, t * .5f, d * .5f));
        g.setMaterial(mat.clone());
        g.setLocalTranslation(x + w * .5f, y, z + d * .5f);
        g.addControl(new com.jme3.bullet.control.RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
