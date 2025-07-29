package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import museumhell.utils.AssetLoader;
import museumhell.utils.GeoUtil.Rect;

import java.util.List;

public class _1FloorBuilder extends _0HorizontalBuilder {

    private final Spatial base;
    private final BoundingBox bb;
    private final float ox, oy, oz;

    public _1FloorBuilder(Node root, PhysicsSpace space, AssetManager am, AssetLoader assets) {
        super(root, space, am);
        base = assets.get("floor1");
        base.updateGeometricState();
        bb = (BoundingBox) base.getWorldBound();
        ox = bb.getCenter().x;
        oy = bb.getCenter().y;
        oz = bb.getCenter().z;
    }

    @Override
    protected Material makeMaterial() {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        float k = 0.08f + (float) Math.random() * 0.05f;
        ColorRGBA c = ColorRGBA.White.mult(k);
        m.setColor("Ambient", c);
        m.setColor("Diffuse", c);
        m.setColor("Specular", ColorRGBA.White.mult(0.3f));
        m.setFloat("Shininess", 1);
        return m;
    }

    private static boolean intersects(Rect h, int x, int z, int w, int d) {
        return h.x1() < x + w && h.x2() > x && h.z1() < z + d && h.z2() > z;
    }

    public void buildPatches(int x, int z, int w, int d, List<Rect> holes, float y, float thickness) {
        buildRegion(x, z, w, d, holes, y, thickness);
    }

    private void buildRegion(int x, int z, int w, int d, List<Rect> holes, float y, float thickness) {
        for (Rect h : holes) {
            if (!intersects(h, x, z, w, d)) continue;
            int hx1 = Math.max((int) h.x1(), x);
            int hx2 = Math.min((int) h.x2(), x + w);
            int hz1 = Math.max((int) h.z1(), z);
            int hz2 = Math.min((int) h.z2(), z + d);
            if (hx1 < hx2 && hz1 < hz2) {
                if (hx1 > x) buildRegion(x, z, hx1 - x, d, holes, y, thickness);
                if (hx2 < x + w) buildRegion(hx2, z, x + w - hx2, d, holes, y, thickness);
                if (hz1 > z) buildRegion(hx1, z, hx2 - hx1, hz1 - z, holes, y, thickness);
                if (hz2 < z + d) buildRegion(hx1, hz2, hx2 - hx1, z + d - hz2, holes, y, thickness);
                return;
            }
        }
        addMesh(x, z, w, d, y, thickness);
    }

    private void addMesh(int x, int z, int w, int d, float y, float thickness) {
        Spatial s = base.clone();
        float modelW = bb.getXExtent() * 2f;
        float modelD = bb.getZExtent() * 2f;
        float modelH = bb.getYExtent() * 2f;
        s.setLocalScale(w / modelW, thickness / modelH, d / modelD);
        s.setLocalTranslation(x + w * .5f - ox * w / modelW, y - oy * thickness / modelH, z + d * .5f - oz * d / modelD);
        var body = new RigidBodyControl(CollisionShapeFactory.createMeshShape(s), 0);
        s.addControl(body);
        root.attachChild(s);
        space.add(body);
    }
}
