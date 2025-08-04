package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.utils.GeoUtil.Rect;

import java.util.List;

abstract class _0HorizontalBuilder {
    protected final Node root;
    protected final PhysicsSpace space;
    protected final AssetManager am;

    protected _0HorizontalBuilder(Node root, PhysicsSpace space, AssetManager am) {
        this.root = root;
        this.space = space;
        this.am = am;
    }

    public void build(int x, int z, int w, int d, List<Rect> holes, float y, float thickness) {
        var mat = makeMaterial();
        buildPatches(x, z, w, d, holes, y, thickness, mat);
    }

    protected abstract Material makeMaterial();

    protected void buildPatches(float x, float z, float w, float d, List<Rect> holes, float y, float t, Material mat) {
        for (Rect h : holes) {
            float hx1 = Math.max(h.x1(), x);
            float hx2 = Math.min(h.x2(), x + w);
            float hz1 = Math.max(h.z1(), z);
            float hz2 = Math.min(h.z2(), z + d);
            if (hx1 < hx2 && hz1 < hz2) {
                if (hx1 > x) buildPatches(x, z, hx1 - x, d, holes, y, t, mat);
                if (hx2 < x + w) buildPatches(hx2, z, x + w - hx2, d, holes, y, t, mat);
                if (hz1 > z) buildPatches(hx1, z, hx2 - hx1, hz1 - z, holes, y, t, mat);
                if (hz2 < z + d) buildPatches(hx1, hz2, hx2 - hx1, z + d - hz2, holes, y, t, mat);
                return;
            }
        }
        Geometry g = new Geometry("Patch", new Box(w * .5f, t * .5f, d * .5f));
        g.setMaterial(mat.clone());
        g.setLocalTranslation(x + w * .5f, y, z + d * .5f);
        g.addControl(new com.jme3.bullet.control.RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
