package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.Geometry;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.bullet.control.RigidBodyControl;
import museumhell.engine.world.levelgen.Room;

public class CorridorBuilder {
    private final AssetManager assetManager;
    private final PhysicsSpace space;
    private final Node root;
    private static final float CORRIDOR_WALL_T = 0.33f * 3f;  // igual que en WB

    public CorridorBuilder(AssetManager am, Node root, PhysicsSpace space) {
        this.assetManager = am;
        this.space = space;
        this.root = root;
    }

    public void build(Room r, float y0, float h) {
        float x = r.x(), z = r.z(), w = r.w(), d = r.h();
        float cx = x + w * .5f, cz = z + d * .5f;

        // Suelo y techo — podrías reusar FloorBuilder, pero para mantenerlo simple:
        makePatch(x, z, w, d, y0 - .1f, .1f, "CorrFloor", ColorRGBA.Brown);
        makePatch(x, z, w, d, y0 + h, .1f, "CorrCeil", ColorRGBA.Blue);

        // Muros con grosor CORRIDOR_WALL_T
        buildWallSlice("CorrN", w, h, CORRIDOR_WALL_T, cx, y0 + h * .5f, z);
        buildWallSlice("CorrS", w, h, CORRIDOR_WALL_T, cx, y0 + h * .5f, z + d);
        buildWallSlice("CorrW", CORRIDOR_WALL_T, h, d, x, y0 + h * .5f, cz);
        buildWallSlice("CorrE", CORRIDOR_WALL_T, h, d, x + w, y0 + h * .5f, cz);
    }

    private void buildWallSlice(String name, float sx, float sy, float sz, float x, float y, float z) {
        Geometry g = new Geometry(name, new Box(sx * .5f, sy * .5f, sz * .5f));
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

    private void makePatch(float x, float z, float w, float d, float y, float t, String name, ColorRGBA col) {
        Geometry g = new Geometry(name, new Box(w * .5f, t * .5f, d * .5f));
        Material m = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Ambient", col.mult(0.05f + (float) Math.random() * 0.07f));
        m.setColor("Diffuse", col.mult(0.05f + (float) Math.random() * 0.07f));
        g.setMaterial(m.clone());
        g.setLocalTranslation(x + w * .5f, y, z + d * .5f);
        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
