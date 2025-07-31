package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import museumhell.engine.world.builders._6LightPlacer;
import museumhell.game.ai.SecurityCamera.CameraData;
import museumhell.engine.world.levelgen.Room;
import museumhell.game.player.PlayerController;

import java.util.*;

public class SecurityCamSystem extends BaseAppState {

    private final _6LightPlacer lightPlacer;
    private PhysicsSpace space;
    private final SecurityCamera camSys;
    private final PlayerController player;
    private final Node root;
    private final List<Geometry> debugLines = new ArrayList<>();
    private static final boolean DEBUG_RAYS = true;
    private final float maxDist = 20f;
    private final float halfFov = FastMath.DEG_TO_RAD * 30;

    public SecurityCamSystem(SecurityCamera camSys, PlayerController player, Node root, _6LightPlacer lightPlacer) {
        this.camSys = camSys;
        this.player = player;
        this.root = root;
        this.lightPlacer = lightPlacer;
    }

    @Override
    protected void initialize(Application app) {
        BulletAppState bullet = getStateManager().getState(BulletAppState.class);
        space = bullet.getPhysicsSpace();

    }

    @Override
    public void update(float tpf) {
        Vector3f pPos = player.getLocation();

        // 1) Inicializar todas las salas como "no detectadas"
        Map<Room, Boolean> detected = new HashMap<>();
        for (CameraData info : camSys.getCameraData()) {
            detected.put(info.room(), false);
        }

        // 2) Para cada cámara, intentar detectar al jugador
        for (CameraData info : camSys.getCameraData()) {
            Room room = info.room();
            // Si ya hay una cámara de esta sala que lo detectó, saltar
            if (detected.get(room)) {
                continue;
            }

            // 2.1) Verificar que el jugador esté en la misma sala (XZ + Y)
            float baseY = info.baseY();
            if (pPos.y < baseY || pPos.y > baseY + info.floorH()) {
                continue;
            }
            if (pPos.x < room.x() || pPos.x > room.x() + room.w() || pPos.z < room.z() || pPos.z > room.z() + room.h()) {
                continue;
            }

            // 2.2) Verificar distancia y ángulo del cono
            Vector3f camPos = info.spat().getWorldTranslation();
            Vector3f toPlayer = pPos.subtract(camPos);
            float dist = toPlayer.length();
            if (dist > maxDist) {
                continue;
            }
            if (FastMath.acos(info.dir().dot(toPlayer.normalize())) > halfFov) {
                continue;
            }

            // 2.3) Ray-cast para comprobar oclusión
            List<PhysicsRayTestResult> results = space.rayTest(camPos, pPos);
            float closestFrac = 1f;
            PhysicsCollisionObject closestObj = null;
            for (PhysicsRayTestResult r : results) {
                if (r.getHitFraction() < closestFrac) {
                    closestFrac = r.getHitFraction();
                    closestObj = r.getCollisionObject();
                }
            }
            // Si el primer objeto no es el jugador → está bloqueado
            if (closestObj != player.getCharacterControl()) {
                continue;
            }

            // 2.4) Esta cámara detecta al jugador
            detected.put(room, true);
        }

        // 3) Si estamos en modo DEBUG, limpiar y dibujar sólo los conos de las cámaras que detectaron
        if (DEBUG_RAYS) {
            debugLines.forEach(root::detachChild);
            debugLines.clear();
            for (CameraData info : camSys.getCameraData()) {
                if (detected.get(info.room())) {
                    drawDebugForCamera(info);
                }
            }
        }

        // 4) Finalmente, encender/apagar el beacon rojo de cada sala UNA VEZ
        for (var entry : detected.entrySet()) {
            lightPlacer.setRoomBeacon(entry.getKey(), entry.getValue());
        }
    }

    private void drawDebugForCamera(CameraData info) {
        ColorRGBA col = new ColorRGBA(1, 0, 0, 0.5f);
        Vector3f camPos = info.spat().getWorldTranslation();
        Vector3f dir = info.dir();

        // Rayo central
        Vector3f centerEnd = camPos.add(dir.mult(maxDist));
        Geometry gC = makeDebugLine("dbg_center", camPos, centerEnd, col);
        root.attachChild(gC);
        debugLines.add(gC);

        // Rayo borde derecho
        Quaternion rR = new Quaternion().fromAngleAxis(halfFov, Vector3f.UNIT_Y);
        Vector3f edgeR = rR.mult(dir).multLocal(maxDist);
        Geometry gR = makeDebugLine("dbg_edgeR", camPos, camPos.add(edgeR), col);
        root.attachChild(gR);
        debugLines.add(gR);

        // Rayo borde izquierdo
        Quaternion rL = new Quaternion().fromAngleAxis(-halfFov, Vector3f.UNIT_Y);
        Vector3f edgeL = rL.mult(dir).multLocal(maxDist);
        Geometry gL = makeDebugLine("dbg_edgeL", camPos, camPos.add(edgeL), col);
        root.attachChild(gL);
        debugLines.add(gL);
    }

    private Geometry makeDebugLine(String name, Vector3f from, Vector3f to, ColorRGBA color) {
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(new float[]{from.x, from.y, from.z, to.x, to.y, to.z}));
        mesh.setBuffer(VertexBuffer.Type.Index, 2, new short[]{0, 1});
        mesh.updateBound();

        Geometry geom = new Geometry(name, mesh);
        Material mat = new Material(getApplication().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setDepthTest(true);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    @Override
    protected void cleanup(Application app) {
        debugLines.forEach(root::detachChild);
        debugLines.clear();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
