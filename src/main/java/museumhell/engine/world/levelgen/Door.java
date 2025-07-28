package museumhell.engine.world.levelgen;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

public class Door {
    private static final float SPEED = 4f;
    private static final float PROTRUDE = 0.1f;

    private final Geometry geo;
    private final RigidBodyControl body;
    private final Vector3f closedPos;
    private final Vector3f openPos;
    private boolean targetOpen = false;
    private float progress = 0f;

    public Door(AssetManager am, PhysicsSpace space, Vector3f center, float w, float h, float t, Vector3f offset) {
        closedPos = center.clone();
        Vector3f dir = offset.normalize();
        openPos = center.add(offset).subtract(dir.mult(PROTRUDE));

        geo = new Geometry("Door", new Box(w * .5f, h * .5f, t * .5f));
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", ColorRGBA.White);
        m.setColor("Ambient", ColorRGBA.Black.mult(0.25f));
        geo.setMaterial(m);
        geo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geo.setLocalTranslation(closedPos);

        body = new RigidBodyControl(0);
        geo.addControl(body);
        space.add(body);
        body.setKinematic(true);
        body.setPhysicsLocation(closedPos);
    }

    public Geometry getSpatial() {
        return geo;
    }

    public Vector3f getAccessPoint() {
        return closedPos;
    }

    public boolean isOpen() {
        return progress >= 0.99f;
    }

    public void toggle() {
        targetOpen = !targetOpen;
    }

    public void update(float tpf) {
        float dirSign = targetOpen ? +1f : -1f;
        progress = FastMath.clamp(progress + dirSign * (SPEED * tpf) / openPos.distance(closedPos), 0f, 1f);

        Vector3f pos = FastMath.interpolateLinear(progress, closedPos, openPos);
        geo.setLocalTranslation(pos);
        body.setPhysicsLocation(pos);
        body.setEnabled(progress < 1f);
    }
}
