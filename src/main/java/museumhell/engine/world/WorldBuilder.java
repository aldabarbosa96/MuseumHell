package museumhell.engine.world;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.engine.world.levelgen.*;
import museumhell.engine.world.levelgen.generator.ConnectionGenerator;

import java.util.*;

/**
 * Construye el contenido físico y gráfico del museo a partir de un {@link MuseumLayout}.
 */
public class WorldBuilder {
    private static final float DOOR_W = 2.5f, WALL_T = .33f, MARGIN = 1f;
    private static final float HOLE_W = DOOR_W * 2f;
    private static final int SMALL_SIDE = 8, GRID_STEP = 12, MAX_LAMPS = 4;
    private static final int MIN_OVERLAP_FOR_DOOR = (int) (DOOR_W + 2 * MARGIN);
    private static final float STAIR_WALL_GAP = 0.20f;
    private static final float STAIR_FOOT_GAP = 1.5f;
    private static final int MAX_STAIRS_PER_FLOOR = 3;
    private static final float CORRIDOR_WALL_T = WALL_T * 3f;
    private final AssetManager am;
    private final Node root;
    private final PhysicsSpace space;
    private final List<Door> doors = new ArrayList<>();
    private final Map<Integer, List<Rect>> stairVoids = new HashMap<>();

    private record Rect(float x1, float x2, float z1, float z2) {
    }

    private record StairPlacement(int floor, float x, float z) {
    }

    private final List<StairPlacement> stairs = new ArrayList<>();

    public WorldBuilder(AssetManager am, Node root, PhysicsSpace space) {
        this.am = am;
        this.root = root;
        this.space = space;
    }


    // 1) build: genera conexiones y las pasa a buildSingleFloor
    public void build(MuseumLayout museum) {
        stairVoids.clear();
        stairs.clear();
        calculateStairVoids(museum);
        float h = museum.floorHeight();

        // 1.a) genera conexiones por planta
        List<List<Connection>> floorConns = new ArrayList<>();
        long seed = System.nanoTime();
        for (LevelLayout lvl : museum.floors()) {
            floorConns.add(ConnectionGenerator.build(lvl, seed++));
        }

        // 1.b) construye cada planta con sus conexiones
        for (int i = 0; i < museum.floors().size(); i++) {
            LevelLayout lvl = museum.floors().get(i);
            List<Rect> holes = stairVoids.getOrDefault(i, List.of());
            List<Connection> conns = floorConns.get(i);
            buildSingleFloor(lvl, conns, museum.yOf(i), h, holes, i == 0);
        }

        connectFloors(museum);
    }


    public void update(float tpf) {
        doors.forEach(d -> d.update(tpf));
    }

    public void tryUseDoor(Vector3f playerPos) {
        for (Door d : doors)
            if (d.getAccessPoint().distance(playerPos) < 2f) {
                d.toggle();
                break;
            }
    }

    public Door nearestDoor(Vector3f p, float maxDist) {
        Door best = null;
        float best2 = maxDist * maxDist;
        for (Door d : doors) {
            float d2 = d.getAccessPoint().distanceSquared(p);
            if (d2 < best2) {
                best2 = d2;
                best = d;
            }
        }
        return best;
    }

    private static boolean rectsOverlap(Rect a, Rect b) {
        return a.x1 < b.x2 && a.x2 > b.x1 && a.z1 < b.z2 && a.z2 > b.z1;
    }

