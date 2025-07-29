package museumhell.engine.world.levelgen.roomObjects;

import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.scene.Spatial;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;

import java.util.Random;

public class Camera {
    private final Node root;
    private final Spatial cameraBase;
    private final float extrusion;
    private final Random rnd = new Random();

    public Camera(Node root, Spatial cameraBase, float extrusion) {
        this.root = root;
        this.cameraBase = cameraBase;
        this.extrusion = extrusion;
    }

    public void build(MuseumLayout museum) {
        float floorH = museum.floorHeight();
        for (int f = 0; f < museum.floors().size(); f++) {
            for (Room r : museum.floors().get(f).rooms()) {
                if (rnd.nextBoolean()) continue;  // ~50% de salas
                placeInRoom(f, r, floorH);
            }
        }
    }

    private void placeInRoom(int floorIdx, Room r, float floorH) {
        float baseY = floorIdx * floorH;
        float yCam = baseY + floorH - 0.8f;

        float x1 = r.x() + 0.2f, x2 = r.x() + r.w() - 0.2f;
        float z1 = r.z() + 0.2f, z2 = r.z() + r.h() - 0.2f;
        Vector3f[] corners = new Vector3f[]{new Vector3f(x1, yCam, z1), new Vector3f(x2, yCam, z1), new Vector3f(x2, yCam, z2), new Vector3f(x1, yCam, z2)};

        Vector3f center = r.center3f(baseY + floorH * 0.5f);
        for (Vector3f pos0 : corners) {
            Vector3f dirToCenter = pos0.subtract(center).normalizeLocal();
            Vector3f normalOut = dirToCenter.negate();
            Vector3f posExtruded = pos0.add(normalOut.mult(extrusion));

            Spatial cam = cameraBase.clone();
            cam.setLocalTranslation(posExtruded);

            Quaternion q = new Quaternion().lookAt(dirToCenter, Vector3f.UNIT_Y);
            cam.setLocalRotation(q);

            root.attachChild(cam);
        }
    }
}