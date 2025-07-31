package museumhell.engine.world.builders;

import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.roomObjects.SecurityCamera;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static museumhell.utils.ConstantManager.*;

public class _6LightPlacer {
    private final Node root;
    private SpotLight flashlight;
    private Vector3f smoothPos;
    private Vector3f smoothDir;
    private final Map<Room, PointLight> roomBeacons = new HashMap<>();

    public _6LightPlacer(Node root) {
        this.root = root;
    }

    public void initFlashlight(Vector3f startPos, Vector3f startDir) {
        flashlight = new SpotLight();
        flashlight.setColor(FLASHLIGHT_COLOR);
        flashlight.setSpotRange(SPOT_RANGE);
        flashlight.setSpotInnerAngle(INNER_ANGLE);
        flashlight.setSpotOuterAngle(OUTER_ANGLE);
        root.addLight(flashlight);
        smoothPos = startPos.clone();
        smoothDir = startDir.clone();
        flashlight.setPosition(smoothPos);
        flashlight.setDirection(smoothDir);
    }

    public void updateFlashlight(Vector3f targetPos, Vector3f targetDir) {
        smoothPos.interpolateLocal(targetPos, SMOOTH_FACTOR);
        smoothDir.interpolateLocal(targetDir, SMOOTH_FACTOR).normalizeLocal();
        flashlight.setPosition(smoothPos);
        flashlight.setDirection(smoothDir);
    }

    public void toggleFlashlight() {
        if (flashlight != null) {
            flashlight.setEnabled(!flashlight.isEnabled());
        }
    }

    public void placeLights(List<Room> rooms, float baseY, float height) {
        Random rnd = new Random();
        for (Room room : rooms) {
            // sólo 1 de cada 5 salas
            if (rnd.nextInt(6) != 0) {
                continue;
            }
            placeRoomLight(room, baseY, height);
        }
    }

    private void placeRoomLight(Room room, float baseY, float height) {
        float ceilingY = baseY + height - 0.05f; // un poco por debajo del techo

        float x1 = room.x() + 0.75f;
        float x2 = room.x() + room.w() - 0.75f;
        float z1 = room.z() + 0.75f;
        float z2 = room.z() + room.h() - 0.75f;

        float range = 10f;
        float angle = FastMath.DEG_TO_RAD * 40f;
        ColorRGBA color = new ColorRGBA(1f, 0.85f, 0.6f, 1f).multLocal(2.5f);

        // 4 focos de techo hacia abajo
        addSpot(x1, ceilingY, z1, Vector3f.UNIT_Y.negate(), range, angle, color);
        addSpot(x2, ceilingY, z1, Vector3f.UNIT_Y.negate(), range, angle, color);
        addSpot(x1, ceilingY, z2, Vector3f.UNIT_Y.negate(), range, angle, color);
        addSpot(x2, ceilingY, z2, Vector3f.UNIT_Y.negate(), range, angle, color);
    }

    private void addSpot(float x, float y, float z, Vector3f direction, float range, float angle, ColorRGBA color) {
        SpotLight sl = new SpotLight();
        sl.setSpotRange(range);
        sl.setSpotInnerAngle(angle * 0.5f);
        sl.setSpotOuterAngle(angle);
        sl.setDirection(direction.normalize());
        sl.setPosition(new Vector3f(x, y, z));
        sl.setColor(color);
        root.addLight(sl);
    }

    public void initRoomBeacons(List<Room> rooms, float baseY, float height) {
        for (Room room : rooms) {
            Vector3f ctr = room.center3f(baseY + height * 0.5f);
            PointLight beacon = new PointLight();
            beacon.setColor(new ColorRGBA(1f, 0f, 0f, 1f).multLocal(2f));
            beacon.setRadius( Math.max(room.w(), room.h()) * 1.5f ); // cubre la sala
            beacon.setPosition(new Vector3f(ctr.x, baseY + height - 0.1f, ctr.z));
            beacon.setEnabled(false);
            root.addLight(beacon);
            roomBeacons.put(room, beacon);
        }
    }

    public void setRoomBeacon(Room room, boolean enabled) {
        PointLight beacon = roomBeacons.get(room);
        if (beacon != null) {
            beacon.setEnabled(enabled);
        }
    }

    public void placeCameraLights(List<SecurityCamera.CameraData> cams) {
        for (SecurityCamera.CameraData info : cams) {
            SpotLight sl = new SpotLight();
            sl.setSpotRange(SPOT_RANGE);
            sl.setSpotInnerAngle(INNER_ANGLE);
            sl.setSpotOuterAngle(OUTER_ANGLE);
            sl.setPosition(info.spat().getWorldTranslation());
            sl.setDirection(info.dir());
            sl.setColor(new ColorRGBA(1f, 0.85f, 0.6f, 1f).multLocal(2.5f));
            root.addLight(sl);
        }
    }
}