    private void buildSingleFloor(LevelLayout layout, List<Connection> conns, float y0, float h, List<Rect> holes, boolean skipFloorHoles) {
        // Prepara los huecos de escalera para el suelo
        List<Rect> floorHoles = skipFloorHoles ? List.of() : holes;
        // Obtiene la lista de todas las salas de esta planta
        List<Room> rooms = layout.rooms();

        for (Room r : rooms) {
            if (isCorridor(r)) {
                buildThickCorridor(r, y0, h);
                continue;
            }
            int w = r.w(), d = r.h();
            float cx = r.x() + w * 0.5f;
            float cz = r.z() + d * 0.5f;

            /* --------- Luces --------- */
            if (w <= SMALL_SIDE && d <= SMALL_SIDE) {
                pointLight(cx, y0 + h - 0.3f, cz, Math.max(w, d) * .95f);
            } else {
                int nx = Math.max(1, Math.round(w / (float) GRID_STEP));
                int nz = Math.max(1, Math.round(d / (float) GRID_STEP));
                int lamps = Math.min(nx * nz, MAX_LAMPS);
                float sx = w / (float) (nx + 1), sz = d / (float) (nz + 1);
                int placed = 0;
                for (int ix = 1; ix <= nx && placed < lamps; ix++) {
                    for (int iz = 1; iz <= nz && placed < lamps; iz++, placed++) {
                        pointLight(r.x() + ix * sx, y0 + h - 0.3f, r.z() + iz * sz, Math.max(sx, sz) * 1.8f);
                    }
                }
            }

            /* --------- Suelo y techo --------- */
            addFloorPatches(r.x(), r.z(), w, d, floorHoles, y0 - 0.1f, 0.1f, "Floor", ColorRGBA.Brown);
            addFloorPatches(r.x(), r.z(), w, d, holes, y0 + h, 0.1f, "Ceil", ColorRGBA.Blue);

            /* --------- Muros y conexiones --------- */
            handleWall(r, Direction.NORTH, conns, rooms, y0, h);
            handleWall(r, Direction.WEST, conns, rooms, y0, h);
            if (!hasNeighbor(r, rooms, Direction.SOUTH)) {
                handleWall(r, Direction.SOUTH, conns, rooms, y0, h);
            }
            if (!hasNeighbor(r, rooms, Direction.EAST)) {
                handleWall(r, Direction.EAST, conns, rooms, y0, h);
            }
        }
    }


    private void handleWall(Room r, Direction dir, List<Connection> conns, List<Room> rooms, float y0, float h) {
        Connection c = findConnection(conns, r, dir);
        if (c == null) {
            buildSolidWall(r, dir, y0, h);
        } else {
            switch (c.type()) {
                case DOOR:
                    buildWallWithDoor(r, dir, y0, h, rooms);
                    break;
                case OPENING:
                    float thickness = isCorridor(r) ? CORRIDOR_WALL_T : WALL_T;
                    buildOpeningWall(r, dir, y0, h, rooms, HOLE_W, thickness);
                    break;
            }
        }
    }


