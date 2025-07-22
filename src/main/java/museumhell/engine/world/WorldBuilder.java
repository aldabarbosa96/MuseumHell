package museumhell.engine.world;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.engine.world.levelgen.Door;
import museumhell.engine.world.levelgen.LevelLayout;
import museumhell.engine.world.levelgen.Room;

import java.util.ArrayList;
import java.util.List;

public class WorldBuilder {
    private static final float DOOR_W = 2;
    private static final float WALL_T = 0.33f;
    private static final float MARGIN = 1.0f;
    private static final int SMALL_SIDE = 8;
    private static final int GRID_STEP = 12;
    private static final int MAX_LAMPS = 4;
    private static final int MIN_OVERLAP_FOR_DOOR = (int) (DOOR_W + 2 * MARGIN);
    private List<Door> doors = new ArrayList<>();

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

            /* ---------- ILUMINACIÓN ---------- */
            int w = r.w();
            int d = r.h();
            float cx = r.x() + w * 0.5f;
            float cz = r.z() + d * 0.5f;

            if (w <= SMALL_SIDE && d <= SMALL_SIDE) {
                // Sala pequeña: 1 lámpara central (tu ajuste original)
                PointLight pl = new PointLight();
                pl.setRadius(Math.max(w, d) * 0.95f);
                pl.setColor(new ColorRGBA(1f, 0.75f, 0.45f, 1).mult(10f));
                pl.setPosition(new Vector3f(cx, height - 0.3f, cz));
                rootNode.addLight(pl);

            } else {
                // Sala mediana / grande: hasta MAX_LAMPS lámparas
                int nx = Math.max(1, Math.round(w / (float) GRID_STEP));
                int nz = Math.max(1, Math.round(d / (float) GRID_STEP));
                int lampsToPlace = Math.min(nx * nz, MAX_LAMPS);

                float stepX = w / (float) (nx + 1);
                float stepZ = d / (float) (nz + 1);

                int placed = 0;
                for (int ix = 1; ix <= nx && placed < lampsToPlace; ix++) {
                    for (int iz = 1; iz <= nz && placed < lampsToPlace; iz++) {

                        PointLight pl = new PointLight();
                        // radio algo menor para que las luces se solapen sin saturar
                        pl.setRadius(Math.max(stepX, stepZ) * 1.8f);
                        pl.setColor(new ColorRGBA(1f, 0.75f, 0.45f, 1).mult(10f));
                        pl.setPosition(new Vector3f(r.x() + ix * stepX, height - 0.3f, r.z() + iz * stepZ));
                        rootNode.addLight(pl);
                        placed++;
                    }
                }
            }

            /* ---------- SUELO Y TECHO ---------- */
            Box floorMesh = new Box(w * 0.5f, 0.1f, d * 0.5f);
            Geometry floor = makeGeometry("Floor", floorMesh, ColorRGBA.Brown);
            floor.setLocalTranslation(cx, -0.1f, cz);
            addStaticNode(floor);

            Geometry ceil = makeGeometry("Ceil", floorMesh, ColorRGBA.Blue);
            ceil.setLocalTranslation(cx, height, cz);
            addStaticNode(ceil);


            /* ---- Vecinos y muros ---- */
            boolean neighN = doorNorth(r, layout);
            boolean neighW = doorWest(r, layout);
            boolean neighS = doorSouth(r, layout);
            boolean neighE = doorEast(r, layout);

            buildWallX(r.x(), r.z(), w, height, neighN, "N");
            buildWallZ(r.x(), r.z(), d, height, neighW, "W");

            if (!neighS) buildWallX(r.x(), r.z() + d, w, height, false, "S");
            if (!neighE) buildWallZ(r.x() + w, r.z(), d, height, false, "E");
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

        float doorX = x0 + MARGIN + side + DOOR_W * .5f;
        Vector3f doorPos = new Vector3f(doorX, h * .5f, z);

        Vector3f slideX = new Vector3f(DOOR_W + 0.05f, 0, 0);

