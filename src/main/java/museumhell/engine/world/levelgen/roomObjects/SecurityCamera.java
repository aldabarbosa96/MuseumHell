package museumhell.engine.world.levelgen.roomObjects;

import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.scene.Spatial;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;

import java.util.*;

import static museumhell.utils.ConstantManager.WALL_T;

public class SecurityCamera {
    private static final float THIN_T = 0.33f;
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

    public record CameraData(Spatial spat, Vector3f dir, Room room, float baseY, float floorH) {}

    public void build(MuseumLayout museum) {
        float floorH = museum.floorHeight();
        for (int f = 0; f < museum.floors().size(); f++) {
            List<Room> rooms = museum.floors().get(f).rooms();
            for (Room r : rooms) {
                if (rnd.nextBoolean()) continue;
                placeInRoom(f, r, floorH, rooms);
            }
        }
    }

    private void placeInRoom(int floorIdx, Room r, float floorH, List<Room> sameFloorRooms) {
        float baseY = floorIdx * floorH;
        Vector3f[] corners = getVectors(r, floorH, baseY, sameFloorRooms);
        Vector3f center = r.center3f(baseY + floorH * 0.5f);
        int camsHere = rnd.nextInt(4) + 1;
        List<Vector3f> list = Arrays.asList(corners);
        Collections.shuffle(list, rnd);
        for (int i = 0; i < camsHere; i++) {
            Vector3f pos0 = list.get(i);
            Vector3f dirToCenter = pos0.subtract(center).normalizeLocal();
            Vector3f normalOut = dirToCenter.negate();
            float extra = (dirToCenter.x > 0 && dirToCenter.z > 0) ? 0.1f : 0f;
            Vector3f posExtruded = pos0.add(normalOut.mult(extrusion + extra));
            Spatial cam = cameraBase.clone();
            cam.setLocalTranslation(posExtruded);
            cam.setLocalRotation(new Quaternion().lookAt(dirToCenter, Vector3f.UNIT_Y));
            root.attachChild(cam);
            camInfos.add(new CameraData(cam, dirToCenter.negate(), r, baseY, floorH));
        }
    }

    private static Vector3f[] getVectors(Room r, float floorH, float baseY, List<Room> rooms) {
        float yCam = baseY + floorH - 0.455f;
        float tW = hasNeighbor(r, rooms, 'W') ? WALL_T : THIN_T;
        float tE = hasNeighbor(r, rooms, 'E') ? WALL_T : THIN_T;
        float tN = hasNeighbor(r, rooms, 'N') ? WALL_T : THIN_T;
        float tS = hasNeighbor(r, rooms, 'S') ? WALL_T : THIN_T;
        float x1 = r.x() + tW * 0.5f;
        float x2 = r.x() + r.w() - tE * 0.5f;
        float z1 = r.z() + tN * 0.5f;
        float z2 = r.z() + r.h() - tS * 0.5f;
        return new Vector3f[]{
                new Vector3f(x1, yCam, z1),
                new Vector3f(x2, yCam, z1),
                new Vector3f(x2, yCam, z2),
                new Vector3f(x1, yCam, z2)
        };
    }

    private static boolean hasNeighbor(Room r, List<Room> rooms, char side) {
        for (Room o : rooms) {
            switch (side) {
                case 'N' -> {
                    if (Math.abs(o.z() + o.h() - r.z()) < 0.01f && overlap1D(r.x(), r.x() + r.w(), o.x(), o.x() + o.w()))
                        return true;
                }
                case 'S' -> {
                    if (Math.abs(o.z() - (r.z() + r.h())) < 0.01f && overlap1D(r.x(), r.x() + r.w(), o.x(), o.x() + o.w()))
                        return true;
                }
                case 'W' -> {
                    if (Math.abs(o.x() + o.w() - r.x()) < 0.01f && overlap1D(r.z(), r.z() + r.h(), o.z(), o.z() + o.h()))
                        return true;
                }
                case 'E' -> {
                    if (Math.abs(o.x() - (r.x() + r.w())) < 0.01f && overlap1D(r.z(), r.z() + r.h(), o.z(), o.z() + o.h()))
                        return true;
                }
            }
        }
        return false;
    }

    private static boolean overlap1D(float a1, float a2, float b1, float b2) {
        return Math.min(a2, b2) - Math.max(a1, b1) > 0.01f;
    }

    public List<CameraData> getCameraData() {
        return camInfos;
    }
}
