package museumhell.engine.world.levelgen;

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

        // 1) calcula posiciones
        closedPos = center.clone();
        openPos = center.add(offset);

        // 2) crea geometría
        geo = new Geometry("Door", new Box(w * .5f, h * .5f, t * .5f));
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", ColorRGBA.DarkGray);
        m.setColor("Ambient", ColorRGBA.DarkGray.mult(0.4f));
        geo.setMaterial(m);
        geo.setLocalTranslation(closedPos);

        // 3) crea el RigidBodyControl (mass=0)
        body = new RigidBodyControl(0);
        geo.addControl(body);

        // 4) añádelo al espacio y **luego** marca kinematic
        space.add(body);
        body.setKinematic(true);
    }

    public Geometry getSpatial() {
        return geo;
    }

    /**
     * Punto donde el jugador pulsa para abrir/cerrar
     */
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
        // a) calcula interpolación
        float dir = targetOpen ? +1f : -1f;
        progress = FastMath.clamp(progress + dir * (SPEED * tpf) / openPos.distance(closedPos), 0f, 1f);

        // b) mueve geometría y collider
        Vector3f newPos = FastMath.interpolateLinear(progress, closedPos, openPos);
        geo.setLocalTranslation(newPos);
        body.setPhysicsLocation(newPos);

        // c) sólo bloquee físicas mientras NO esté completamente abierto
        body.setEnabled(progress < 1f);
    }
}
