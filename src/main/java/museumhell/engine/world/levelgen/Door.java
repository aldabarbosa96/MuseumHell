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
    private static final float SPEED = 3.5f;
    private static final float PROTRUDE = 0.1f;

    private final Geometry geo;
    private final RigidBodyControl body;
    private final Vector3f closedPos;
    private final Vector3f openPos;
    private boolean targetOpen = false;
    private float progress = 0f;

    public Door(AssetManager am, PhysicsSpace space, Vector3f center, float w, float h, float t, Vector3f offset) {
        // La puerta cerrada se sitúa exactamente en el centro del hueco
        closedPos = center.clone();
        // Dirección normalizada para el desplazamiento de apertura
        Vector3f dir = offset.normalize();
        // Puerta abierta: desplazamiento completo
        // más un pequeño sobresaliente en sentido contrario al offset
        openPos = center.add(offset).subtract(dir.mult(PROTRUDE));

        // Crear la geometría de la puerta
        geo = new Geometry("Door", new Box(w * .5f, h * .5f, t * .5f));
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", ColorRGBA.DarkGray);
        m.setColor("Ambient", ColorRGBA.DarkGray.mult(0.4f));
        geo.setMaterial(m);
        geo.setLocalTranslation(closedPos);

        // Configurar física kinemática
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
        // Ajusta el progreso de apertura/cierre
        float dirSign = targetOpen ? +1f : -1f;
        progress = FastMath.clamp(progress + dirSign * (SPEED * tpf) / openPos.distance(closedPos), 0f, 1f);

        // Interpola la posición y actualiza geometría y física
        Vector3f pos = FastMath.interpolateLinear(progress, closedPos, openPos);
        geo.setLocalTranslation(pos);
        body.setPhysicsLocation(pos);
        // Desactiva la colisión cuando está completamente abierta
        body.setEnabled(progress < 1f);
    }
}
