package museumhell.engine.world.world;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.builders.*;
import museumhell.engine.world.levelgen.*;
import museumhell.engine.world.levelgen.generator.ConnectionGenerator;
import museumhell.utils.AssetLoader;
import museumhell.utils.GeoUtil.*;

import java.util.*;

import static museumhell.utils.ConstantManager.*;
import static museumhell.utils.GeoUtil.opposite;
import static museumhell.utils.GeoUtil.overlap;

public class WorldBuilder {
    private final _6LightPlacer a7LightPlacer;
    private final _1FloorBuilder a1FloorBuilder;
    private final _5CeilBuilder0 a6CeilBuilder;
    private final _2WallBuilder a2WallBuilder;
    private final _3DoorBuilder a4DoorBuilder;
    private final _4StairBuilder a5StairBuilder;
    private final List<Door> doors = new ArrayList<>();
    private boolean doorOpen = false;

    public WorldBuilder(AssetManager am, Node root, PhysicsSpace space, AssetLoader assetLoader) {
        this.a7LightPlacer = new _6LightPlacer(root);
        this.a1FloorBuilder = new _1FloorBuilder(root, space, am, assetLoader);
        this.a6CeilBuilder = new _5CeilBuilder0(root, space, am);
        this.a2WallBuilder = new _2WallBuilder(am, root, space, assetLoader);
        this.a4DoorBuilder = new _3DoorBuilder(am, space, root, doors, a2WallBuilder);
        this.a5StairBuilder = new _4StairBuilder(am, space, root);
    }

    // 1) build: genera conexiones y las pasa a buildSingleFloor
    public void build(MuseumLayout museum) {
        float h = museum.floorHeight();

        /* ---------- 1) conexiones por planta ---------- */
        List<List<Connection>> floorConns = new ArrayList<>();
        long seed = System.nanoTime();
        for (LevelLayout lvl : museum.floors()) {
            floorConns.add(ConnectionGenerator.build(lvl, seed++));
        }

        /* ---------- 2) planificación de escaleras ---------- */
        _4StairBuilder.Plan plan = a5StairBuilder.plan(museum);

        /* ---------- 2.1) huecos‑base de escalera por planta ---------- */
        Map<Integer, List<Rect>> baseHoles = new HashMap<>();
        for (var sp : plan.placements) {
            Rect hole = computeHoleFromPlacement(sp, museum.floorHeight());
            baseHoles.computeIfAbsent(sp.floor(), k -> new ArrayList<>()).add(hole);
        }

        /* ---------- 3) construir cada planta ---------- */
        for (int i = 0; i < museum.floors().size(); i++) {
            LevelLayout lvl = museum.floors().get(i);
            List<Connection> cns = floorConns.get(i);

            List<Rect> ceilHoles = plan.holes.getOrDefault(i, List.of());

            /*– huecos que SÍ perforan el suelo (llegada escalera) */
            List<Rect> floorHoles = new ArrayList<>(ceilHoles);
            floorHoles.removeAll(baseHoles.getOrDefault(i, List.of()));

            /* planta0: nunca perforamos el suelo */
            if (i == 0) floorHoles = List.of();

            buildSingleFloor(lvl, cns, museum.yOf(i), h, ceilHoles, floorHoles);
        }

        /* ---------- 4) colocar las escaleras ---------- */
        a5StairBuilder.place(plan, museum);
    }

    private Rect computeHoleFromPlacement(_4StairBuilder.StairPlacement sp, float floorH) {

        int steps = (int) Math.ceil(floorH / STEP_H);
        float runD = steps * STEP_DEPTH;
        float hxPad = STAIR_WIDTH * 0.5f;
        float pad = 0.05f;

        Enum<?> orient = (Enum<?>) sp.orientation();
        boolean eastWest = orient.name().equals("EW");

        if (eastWest) {
            return new Rect(sp.x() - hxPad, sp.x() + hxPad, sp.z() - STEP_DEPTH * 0.5f - pad, sp.z() + runD + pad);
        } else {
            return new Rect(sp.x() - STEP_DEPTH * 0.5f - pad, sp.x() + runD + pad, sp.z() - hxPad, sp.z() + hxPad);
        }
    }


