package museumhell.game.ai;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.scene.Spatial;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;

import java.util.*;

import static museumhell.utils.ConstantManager.WALL_T;

public class SecurityCamera {
    private final Node root;
    private final Spatial cameraBase;
    private final float extrusion;
    private final Random rnd = new Random();
    private final List<CameraData> camInfos = new ArrayList<>();

    public SecurityCamera(Node root, Spatial cameraBase, float extrusion) {
        this.root = root;
        this.cameraBase = cameraBase;
        this.extrusion = extrusion;
    }

    public record CameraData(Spatial spat, Vector3f dir, Room room, float baseY, float floorH) {
    }

    public void build(MuseumLayout museum) {
        float floorH = museum.floorHeight();
        for (int f = 0; f < museum.floors().size(); f++) {
            List<Room> rooms = museum.floors().get(f).rooms();
            for (Room r : rooms) {
                if (rnd.nextFloat() >= 0.3f) continue;
                placeInRoom(f, r, floorH);
            }
        }
    }

    private void placeInRoom(int floorIdx, Room r, float floorH) {
        float baseY = floorIdx * floorH;
        Vector3f[] corners = getVectors(r, floorH, baseY);
        Vector3f center = r.center3f(baseY + floorH * 0.5f);

        int camsHere = rnd.nextInt(3) + 1;
        List<Vector3f> list = Arrays.asList(corners);
        Collections.shuffle(list, rnd);

        for (int i = 0; i < camsHere; i++) {
            Vector3f cornerPos = list.get(i);

            Vector3f dirToCenter = center.subtract(cornerPos).normalizeLocal();
            Vector3f camPos = cornerPos.add(dirToCenter.mult(extrusion));

            Spatial cam = cameraBase.clone();
            cam.setLocalTranslation(camPos);

            Quaternion rot = new Quaternion().lookAt(dirToCenter, Vector3f.UNIT_Y);
            rot.multLocal(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y));
            cam.setLocalRotation(rot);

            root.attachChild(cam);
            camInfos.add(new CameraData(cam, dirToCenter, r, baseY, floorH));
        }
    }


    private static Vector3f[] getVectors(Room r, float floorH, float baseY) {
        float yCam = baseY + floorH - 0.5f;

        float x1 = r.x() + WALL_T;
        float x2 = r.x() + r.w() - WALL_T;
        float z1 = r.z() + WALL_T;
        float z2 = r.z() + r.h() - WALL_T;

        return new Vector3f[]{new Vector3f(x1, yCam, z1), new Vector3f(x2, yCam, z1), new Vector3f(x2, yCam, z2), new Vector3f(x1, yCam, z2)};
    }


    public List<CameraData> getCameraData() {
        return camInfos;
    }
}
