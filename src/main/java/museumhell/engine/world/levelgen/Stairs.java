package museumhell.engine.world.levelgen;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

public final class Stairs {

    public static final float WIDTH = 2.5f;   // ancho libre de la escalera
    public static final float STEP_H = 0.30f;  // alto de cada peldaño
    public static final float STEP_DEPTH = 0.40f;  // huella de cada peldaño

    public static void add(Node root, PhysicsSpace ps, AssetManager am, Vector3f base, float floorHeight) {

        int steps = (int) Math.ceil(floorHeight / STEP_H);
        Material mat = makeMat(am);

        for (int i = 0; i < steps; i++) {

            float h = STEP_H;
            float yCenter = base.y + h * .5f + i * STEP_H;
            float zCenter = base.z + STEP_DEPTH * .5f + i * STEP_DEPTH;

            Box shape = new Box(WIDTH * .5f, h * .5f, STEP_DEPTH * .5f);
            Geometry g = new Geometry("StairStep_" + i, shape);
            g.setMaterial(mat.clone());

            g.setLocalTranslation(base.x, yCenter, zCenter);
            g.addControl(new RigidBodyControl(0));

            root.attachChild(g);
            ps.add(g);
        }
    }

    /* ─────────────────── helpers ─────────────────── */

    private static Material makeMat(AssetManager am) {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", ColorRGBA.Gray);
        m.setColor("Ambient", ColorRGBA.Gray.mult(0.35f));
        return m;
    }

    private Stairs() {
    }   // utilidad estática: no instanciable
}
