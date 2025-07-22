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
    private static final float DOOR_W = 2;
    private static final float WALL_T = 0.33f;
    private static final float MARGIN = 1.0f;

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

            /* suelo y techo */
            float cx = r.x() + r.w() * .5f;
            float cz = r.z() + r.h() * .5f;

            Box floorMesh = new Box(r.w() * .5f, 0.1f, r.h() * .5f);
            Geometry floor = makeGeometry("Floor", floorMesh, ColorRGBA.LightGray);
            floor.setLocalTranslation(cx, -0.1f, cz);
            addStaticNode(floor);

            Geometry ceil = makeGeometry("Ceil", floorMesh, ColorRGBA.Blue);
            ceil.setLocalTranslation(cx, height, cz);
            addStaticNode(ceil);

            /* detección de vecinos  */
            boolean neighN = doorNorth(r, layout);
            boolean neighW = doorWest(r, layout);
            boolean neighS = doorSouth(r, layout);
            boolean neighE = doorEast(r, layout);

            buildWallX(r.x(), r.z(), r.w(), height, neighN, "N");
            buildWallZ(r.x(), r.z(), r.h(), height, neighW, "W");

            /* Sur y Este SOLO si no hay vecino (cerrar exterior) */
            if (!neighS) buildWallX(r.x(), r.z() + r.h(), r.w(), height, false, "S");
            if (!neighE) buildWallZ(r.x() + r.w(), r.z(), r.h(), height, false, "E");
        }
    }


    private void buildWallX(float x0, float z, int width, float h, boolean hasNeighbor, String tag) {

        if (!hasNeighbor) {                    // — muro macizo exterior —
            Geometry g = makeGeometry("Wall" + tag, new Box(width * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
            g.setLocalTranslation(x0 + width * .5f, h * .5f, z);
            addStaticNode(g);
            return;
        }

        float free = width - 2 * MARGIN;
        if (free < DOOR_W) {
            hasNeighbor = false;
            buildWallX(x0, z, width, h, hasNeighbor, tag);
            return;
        }

        float side = (free - DOOR_W) * .5f;
        Geometry gL = makeGeometry("Wall" + tag + "_L", new Box((MARGIN + side) * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
        gL.setLocalTranslation(x0 + (MARGIN + side) * .5f, h * .5f, z);
        addStaticNode(gL);

        Geometry gR = makeGeometry("Wall" + tag + "_R", new Box((MARGIN + side) * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
        gR.setLocalTranslation(x0 + width - (MARGIN + side) * .5f, h * .5f, z);
        addStaticNode(gR);
    }

    /*  Pared paralela al eje Z (E-W)  */
    private void buildWallZ(float x, float z0, int depth, float h, boolean hasNeighbor, String tag) {

        if (!hasNeighbor) {                    // — muro macizo exterior —
            Geometry g = makeGeometry("Wall" + tag, new Box(WALL_T, h * .5f, depth * .5f), ColorRGBA.DarkGray);
            g.setLocalTranslation(x, h * .5f, z0 + depth * .5f);
            addStaticNode(g);
            return;
        }

        float free = depth - 2 * MARGIN;
        if (free < DOOR_W) {
            hasNeighbor = false;
            buildWallZ(x, z0, depth, h, hasNeighbor, tag);
            return;
        }

        float side = (free - DOOR_W) * .5f;
        Geometry gT = makeGeometry("Wall" + tag + "_T", new Box(WALL_T, h * .5f, (MARGIN + side) * .5f), ColorRGBA.DarkGray);
        gT.setLocalTranslation(x, h * .5f, z0 + (MARGIN + side) * .5f);
        addStaticNode(gT);

        Geometry gB = makeGeometry("Wall" + tag + "_B", new Box(WALL_T, h * .5f, (MARGIN + side) * .5f), ColorRGBA.DarkGray);
        gB.setLocalTranslation(x, h * .5f, z0 + depth - (MARGIN + side) * .5f);
        addStaticNode(gB);
    }

    public void addLootToRoom(Room room, int count) {
        for (int i = 0; i < count; i++) {
            float lx = room.x() + 1f + (float) Math.random() * (room.w() - 2f);
            float lz = room.z() + 1f + (float) Math.random() * (room.h() - 2f);
            Geometry loot = makeGeometry("Loot", new Box(.5f, .5f, .5f), ColorRGBA.Red);
            loot.setLocalTranslation(lx, .5f, lz);
            addStaticNode(loot);
        }
    }

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

    private boolean doorSouth(Room a, LevelLayout L) {
        int ax1 = a.x(), ax2 = a.x() + a.w(), az2 = a.z() + a.h();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.z() == az2 && rangesOverlap(ax1, ax2, b.x(), b.x() + b.w())) return true;
        }
        return false;
    }

    private boolean doorEast(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax2 = a.x() + a.w();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.x() == ax2 && rangesOverlap(az1, az2, b.z(), b.z() + b.h())) return true;
        }
        return false;
    }

    private boolean rangesOverlap(int a1, int a2, int b1, int b2) {
        return a1 < b2 && b1 < a2;
    }

    private Geometry makeGeometry(String name, Mesh mesh, ColorRGBA color) {
        Geometry g = new Geometry(name, mesh);
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        g.setMaterial(m);
        return g;
    }


    private void addStaticNode(Geometry geo) {
        geo.addControl(new RigidBodyControl(0));
        rootNode.attachChild(geo);
        physicsSpace.add(geo.getControl(RigidBodyControl.class));
    }
}