        Door door = new Door(assetManager, physicsSpace, doorPos, DOOR_W, h, WALL_T, slideX);
        rootNode.attachChild(door.getSpatial());
        doors.add(door);
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

        float doorZ = z0 + MARGIN + side + DOOR_W * .5f;
        Vector3f doorPos = new Vector3f(x, h * .5f, doorZ);

        Vector3f slideZ = new Vector3f(0, 0, DOOR_W + 0.05f);

        Door door = new Door(assetManager, physicsSpace, doorPos, WALL_T, h, DOOR_W, slideZ);
        rootNode.attachChild(door.getSpatial());
        doors.add(door);

    }

    public void addLootToRoom(Room room, int count) {
        for (int i = 0; i < count; i++) {
            float lx = room.x() + 1f + (float) Math.random() * (room.w() - 2f);
            float lz = room.z() + 1f + (float) Math.random() * (room.h() - 2f);
            Geometry loot = makeGeometry("Loot", new Box(.5f, .5f, .5f), ColorRGBA.Orange);
            loot.setLocalTranslation(lx, .5f, lz);
            addStaticNode(loot);
        }
    }

    private int overlapLen(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }

    /* ---------- puertas ---------- */
    private boolean doorNorth(Room a, LevelLayout L) {
        int ax1 = a.x(), ax2 = a.x() + a.w(), az1 = a.z();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.z() + b.h() == az1) {
                int len = overlapLen(ax1, ax2, b.x(), b.x() + b.w());
                if (len >= MIN_OVERLAP_FOR_DOOR) return true;
            }
        }
        return false;
    }

    private boolean doorSouth(Room a, LevelLayout L) {
        int ax1 = a.x(), ax2 = a.x() + a.w(), az2 = a.z() + a.h();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.z() == az2) {
                int len = overlapLen(ax1, ax2, b.x(), b.x() + b.w());
                if (len >= MIN_OVERLAP_FOR_DOOR) return true;
            }
        }
        return false;
    }

    private boolean doorWest(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax1 = a.x();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.x() + b.w() == ax1) {
                int len = overlapLen(az1, az2, b.z(), b.z() + b.h());
                if (len >= MIN_OVERLAP_FOR_DOOR) return true;
            }
        }
        return false;
    }

    private boolean doorEast(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax2 = a.x() + a.w();
        for (Room b : L.rooms()) {
            if (b == a) continue;
            if (b.x() == ax2) {
                int len = overlapLen(az1, az2, b.z(), b.z() + b.h());
                if (len >= MIN_OVERLAP_FOR_DOOR) return true;
            }
        }
        return false;
    }

    public void tryUseDoor(Vector3f playerPos) {
        final float MAX_DIST = 2.0f;
        for (Door d : doors) {
            if (d.getAccessPoint().distance(playerPos) < MAX_DIST) {
                d.toggle();
                break;
            }
        }
    }

    public Door nearestDoor(Vector3f pos, float maxDist) {
        Door best = null;
        float best2 = maxDist * maxDist;
        for (Door d : doors) {
            float dist2 = d.getAccessPoint().distanceSquared(pos);
            if (dist2 < best2) { best2 = dist2; best = d; }
        }
        return best;
    }

    private Geometry makeGeometry(String name, Mesh mesh, ColorRGBA base) {
        Geometry g = new Geometry(name, mesh);

        float f = 0.05f + (float) Math.random() * 0.07f;
        ColorRGBA dark = base.mult(f);

        Material m = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Ambient", dark);
        m.setColor("Diffuse", dark);
        m.setColor("Specular", ColorRGBA.Black);
        m.setFloat("Shininess", 1);

        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        g.setMaterial(m);
        return g;
    }


    private void addStaticNode(Geometry geo) {
        geo.addControl(new RigidBodyControl(0));
        rootNode.attachChild(geo);
        physicsSpace.add(geo.getControl(RigidBodyControl.class));
    }

    public void update(float tpf) {
        for (Door d : doors) d.update(tpf);
    }
}
