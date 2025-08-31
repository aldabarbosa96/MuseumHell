package museumhell.game.items;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.shape.Sphere;
import museumhell.engine.world.builders._6LightPlacer;
import museumhell.utils.media.AssetLoader;

import static com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive;

public class Pistol implements HandItem {
    private final _6LightPlacer lp;
    private final Spatial model;
    private Node holder;
    private Node muzzle;
    private final float scale = 3f;
    private final Quaternion fix = new Quaternion().fromAngles(FastMath.DEG_TO_RAD * -5f, 0f, FastMath.DEG_TO_RAD * -8f);
    private static final float MUZZLE_Y_FINE = 0.0135f;
    private final float ahead = 0.15f;
    private static final float PROJECTILE_RADIUS = 0.04f;
    private static final float SPAWN_BACK = 0.012f;

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
        holder = new Node("pistolHolder");
        model.setLocalTranslation(0f, -0.03f, 0f);
        holder.attachChild(model);
        lp.attachFlashlightModel(holder, fix, scale, ahead);

        muzzle = new Node("Muzzle");
        muzzle.setLocalTranslation(computeMuzzleLocal());
        holder.attachChild(muzzle);
    }

    @Override
    public void onUnequip() {
        lp.detachHandModel();
        holder = null;
        muzzle = null;
    }

    public void fire(SimpleApplication app, PhysicsSpace space) {
        if (muzzle == null) return;

        Vector3f origin = muzzle.getWorldTranslation().clone();
        Vector3f dir = muzzle.getWorldRotation().mult(Vector3f.UNIT_Z).normalizeLocal();
        origin.addLocal(dir.mult(-SPAWN_BACK));

        Sphere sph = new Sphere(12, 16, PROJECTILE_RADIUS);
        Geometry g = new Geometry("Projectile", sph);
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", ColorRGBA.Red);
        m.setColor("Ambient", ColorRGBA.Red.mult(0.35f));
        m.setColor("Specular", ColorRGBA.Red);
        m.setFloat("Shininess", 8f);
        g.setMaterial(m);
        g.setLocalTranslation(origin);
        g.setShadowMode(CastAndReceive);

        var shape = new SphereCollisionShape(PROJECTILE_RADIUS);
        var body = new RigidBodyControl(shape, 0.2f);
        g.addControl(body);
        app.getRootNode().attachChild(g);
        space.add(body);

        float speed = 75f;
        body.setLinearVelocity(dir.mult(speed));
        body.setCcdMotionThreshold(0.01f);
        body.setCcdSweptSphereRadius(PROJECTILE_RADIUS);
        body.setGravity(new Vector3f(0, -7.5f, 0));

        g.addControl(new AbstractControl() {
            float life = 2.5f;
            @Override protected void controlUpdate(float tpf) {
                life -= tpf;
                if (life <= 0f) {
                    space.remove(body);
                    if (spatial.getParent() != null) spatial.removeFromParent();
                    spatial.removeControl(this);
                }
            }
            @Override protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
        });
    }


    private Vector3f computeMuzzleLocal() {
        BoundingBox bb = localBounds(model);
        Vector3f center = bb.getCenter().clone();
        Vector3f ext = bb.getExtent(new Vector3f());
        Vector3f min = center.subtract(ext.clone());
        Vector3f max = center.add(ext.clone());
        float xMid = (min.x + max.x) * 0.5f;
        Vector3f p = new Vector3f(xMid, max.y - MUZZLE_Y_FINE, max.z);
        return p.add(model.getLocalTranslation());
    }

    private static BoundingBox localBounds(Spatial s) {
        return boundsRec(s, Transform.IDENTITY);
    }

    private static BoundingBox boundsRec(Spatial s, Transform acc) {
        if (s instanceof Geometry g) {
            Mesh mesh = g.getMesh();
            if (mesh.getBound() == null) mesh.updateBound();
            BoundingBox bb = (BoundingBox) mesh.getBound();
            return transformBoundingBox(bb, acc.combineWithParent(g.getLocalTransform()));
        } else if (s instanceof Node n) {
            BoundingBox out = null;
            for (Spatial c : n.getChildren()) {
                BoundingBox b = boundsRec(c, acc.combineWithParent(((Node) s).getLocalTransform()));
                if (b == null) continue;
                out = (out == null) ? b : merge(out, b);
            }
            return out;
        } else {
            return null;
        }
    }

    private static BoundingBox merge(BoundingBox a, BoundingBox b) {
        Vector3f ac = a.getCenter(new Vector3f());
        Vector3f ae = a.getExtent(new Vector3f());
        Vector3f bc = b.getCenter(new Vector3f());
        Vector3f be = b.getExtent(new Vector3f());

        Vector3f amin = ac.subtract(ae);
        Vector3f amax = ac.add(ae);
        Vector3f bmin = bc.subtract(be);
        Vector3f bmax = bc.add(be);

        Vector3f min = new Vector3f(Math.min(amin.x, bmin.x), Math.min(amin.y, bmin.y), Math.min(amin.z, bmin.z));
        Vector3f max = new Vector3f(Math.max(amax.x, bmax.x), Math.max(amax.y, bmax.y), Math.max(amax.z, bmax.z));
        Vector3f center = min.add(max).multLocal(0.5f);
        Vector3f extent = max.subtract(center);
        return new BoundingBox(center, extent.x, extent.y, extent.z);
    }

    private static BoundingBox transformBoundingBox(BoundingBox bb, Transform t) {
        Vector3f c = bb.getCenter(new Vector3f());
        Vector3f e = bb.getExtent(new Vector3f());

        Vector3f[] pts = new Vector3f[]{c.add(new Vector3f(+e.x, +e.y, +e.z)), c.add(new Vector3f(+e.x, +e.y, -e.z)), c.add(new Vector3f(+e.x, -e.y, +e.z)), c.add(new Vector3f(+e.x, -e.y, -e.z)), c.add(new Vector3f(-e.x, +e.y, +e.z)), c.add(new Vector3f(-e.x, +e.y, -e.z)), c.add(new Vector3f(-e.x, -e.y, +e.z)), c.add(new Vector3f(-e.x, -e.y, -e.z))};

        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (Vector3f p : pts) {
            Vector3f q = t.transformVector(p, null);
            min.minLocal(q);
            max.maxLocal(q);
        }

        Vector3f center = min.add(max).multLocal(0.5f);
        Vector3f extent = max.subtract(center);
        return new BoundingBox(center, extent.x, extent.y, extent.z);
    }
}
