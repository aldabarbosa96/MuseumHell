package museumhell.game.ai.cameras;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.scene.Spatial;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.utils.GeoUtil.Rect; // <-- NUEVO

import java.util.*;

import static museumhell.utils.ConstantManager.WALL_T;

public class SecurityCamera {
    private final Node root;
    private final Spatial cameraBase;
    private final float extrusion;
    private final Random rnd = new Random();
    private final List<CameraData> camInfos = new ArrayList<>();
    private final Map<Integer, List<Rect>> ceilHolesByFloor; // <-- NUEVO

    // --- constructor NUEVO (inyecta huecos por planta)
    public SecurityCamera(Node root, Spatial cameraBase, float extrusion, Map<Integer, List<Rect>> ceilHolesByFloor) {
        this.root = root;
        this.cameraBase = cameraBase;
        this.extrusion = extrusion;
        this.ceilHolesByFloor = (ceilHolesByFloor != null) ? ceilHolesByFloor : Map.of();
    }

    public record CameraData(Spatial spat, Vector3f dir, Room room, float baseY, float floorH) { }

    public void build(MuseumLayout museum) {
        float floorH = museum.floorHeight();
        for (int f = 0; f < museum.floors().size(); f++) {
            List<Room> rooms = museum.floors().get(f).rooms();
            List<Rect> holesHere = ceilHolesByFloor.getOrDefault(f, List.of()); // <-- huecos techo planta f
            for (Room r : rooms) {
                if (rnd.nextFloat() >= 0.3f) continue;
                placeInRoom(f, r, floorH, holesHere);
            }
        }
    }

    private void placeInRoom(int floorIdx, Room r, float floorH, List<Rect> floorHoles) {
        float baseY = floorIdx * floorH;
        Vector3f[] corners = getVectors(r, floorH, baseY);
        Vector3f center = r.center3f(baseY + floorH * 0.5f);

        // --- Determina lados "malos" por hueco de escalera cercano
        boolean badN = false, badS = false, badW = false, badE = false;
        final float MARGIN = WALL_T + 0.20f; // margen de seguridad hacia el interior

        for (Rect h : floorHoles) {
            float x1 = Math.max(h.x1(), r.x());
            float x2 = Math.min(h.x2(), r.x() + r.w());
            float z1 = Math.max(h.z1(), r.z());
            float z2 = Math.min(h.z2(), r.z() + r.h());
            if (x1 < x2 && z1 < z2) { // el hueco interseca esta sala
                if (z1 - r.z() <= MARGIN) badN = true;                         // toca lado norte
                if ((r.z() + r.h()) - z2 <= MARGIN) badS = true;              // toca lado sur
                if (x1 - r.x() <= MARGIN) badW = true;                         // toca lado oeste
                if ((r.x() + r.w()) - x2 <= MARGIN) badE = true;              // toca lado este
            }
        }

        // --- Construye la lista de esquinas permitidas (orden: NW, NE, SE, SW)
        List<Vector3f> allowed = new ArrayList<>(4);
        if (!(badN || badW)) allowed.add(corners[0]); // NW
        if (!(badN || badE)) allowed.add(corners[1]); // NE
        if (!(badS || badE)) allowed.add(corners[2]); // SE
        if (!(badS || badW)) allowed.add(corners[3]); // SW

        if (allowed.isEmpty()) {
            // No hay esquinas seguras en esta sala → no colocamos cámaras aquí
            return;
        }

        int camsHere = rnd.nextInt(3) + 1;
        Collections.shuffle(allowed, rnd);

        for (int i = 0; i < Math.min(camsHere, allowed.size()); i++) {
            Vector3f cornerPos = allowed.get(i);

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

        return new Vector3f[]{
                new Vector3f(x1, yCam, z1), // NW
                new Vector3f(x2, yCam, z1), // NE
                new Vector3f(x2, yCam, z2), // SE
                new Vector3f(x1, yCam, z2)  // SW
        };
    }

    public List<CameraData> getCameraData() { return camInfos; }
}
