package museumhell.world.levelgen;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

public class Door {

    private static final float SPEED = 3f;

    private final Geometry geo;
    private final RigidBodyControl body;

    private final Vector3f closedPos;
    private final Vector3f openPos;

    private boolean targetOpen = false;
    private float progress = 0f;

    public Door(AssetManager am, PhysicsSpace space, Vector3f center, float w, float h, float t, Vector3f offset) {

        closedPos = center.clone();
        openPos = center.add(offset);

        geo = new Geometry("Door", new Box(w * .5f, h * .5f, t * .5f));

        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", ColorRGBA.DarkGray);
        m.setColor("Ambient", ColorRGBA.DarkGray.mult(0.4f));
        geo.setMaterial(m);

        geo.setLocalTranslation(closedPos);

        body = new RigidBodyControl(0);
        geo.addControl(body);
        space.add(body);
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

        float dir = targetOpen ? 1f : -1f;
        progress = FastMath.clamp(progress + dir * (SPEED * tpf) / openPos.distance(closedPos), 0f, 1f);
        Vector3f newPos = FastMath.interpolateLinear(progress, closedPos, openPos);
        geo.setLocalTranslation(newPos);

        if (progress < 0.01f) {
            body.setPhysicsLocation(closedPos);
            body.setEnabled(true);
        } else if (progress > 0.99f) {
            body.setEnabled(false);
        }
    }
}