    private float[] getOverlapRange(Room r, List<Room> rooms, Direction dir) {
        int a1, a2, b1, b2;
        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            // solapamiento en X
            a1 = r.x();
            a2 = r.x() + r.w();
            int zEdge = dir == Direction.NORTH ? r.z() : r.z() + r.h();
            for (Room o : rooms) {
                if ((dir == Direction.NORTH && o.z() + o.h() == zEdge) || (dir == Direction.SOUTH && o.z() == zEdge)) {
                    b1 = o.x();
                    b2 = o.x() + o.w();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= MIN_OVERLAP_FOR_DOOR) {
                        float start = Math.max(a1, b1);
                        float end = Math.min(a2, b2);
                        return new float[]{start, end};
                    }
                }
            }
        } else {
            // solapamiento en Z
            a1 = r.z();
            a2 = r.z() + r.h();
            int xEdge = dir == Direction.WEST ? r.x() : r.x() + r.w();
            for (Room o : rooms) {
                if ((dir == Direction.WEST && o.x() + o.w() == xEdge) || (dir == Direction.EAST && o.x() == xEdge)) {
                    b1 = o.z();
                    b2 = o.z() + o.h();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= MIN_OVERLAP_FOR_DOOR) {
                        float start = Math.max(a1, b1);
                        float end = Math.min(a2, b2);
                        return new float[]{start, end};
                    }
                }
            }
        }
        throw new IllegalStateException("No hay vecino válido para dir=" + dir + " en sala " + r);
    }


    private boolean hasNeighbor(Room a, List<Room> rooms, Direction dir) {
        for (Room b : rooms) {
            switch (dir) {
                case NORTH -> {
                    if (b.z() + b.h() == a.z() && overlap(a.x(), a.x() + a.w(), b.x(), b.x() + b.w()) >= MIN_OVERLAP_FOR_DOOR)
                        return true;
                }
                case SOUTH -> {
                    if (b.z() == a.z() + a.h() && overlap(a.x(), a.x() + a.w(), b.x(), b.x() + b.w()) >= MIN_OVERLAP_FOR_DOOR)
                        return true;
                }
                case WEST -> {
                    if (b.x() + b.w() == a.x() && overlap(a.z(), a.z() + a.h(), b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR)
                        return true;
                }
                case EAST -> {
                    if (b.x() == a.x() + a.w() && overlap(a.z(), a.z() + a.h(), b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR)
                        return true;
                }
            }
        }
        return false;
    }

    private Direction opposite(Direction d) {
        return switch (d) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }

    private Connection findConnection(List<Connection> conns, Room room, Direction dir) {
        for (Connection c : conns) {
            // si room es el origen y la dir coincide…
            if (c.a() == room && c.dir() == dir) {
                return c;
            }
            // o si room es el destino y la dir opuesta coincide
            if (c.b() == room && opposite(c.dir()) == dir) {
                return c;
            }
        }
        return null;
    }


    private void buildSolidWall(Room r, Direction dir, float y0, float h) {
        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            // muro horizontal
            float z = (dir == Direction.NORTH ? r.z() : r.z() + r.h());
            Geometry g = makeGeometry("Wall" + dir, new Box(r.w() * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
            g.setLocalTranslation(r.x() + r.w() * .5f, y0 + h * .5f, z);
            addStatic(g);
        } else {
            // muro vertical
            float x = (dir == Direction.WEST ? r.x() : r.x() + r.w());
            Geometry g = makeGeometry("Wall" + dir, new Box(WALL_T, h * .5f, r.h() * .5f), ColorRGBA.DarkGray);
            g.setLocalTranslation(x, y0 + h * .5f, r.z() + r.h() * .5f);
            addStatic(g);
        }
    }

    private void buildOpeningWall(Room r, Direction dir, float y0, float h, List<Room> rooms, float holeWidth, float wallThickness) {
        float[] ov = getOverlapRange(r, rooms, dir);
        float center = (ov[0] + ov[1]) * .5f;
        float halfHole = holeWidth * .5f;

        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            float z = dir == Direction.NORTH ? r.z() : r.z() + r.h();
            float leftW = center - halfHole - r.x();
            float rightW = (r.x() + r.w()) - (center + halfHole);
            if (leftW > 0) addWallSlice(r.x() + leftW * 0.5f, y0 + h * 0.5f, z, leftW * 0.5f, h * 0.5f, wallThickness);
            if (rightW > 0)
                addWallSlice(r.x() + r.w() - rightW * 0.5f, y0 + h * 0.5f, z, rightW * 0.5f, h * 0.5f, wallThickness);
        } else {
            float x = dir == Direction.WEST ? r.x() : r.x() + r.w();
            float backD = center - halfHole - r.z();
            float frontD = (r.z() + r.h()) - (center + halfHole);
            if (backD > 0) addWallSlice(x, y0 + h * 0.5f, r.z() + backD * 0.5f, wallThickness, h * 0.5f, backD * 0.5f);
            if (frontD > 0)
                addWallSlice(x, y0 + h * 0.5f, r.z() + r.h() - frontD * 0.5f, wallThickness, h * 0.5f, frontD * 0.5f);
        }
    }

    private void addWallSlice(float x, float y, float z, float sx, float sy, float sz) {
        Geometry g = makeGeometry("WallSlice", new Box(sx, sy, sz), ColorRGBA.Gray);
        g.setLocalTranslation(x, y, z);
        addStatic(g);
    }


    private void buildWallWithDoor(Room r, Direction dir, float y0, float h, List<Room> rooms) {
        // 1) hacer hueco justo del ancho de la puerta
        buildOpeningWall(r, dir, y0, h, rooms, DOOR_W, WALL_T);

        // 2) calcular centro
        float[] ov = getOverlapRange(r, rooms, dir);
        float holeCenter = (ov[0] + ov[1]) * .5f;

        // 3) decidir posición y offset
        Vector3f center, offset;
        if (dir == Direction.NORTH) {
            center = new Vector3f(holeCenter, y0 + h * .5f, r.z());
            offset = new Vector3f(DOOR_W + .05f, 0, 0);
        } else if (dir == Direction.SOUTH) {
            center = new Vector3f(holeCenter, y0 + h * .5f, r.z() + r.h());
            offset = new Vector3f(DOOR_W + .05f, 0, 0);
        } else if (dir == Direction.WEST) {
            center = new Vector3f(r.x(), y0 + h * .5f, holeCenter);
            offset = new Vector3f(0, 0, DOOR_W + .05f);
        } else { // EAST
            center = new Vector3f(r.x() + r.w(), y0 + h * .5f, holeCenter);
            offset = new Vector3f(0, 0, DOOR_W + .05f);
        }

        // 4) crear la puerta con las dimensiones correctas
        float w = (dir == Direction.NORTH || dir == Direction.SOUTH) ? DOOR_W : WALL_T;
        float t = (dir == Direction.NORTH || dir == Direction.SOUTH) ? WALL_T : DOOR_W;
        Door d = new Door(am, space, center, w, h, t, offset);
        root.attachChild(d.getSpatial());
        doors.add(d);
    }

    private void buildThickCorridor(Room r, float y0, float h) {
        float x = r.x(), z = r.z(), w = r.w(), d = r.h();
        float cx = x + w * 0.5f, cz = z + d * 0.5f;

        // suelo + techo (igual que siempre)
        makePatch(x, z, w, d, y0 - .1f, .1f, "CorrFloor", ColorRGBA.Brown);
        makePatch(x, z, w, d, y0 + h, .1f, "CorrCeil", ColorRGBA.Blue);

        // muros N/S con grosor CORRIDOR_WALL_T
        Geometry wallN = makeGeometry("CorrN", new Box(w * 0.5f, h * 0.5f, CORRIDOR_WALL_T), ColorRGBA.Gray);
        wallN.setLocalTranslation(cx, y0 + h * 0.5f, z);
        addStatic(wallN);

        Geometry wallS = makeGeometry("CorrS", new Box(w * 0.5f, h * 0.5f, CORRIDOR_WALL_T), ColorRGBA.Gray);
        wallS.setLocalTranslation(cx, y0 + h * 0.5f, z + d);
        addStatic(wallS);

        // muros W/E con grosor CORRIDOR_WALL_T
        Geometry wallW = makeGeometry("CorrW", new Box(CORRIDOR_WALL_T, h * 0.5f, d * 0.5f), ColorRGBA.Gray);
        wallW.setLocalTranslation(x, y0 + h * 0.5f, cz);
        addStatic(wallW);

        Geometry wallE = makeGeometry("CorrE", new Box(CORRIDOR_WALL_T, h * 0.5f, d * 0.5f), ColorRGBA.Gray);
        wallE.setLocalTranslation(x + w, y0 + h * 0.5f, cz);
        addStatic(wallE);
    }


    private void addFloorPatches(float rx, float rz, float rw, float rd, List<Rect> holes, float y, float t, String tag, ColorRGBA col) {

        /* Busca si la sala se solapa con algún hueco */
        for (Rect v : holes) {
            float hx1 = Math.max(v.x1, rx);
            float hx2 = Math.min(v.x2, rx + rw);
            float hz1 = Math.max(v.z1, rz);
            float hz2 = Math.min(v.z2, rz + rd);

            if (hx1 < hx2 && hz1 < hz2) {      // hay solapamiento → 4 parches máx.

                // 1) izquierda
                if (hx1 > rx) makePatch(rx, rz, hx1 - rx, rd, y, t, tag + "_L", col);

                // 2) derecha
                if (hx2 < rx + rw) makePatch(hx2, rz, rx + rw - hx2, rd, y, t, tag + "_R", col);

                // 3) delante
                if (hz1 > rz) makePatch(hx1, rz, hx2 - hx1, hz1 - rz, y, t, tag + "_F", col);

                // 4) detrás
                if (hz2 < rz + rd) makePatch(hx1, hz2, hx2 - hx1, rz + rd - hz2, y, t, tag + "_B", col);

                return; // solo un hueco por sala en esta implementación
            }
        }

        /* Sin hueco → losa completa */
        makePatch(rx, rz, rw, rd, y, t, tag, col);
    }

    private void makePatch(float x, float z, float w, float d, float y, float t, String name, ColorRGBA col) {
        Geometry g = makeGeometry(name, new Box(w * .5f, t * .5f, d * .5f), col);
        g.setLocalTranslation(x + w * .5f, y, z + d * .5f);
        addStatic(g);
    }

    private void calculateStairVoids(MuseumLayout museum) {
        if (museum.floors().size() < 2) return;

        Random rnd = new Random(museum.floors().size() * 73L);
        int steps = (int) Math.ceil(museum.floorHeight() / Stairs.STEP_H);
        float runDepth = steps * Stairs.STEP_DEPTH;
        float hxPad = Stairs.WIDTH * .5f + 0.05f;

        for (int floor = 0; floor < museum.floors().size() - 1; floor++) {
            LevelLayout A = museum.floors().get(floor);
            LevelLayout B = museum.floors().get(floor + 1);

            Map<Room, Boolean> usedRoom = new HashMap<>();
            int placed = 0;

            List<Room> roomsA = new ArrayList<>(A.rooms());
            Collections.shuffle(roomsA, rnd);

            for (Room ra : roomsA) {
                if (placed >= MAX_STAIRS_PER_FLOOR) break;
                if (usedRoom.getOrDefault(ra, false)) continue;

                List<Room> roomsB = new ArrayList<>(B.rooms());
                Collections.shuffle(roomsB, rnd);

                for (Room rb : roomsB) {
                    int ix1 = Math.max(ra.x(), rb.x());
                    int ix2 = Math.min(ra.x() + ra.w(), rb.x() + rb.w());
                    int iz1 = Math.max(ra.z(), rb.z());
                    int iz2 = Math.min(ra.z() + ra.h(), rb.z() + rb.h());
                    if (ix2 - ix1 < Stairs.WIDTH + STAIR_WALL_GAP * 2) continue;
                    if (iz2 - iz1 < runDepth + STAIR_FOOT_GAP * 2) continue;

                    boolean left = rnd.nextBoolean();
                    if (left && doorWest(ra, A)) continue;
                    if (!left && doorEast(ra, A)) continue;

                    float sx = left ? ix1 + STAIR_WALL_GAP + Stairs.WIDTH * .5f : ix2 - STAIR_WALL_GAP - Stairs.WIDTH * .5f;
                    float zMin = iz1 + Stairs.STEP_DEPTH * .5f + STAIR_FOOT_GAP;
                    float zMax = iz2 - runDepth + Stairs.STEP_DEPTH * .5f - STAIR_FOOT_GAP;
                    float sz = (zMax > zMin) ? FastMath.interpolateLinear(rnd.nextFloat(), zMin, zMax) : zMin;

                    Rect hole = new Rect(sx - hxPad, sx + hxPad, sz - Stairs.STEP_DEPTH * .5f, sz + runDepth - Stairs.STEP_DEPTH * .5f);

                    boolean conflict = false;
                    for (Rect ex : stairVoids.getOrDefault(floor, List.of())) {
                        if (rectsOverlap(hole, ex)) {
                            conflict = true;
                            break;
                        }
                    }
                    if (conflict) continue;

                    stairVoids.computeIfAbsent(floor, k -> new ArrayList<>()).add(hole);
                    stairVoids.computeIfAbsent(floor + 1, k -> new ArrayList<>()).add(hole);
                    stairs.add(new StairPlacement(floor, sx, sz));

                    usedRoom.put(ra, true);
                    placed++;
                    break;
                }
            }
        }
    }


    private void connectFloors(MuseumLayout museum) {
        for (StairPlacement sp : stairs) {
            float y0 = museum.yOf(sp.floor);
            Stairs.add(root, space, am, new Vector3f(sp.x, y0, sp.z), museum.floorHeight());
        }
    }


    private int overlap(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }

    private boolean doorWest(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax = a.x();
        for (Room b : L.rooms())
            if (b != a && b.x() + b.w() == ax && overlap(az1, az2, b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR)
                return true;
        return false;
    }

    private boolean doorEast(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax = a.x() + a.w();
        for (Room b : L.rooms())
            if (b != a && b.x() == ax && overlap(az1, az2, b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR) return true;
        return false;
    }

    private void pointLight(float x, float y, float z, float radius) {
        PointLight pl = new PointLight();
        pl.setRadius(radius);
        pl.setColor(new ColorRGBA(1f, .75f, .45f, 1f).mult(10f));
        pl.setPosition(new Vector3f(x, y, z));
        root.addLight(pl);
    }

    private Geometry makeGeometry(String name, Mesh mesh, ColorRGBA base) {
        Geometry g = new Geometry(name, mesh);
        ColorRGBA dark = base.mult(.05f + (float) Math.random() * .07f);
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Ambient", dark);
        m.setColor("Diffuse", dark);
        m.setColor("Specular", ColorRGBA.Black);
        m.setFloat("Shininess", 1);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        g.setMaterial(m);
        return g;
    }

    private void addStatic(Geometry g) {
        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }

    private boolean isCorridor(Room r) {
        return Float.compare(r.w(), HOLE_W) == 0 || Float.compare(r.h(), HOLE_W) == 0;
    }
}