    public void update(float tpf) {
        doors.forEach(d -> d.update(tpf));
    }

    public void tryUseDoor(Vector3f playerPos) {
        for (Door d : doors)
            if (d.getAccessPoint().distance(playerPos) < 3.5f) {
                d.toggle();
                doorOpen = true;
                return;
            }
        doorOpen = false;
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

    private void buildSingleFloor(LevelLayout layout, List<Connection> conns, float y0, float h, List<Rect> ceilHoles, List<Rect> floorHoles) {

        List<Room> rooms = layout.rooms();

        /* 1) iluminación */
        a7LightPlacer.placeLights(rooms, y0, h);

        /* 2) suelo y techo */
        for (Room r : rooms) {
            float floorCenterY = y0 - FLOOR_T * 0.5f;
            a1FloorBuilder.buildPatches(r.x(), r.z(), r.w(), r.h(), floorHoles, floorCenterY, FLOOR_T);

            float ceilCenterY = y0 + h - CEIL_T * 1.5f;
            a6CeilBuilder.buildPatches(r.x(), r.z(), r.w(), r.h(), ceilHoles, ceilCenterY, CEIL_T);

            /* 3) muros y aberturas */
            for (Direction dir : List.of(Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST)) {
                if ((dir == Direction.SOUTH || dir == Direction.EAST) && hasNeighbor(r, rooms, dir)) {
                    continue;
                }

                Connection c = findConnection(conns, r, dir);
                if (c == null) {
                    // Antes de poner un muro sólido, comprobamos si chocaría con alguna puerta ya creada
                    if (!isDoorIntersectingWall(r, dir)) {
                        a2WallBuilder.buildSolid(r, dir, y0, h);
                    }
                } else if (c.type() == ConnectionType.OPENING) {
                    float thickness = isCorridor(r) ? CORRIDOR_WALL_T : WALL_T;
                    a2WallBuilder.buildOpening(r, dir, y0, h, rooms, HOLE_W, thickness);
                } else { /* puerta */
                    a4DoorBuilder.build(r, dir, y0, h - 0.2f, rooms);
                }
            }
        }
    }

    private boolean isDoorIntersectingWall(Room r, Direction dir) {
        // DOOR_T es el grosor de la puerta, lo tomamos de tus constantes
        float halfDoorThick = DOOR_T * 0.5f + 0.1f; // + un pequeño margen
        for (Door d : doors) {
            Vector3f p = d.getAccessPoint(); // posición central de la puerta
            switch (dir) {
                case NORTH:
                    // plano Z = r.z
                    if (Math.abs(p.z - r.z()) < halfDoorThick
                            && p.x >= r.x() && p.x <= r.x() + r.w()) {
                        return true;
                    }
                    break;
                case SOUTH:
                    // plano Z = r.z + r.h
                    if (Math.abs(p.z - (r.z() + r.h())) < halfDoorThick
                            && p.x >= r.x() && p.x <= r.x() + r.w()) {
                        return true;
                    }
                    break;
                case WEST:
                    // plano X = r.x
                    if (Math.abs(p.x - r.x()) < halfDoorThick
                            && p.z >= r.z() && p.z <= r.z() + r.h()) {
                        return true;
                    }
                    break;
                case EAST:
                    // plano X = r.x + r.w
                    if (Math.abs(p.x - (r.x() + r.w())) < halfDoorThick
                            && p.z >= r.z() && p.z <= r.z() + r.h()) {
                        return true;
                    }
                    break;
            }
        }
        return false;
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

    private boolean isCorridor(Room r) {
        return Float.compare(r.w(), HOLE_W) == 0 || Float.compare(r.h(), HOLE_W) == 0;
    }

    public _6LightPlacer getLightPlacer() {
        return a7LightPlacer;
    }

    public boolean isDoorOpen() {
        return doorOpen;
    }
}
