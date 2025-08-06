package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.builders._6LightPlacer;
import museumhell.game.ai.SecurityCamera.CameraData;
import museumhell.engine.world.levelgen.Room;
import museumhell.game.player.PlayerController;
import museumhell.utils.media.AudioLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static museumhell.utils.ConstantManager.BLINK_INTERVAL;

public class SecurityCamSystem extends BaseAppState {
    private final _6LightPlacer lightPlacer;
    private final AudioLoader audio;
    private PhysicsSpace space;
    private final SecurityCamera camSys;
    private final PlayerController player;
    private final Node root;
    private final Map<Room, Boolean> prevDetected = new HashMap<>();
    private boolean alarmInProgress = false;
    private Room alarmRoom = null;
    private int toggleCount = 0;
    private float blinkTimer = 0f;
    private final float maxDist = 20f;
    private final float halfFov = FastMath.DEG_TO_RAD * 30;

    public SecurityCamSystem(SecurityCamera camSys, PlayerController player, Node root, _6LightPlacer lightPlacer, AudioLoader audioLoader) {
        this.camSys = camSys;
        this.player = player;
        this.root = root;
        this.lightPlacer = lightPlacer;
        this.audio = audioLoader;
    }

    @Override
    protected void initialize(Application app) {
        BulletAppState bullet = getStateManager().getState(BulletAppState.class);
        space = bullet.getPhysicsSpace();
        for (CameraData info : camSys.getCameraData()) {
            prevDetected.put(info.room(), false);
        }
    }

    @Override
    public void update(float tpf) {
        if (alarmInProgress) {
            blinkTimer += tpf;
            if (blinkTimer >= BLINK_INTERVAL) {
                blinkTimer -= BLINK_INTERVAL;
                toggleCount++;
                boolean newState = toggleCount % 2 == 1;
                lightPlacer.setRoomBeacon(alarmRoom, newState);
                if (toggleCount >= 6) {
                    alarmInProgress = false;
                    lightPlacer.setRoomBeacon(alarmRoom, false);
                }
            }
            return;
        }
        Vector3f pPos = player.getLocation();
        Map<Room, Boolean> detected = new HashMap<>();
        for (CameraData info : camSys.getCameraData()) {
            detected.put(info.room(), false);
        }
        for (CameraData info : camSys.getCameraData()) {
            Room room = info.room();
            if (detected.get(room)) {
                continue;
            }
            float baseY = info.baseY();
            if (pPos.y < baseY || pPos.y > baseY + info.floorH()) {
                continue;
            }
            if (pPos.x < room.x() || pPos.x > room.x() + room.w() || pPos.z < room.z() || pPos.z > room.z() + room.h()) {
                continue;
            }
            Vector3f camPos = info.spat().getWorldTranslation();
            Vector3f toPlayer = pPos.subtract(camPos);
            float dist = toPlayer.length();
            if (dist > maxDist) {
                continue;
            }
            if (FastMath.acos(info.dir().dot(toPlayer.normalize())) > halfFov) {
                continue;
            }
            List<PhysicsRayTestResult> results = space.rayTest(camPos, pPos);
            float closestFrac = 1f;
            PhysicsCollisionObject closestObj = null;
            for (PhysicsRayTestResult r : results) {
                if (r.getHitFraction() < closestFrac) {
                    closestFrac = r.getHitFraction();
                    closestObj = r.getCollisionObject();
                }
            }
            if (closestObj != player.getCharacterControl()) {
                continue;
            }
            detected.put(room, true);
        }
        for (Map.Entry<Room, Boolean> entry : detected.entrySet()) {
            Room room = entry.getKey();
            boolean isNow = entry.getValue();
            boolean wasBefore = prevDetected.getOrDefault(room, false);
            lightPlacer.setRoomBeacon(room, isNow);
            if (!wasBefore && isNow) {
                alarmInProgress = true;
                alarmRoom = room;
                toggleCount = 0;
                blinkTimer = 0f;
                audio.play("alarm");
            }
            prevDetected.put(room, isNow);
        }
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
