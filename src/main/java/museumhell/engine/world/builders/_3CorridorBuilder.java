package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.engine.world.levelgen.Room;

import java.util.List;

import static museumhell.utils.ConstantManager.*;

public class _3CorridorBuilder {
    private final _1FloorBuilder floor;
    private final _6CeilBuilder0 ceil;
    private final AssetManager assetManager;
    private final PhysicsSpace space;
    private final Node root;

    public _3CorridorBuilder(AssetManager am, Node root, PhysicsSpace space, _1FloorBuilder floor, _6CeilBuilder0 ceil) {
        this.assetManager = am;
        this.space = space;
        this.root = root;
        this.floor = floor;
        this.ceil = ceil;
    }

    public void build(Room r, float y0, float h) {
        int ix = r.x(), iz = r.z(), iw = r.w(), id = r.h();

        float floorCenterY = y0 - FLOOR_T * 0.5f;
        floor.buildPatches(ix, iz, iw, id, List.of(), floorCenterY, FLOOR_T);

        float ceilCenterY = y0 + h - CEIL_T * 1.5f;
        ceil.buildPatches(ix, iz, iw, id, List.of(), ceilCenterY, CEIL_T);

        float cx = ix + iw * 0.5f;
        float cz = iz + id * 0.5f;
        buildWallSlice("CorrN", iw, h, WALL_T, cx, y0 + h * 0.5f, iz);
        buildWallSlice("CorrS", iw, h, WALL_T, cx, y0 + h * 0.5f, iz + id);
        buildWallSlice("CorrW", WALL_T, h, id, ix, y0 + h * 0.5f, cz);
        buildWallSlice("CorrE", WALL_T, h, id, ix + iw, y0 + h * 0.5f, cz);
    }

    private void buildWallSlice(String name, float sx, float sy, float sz, float x, float y, float z) {
        Geometry g = new Geometry(name, new Box(sx * 0.5f, sy * 0.5f, sz * 0.5f));
        g.setLocalTranslation(x, y, z);

        Material m = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Ambient", ColorRGBA.Gray.mult(0.1f));
        m.setColor("Diffuse", ColorRGBA.Gray.mult(0.1f));
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        g.setMaterial(m);
        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
