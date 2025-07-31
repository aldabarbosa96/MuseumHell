package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.SpotLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import museumhell.engine.world.builders._7LightPlacer;
import museumhell.engine.world.levelgen.roomObjects.SecurityCamera;
import museumhell.engine.world.levelgen.roomObjects.SecurityCamera.CameraData;
import museumhell.engine.world.levelgen.Room;
import museumhell.game.player.PlayerController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityCamSystem extends BaseAppState {
    private final _7LightPlacer lightPlacer;
    private final SecurityCamera camSys;
    private final PlayerController player;
    private final Node root;
    private final Map<Room, SpotLight> redLights = new HashMap<>();
    private final List<Geometry> debugLines = new ArrayList<>();
    private static final boolean DEBUG_RAYS = false;

    // Parámetros del cono
    private final float maxDist = 20f;
    private final float halfFov = FastMath.DEG_TO_RAD * 30;

    public SecurityCamSystem(SecurityCamera camSys, PlayerController player, Node root, _7LightPlacer lightPlacer) {
        this.camSys = camSys;
        this.player = player;
        this.root = root;
        this.lightPlacer = lightPlacer;
    }

    @Override
    protected void initialize(Application app) {
        if (DEBUG_RAYS) createDebugLines();
    }

    @Override
    public void update(float tpf) {
        Vector3f pPos = player.getLocation().clone();
        for (CameraData info : camSys.getCameraData()) {
            Room room = info.room();
            Vector3f camPos = info.spat().getWorldTranslation();
            Vector3f toPlayer = pPos.subtract(camPos);
            float dist = toPlayer.length();
            if (dist <= maxDist && FastMath.acos(info.dir().dot(toPlayer.normalize())) <= halfFov) {
                // **ACTIVA** el beacon rojo de toda la sala
                lightPlacer.setRoomBeacon(room, true);
            } else {
                // **DESACTIVA** el beacon
                lightPlacer.setRoomBeacon(room, false);
            }
        }
    }

    private Geometry makeDebugLine(String name, Vector3f from, Vector3f to, ColorRGBA color) {
        // 1) Construye el mesh con dos vértices
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(new float[]{from.x, from.y, from.z, to.x, to.y, to.z}));
        mesh.setBuffer(VertexBuffer.Type.Index, 2, new short[]{0, 1});
        mesh.updateBound();

        // 2) Crea la geometría
        Geometry geom = new Geometry(name, mesh);
        Material mat = new Material(getApplication().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        // **Habilita el depth test** para que se ocluyan con la geometría:
        mat.getAdditionalRenderState().setDepthTest(true);
        // Opcional: si usas transparencia
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        geom.setMaterial(mat);
        // Mete las líneas en Transparent para que se dibujen en el orden adecuado
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);

        return geom;
    }

    private void createDebugLines() {
        // color verde semitransparente
        ColorRGBA col = new ColorRGBA(1, 0, 0, 0.5f);
        for (CameraData info : camSys.getCameraData()) {
            Vector3f camPos = info.spat().getWorldTranslation();
            Vector3f dir = info.dir();

            // línea central
            Vector3f centerEnd = camPos.add(dir.mult(maxDist));
            Geometry gC = makeDebugLine("center", camPos, centerEnd, col);
            root.attachChild(gC);
            debugLines.add(gC);

            // borde derecho (rota +halfFov sobre Y)
            Quaternion rR = new Quaternion().fromAngleAxis(halfFov, Vector3f.UNIT_Y);
            Vector3f edgeR = rR.mult(dir).multLocal(maxDist);
            Geometry gR = makeDebugLine("edgeR", camPos, camPos.add(edgeR), col);
            root.attachChild(gR);
            debugLines.add(gR);

            // borde izquierdo (rota −halfFov)
            Quaternion rL = new Quaternion().fromAngleAxis(-halfFov, Vector3f.UNIT_Y);
            Vector3f edgeL = rL.mult(dir).multLocal(maxDist);
            Geometry gL = makeDebugLine("edgeL", camPos, camPos.add(edgeL), col);
            root.attachChild(gL);
            debugLines.add(gL);
        }
    }


    @Override
    protected void cleanup(Application app) {
        // elimina cualquier foco restante
        redLights.values().forEach(root::removeLight);
        redLights.clear();

        debugLines.forEach(root::detachChild);
        debugLines.clear();
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
}
