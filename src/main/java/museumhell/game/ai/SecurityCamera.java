package museumhell.game.ai;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

import java.util.List;

import static museumhell.utils.ConstantManager.*;


public class SecurityCamera {
    private final Spatial model;
    private final Vector3f pos;
    private final Vector3f dir;
    private final float fovRad;
    private final float range;

    private final PhysicsSpace space; // Para ray-cast.

    public SecurityCamera(Spatial model, Vector3f dir, PhysicsSpace ps) {
        this.model = model;
        this.pos = model.getWorldTranslation().clone();
        this.dir = dir.normalize();
        this.space = ps;
        this.fovRad = FastMath.DEG_TO_RAD * CAM_FOV_DEG;
        this.range = CAM_RANGE;
    }

    public boolean canSee(Vector3f target) {

        /* 1. Alcance y ángulo */
        Vector3f toTarget = target.subtract(pos);
        float dist = toTarget.length();
        if (dist > range) return false;

        float angle = dir.angleBetween(toTarget.normalizeLocal());
        if (angle > fovRad * 0.5f) return false;

        /* 2. Ray-cast hacia el jugador */
        List<PhysicsRayTestResult> hits = space.rayTest(pos, target);
        for (PhysicsRayTestResult hit : hits) {
            // Ignoramos la propia cámara.  También podrías filtrar por capas.
            if (hit.getCollisionObject().getUserObject() == model) continue;

            // Si algo se interpone antes del final (≈ jugador), no hay visión.
            if (hit.getHitFraction() < 0.99f) return false;
        }
        return true;
    }


    public Spatial getModel() {
        return model;
    }

    public Vector3f getPosition() {
        return pos;
    }

    public Vector3f getDirection() {
        return dir;
    }
}
