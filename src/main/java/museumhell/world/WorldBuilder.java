package museumhell.world;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.world.levelgen.LevelLayout;
import museumhell.world.levelgen.Room;

public class WorldBuilder {

    private static final float DOOR_W = 1.4f;
    private static final float WALL_T = 0.1f;
    private static final int MAX_GAP = 4;

    private final AssetManager assetManager;
    private final Node rootNode;
    private final PhysicsSpace physicsSpace;

    public WorldBuilder(AssetManager am, Node rootNode, PhysicsSpace ps) {
        this.assetManager = am;
        this.rootNode = rootNode;
        this.physicsSpace = ps;
    }

    public void build(LevelLayout layout, float height) {

        for (Room r : layout.rooms()) {

            /* suelo */
            float cx = r.x() + r.w() * .5f;
            float cz = r.z() + r.h() * .5f;

            Box floorMesh = new Box(r.w() * .5f, 0.1f, r.h() * .5f);
            Geometry floor = makeGeom("Floor", floorMesh, ColorRGBA.LightGray);
            floor.setLocalTranslation(cx, -0.1f, cz);
            addStatic(floor);

            /* techo */
            Geometry ceil = makeGeom("Ceil", floorMesh, ColorRGBA.Blue);
            ceil.setLocalTranslation(cx, height, cz);
            addStatic(ceil);

            /* vecinos */
            boolean doorN = doorNorth(r, layout);
            boolean doorW = doorWest(r, layout);

            /* paredes */
            buildWallX(r.x(), r.z(), r.w(), height, doorN, "N");
            buildWallZ(r.x(), r.z(), r.h(), height, doorW, "W");
        }
    }

    private void buildWallX(float x0, float z, int width, float h, boolean door, String tag) {

        if (!door) {
            Geometry g = makeGeom("Wall" + tag, new Box(width * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
            g.setLocalTranslation(x0 + width * .5f, h * .5f, z);
            addStatic(g);
            return;
        }

        float side = (width - DOOR_W) * .5f;
        if (side <= 0) return;

        Geometry gL = makeGeom("Wall" + tag + "_L", new Box(side * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
        gL.setLocalTranslation(x0 + side * .5f, h * .5f, z);
        addStatic(gL);

        Geometry gR = makeGeom("Wall" + tag + "_R", new Box(side * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
        gR.setLocalTranslation(x0 + width - side * .5f, h * .5f, z);
        addStatic(gR);
    }

    private void buildWallZ(float x, float z0, int depth, float h, boolean door, String tag) {

        if (!door) {
            Geometry g = makeGeom("Wall" + tag, new Box(WALL_T, h * .5f, depth * .5f), ColorRGBA.DarkGray);
            g.setLocalTranslation(x, h * .5f, z0 + depth * .5f);
            addStatic(g);
            return;
        }

        float side = (depth - DOOR_W) * .5f;
        if (side <= 0) return;

        Geometry gT = makeGeom("Wall" + tag + "_T", new Box(WALL_T, h * .5f, side * .5f), ColorRGBA.DarkGray);
        gT.setLocalTranslation(x, h * .5f, z0 + side * .5f);
        addStatic(gT);

        Geometry gB = makeGeom("Wall" + tag + "_B", new Box(WALL_T, h * .5f, side * .5f), ColorRGBA.DarkGray);
        gB.setLocalTranslation(x, h * .5f, z0 + depth - side * .5f);
        addStatic(gB);
    }

    public void addLootToRoom(Room room, int count) {
        for (int i = 0; i < count; i++) {
            float lx = room.x() + 1f + (float) Math.random() * (room.w() - 2f);
            float lz = room.z() + 1f + (float) Math.random() * (room.h() - 2f);
            Geometry loot = makeGeom("Loot", new Box(.5f, .5f, .5f), ColorRGBA.Red);
            loot.setLocalTranslation(lx, .5f, lz);
            addStatic(loot);
        }
    }

    /* ---------------- puertas reciprocas ---------------------------------- */
    private boolean doorNorth(Room a, LevelLayout L) {
        int ax1 = a.x(), ax2 = a.x() + a.w(), az1 = a.z();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.z() + b.h() == az1 && rangesOverlap(ax1, ax2, b.x(), b.x() + b.w())) return true;
        }
        return false;
    }

    private boolean doorWest(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax1 = a.x();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.x() + b.w() == ax1 && rangesOverlap(az1, az2, b.z(), b.z() + b.h())) return true;
        }
        return false;
    }


    private boolean rangesOverlap(int a1, int a2, int b1, int b2) {
        return a1 < b2 && b1 < a2;
    }

    private Geometry makeGeom(String name, Mesh mesh, ColorRGBA color) {
        Geometry g = new Geometry(name, mesh);
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        g.setMaterial(m);
        return g;
    }


    private void addStatic(Geometry geo) {
        geo.addControl(new RigidBodyControl(0));
        rootNode.attachChild(geo);
        physicsSpace.add(geo.getControl(RigidBodyControl.class));
    }
}
