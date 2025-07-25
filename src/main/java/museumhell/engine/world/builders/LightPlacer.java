package museumhell.engine.world.builders;

import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.Room;

import java.util.List;

public class LightPlacer {
    private final Node root;
    private SpotLight flashlight;
    private Vector3f smoothPos;
    private Vector3f smoothDir;
    private static final ColorRGBA FLASHLIGHT_COLOR = new ColorRGBA(1f, 0.95f, 0.65f, 1f).multLocal(5f);
    private static final float SPOT_RANGE = 50f;
    private static final float INNER_ANGLE = FastMath.DEG_TO_RAD * 5;
    private static final float OUTER_ANGLE = FastMath.DEG_TO_RAD * 20f;
    private static final float SMOOTH_FACTOR = 0.12f;
    private static final int SMALL_SIDE = 8;
    private static final int GRID_STEP = 12;
    private static final int MAX_LAMPS = 4;

    public LightPlacer(Node root) {
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
        /*for (Room room : rooms) {
            placeRoomLight(room, baseY, height);
        }*/
    }

    private void placeRoomLight(Room room, float baseY, float height) {
        int w = room.w();
        int d = room.h();
        float centerX = room.x() + w * 0.5f;
        float centerZ = room.z() + d * 0.5f;
        float y = baseY + height - 0.3f;
        if (w <= SMALL_SIDE && d <= SMALL_SIDE) {
            addPointLight(centerX, y, centerZ, Math.max(w, d) * 0.95f);
        } else {
            int nx = Math.max(1, Math.round(w / (float) GRID_STEP));
            int nz = Math.max(1, Math.round(d / (float) GRID_STEP));
            int lamps = Math.min(nx * nz, MAX_LAMPS);
            float stepX = w / (float) (nx + 1);
            float stepZ = d / (float) (nz + 1);
            int placed = 0;
            for (int ix = 1; ix <= nx && placed < lamps; ix++) {
                for (int iz = 1; iz <= nz && placed < lamps; iz++, placed++) {
                    float x = room.x() + ix * stepX;
                    float z = room.z() + iz * stepZ;
                    addPointLight(x, y, z, Math.max(stepX, stepZ) * 1.8f);
                }
            }
        }
    }

    private void addPointLight(float x, float y, float z, float radius) {
        PointLight pl = new PointLight();
        pl.setRadius(radius);
        pl.setColor(new ColorRGBA(1f, 0.75f, 0.45f, 1f).multLocal(0.1f));
        pl.setPosition(new Vector3f(x, y, z));
        root.addLight(pl);
    }
}
