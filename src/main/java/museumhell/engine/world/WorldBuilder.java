package museumhell.engine.world;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.builders.*;
import museumhell.engine.world.levelgen.*;
import museumhell.engine.world.levelgen.generator.ConnectionGenerator;
import museumhell.utils.AssetLoader;
import museumhell.utils.GeoUtil;

import java.util.*;

public class WorldBuilder {
    private static final float DOOR_W = 2.5f, WALL_T = 2f, MARGIN = 1f;
    private static final float HOLE_W = DOOR_W * 2f;
    private static final int MIN_OVERLAP_FOR_DOOR = (int) (DOOR_W + 2 * MARGIN);
    private static final float CORRIDOR_WALL_T = WALL_T * 3f;

    private final LightPlacer lightPlacer;
    private final FloorBuilder floorBuilder;
    private final WallBuilder wallBuilder;
    private final DoorBuilder doorBuilder;
    private final CorridorBuilder corridorBuilder;
    private final StairBuilder stairBuilder;

    private final AssetManager am;
    private final Node root;
    private final PhysicsSpace space;
    private final List<Door> doors = new ArrayList<>();
    private boolean doorOpen = false;

    public record Rect(float x1, float x2, float z1, float z2) {
        public float x1() {
            return x1;
        }

        public float x2() {
            return x2;
        }

        public float z1() {
            return z1;
        }

        public float z2() {
            return z2;
        }
    }

    public WorldBuilder(AssetManager am, Node root, PhysicsSpace space, AssetLoader assetLoader) {
        this.am = am;
        this.root = root;
        this.space = space;
        this.lightPlacer = new LightPlacer(root);
        this.floorBuilder = new FloorBuilder(root, space, am);
        this.wallBuilder = new WallBuilder(am,root, space, assetLoader);
        this.doorBuilder = new DoorBuilder(am, space, root, doors, assetLoader);
        this.corridorBuilder = new CorridorBuilder(am, root, space);
        this.stairBuilder = new StairBuilder(am, space, root);
    }


    // 1) build: genera conexiones y las pasa a buildSingleFloor
    public void build(MuseumLayout museum) {
        float h = museum.floorHeight();

        // 1) Generar conexiones por planta
        List<List<Connection>> floorConns = new ArrayList<>();
        long seed = System.nanoTime();
        for (LevelLayout lvl : museum.floors()) {
            floorConns.add(ConnectionGenerator.build(lvl, seed++));
        }

        // 2) Planificar escaleras (huecos + posiciones)
        StairBuilder.Plan plan = stairBuilder.plan(museum);

        // 3) Construir cada planta, recortando con plan.holes
        for (int i = 0; i < museum.floors().size(); i++) {
            LevelLayout lvl = museum.floors().get(i);
            List<Connection> conns = floorConns.get(i);
            List<Rect> holes = plan.holes.getOrDefault(i, List.of());
            buildSingleFloor(lvl, conns, museum.yOf(i), h, holes, i == 0);
        }

        // 4) Colocar las escaleras en la escena
        stairBuilder.place(plan, museum);
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

    private void buildSingleFloor(LevelLayout layout, List<Connection> conns, float y0, float h, List<Rect> holes, boolean skipFloorHoles) {
        List<Rect> floorHoles = skipFloorHoles ? List.of() : holes;
        List<Room> rooms = layout.rooms();

        // 1) Iluminación
        lightPlacer.placeLights(rooms, y0, h);

        // 2) Suelo y techo
        for (Room r : rooms) {
            if (isCorridor(r)) {
                corridorBuilder.build(r, y0, h);
                continue;
            }
            floorBuilder.buildPatches(r.x(), r.z(), r.w(), r.h(), floorHoles, y0 - 0.1f, 0.1f, "Floor", ColorRGBA.Brown);
            floorBuilder.buildPatches(r.x(), r.z(), r.w(), r.h(), holes, y0 + h, 0.1f, "Ceil", ColorRGBA.Red);

            // 3) Muros y aberturas
            for (Direction dir : List.of(Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST)) {
                // No pintamos muros interiores
                if ((dir == Direction.SOUTH || dir == Direction.EAST) && hasNeighbor(r, rooms, dir)) {
                    continue;
                }

                Connection c = findConnection(conns, r, dir);
                if (c == null) {
                    wallBuilder.buildSolid(r, dir, y0, h);
                } else if (c.type() == ConnectionType.OPENING) {
                    float thickness = isCorridor(r) ? CORRIDOR_WALL_T : WALL_T;
                    wallBuilder.buildOpening(r, dir, y0, h, rooms, HOLE_W, thickness);
                } else { // Connection.Type.DOOR
                    doorBuilder.build(r, dir, y0, h, rooms);
                }
            }
        }
    }

    private boolean hasNeighbor(Room a, List<Room> rooms, Direction dir) {
        for (Room b : rooms) {
            switch (dir) {
                case NORTH -> {
                    if (b.z() + b.h() == a.z() && GeoUtil.overlap(a.x(), a.x() + a.w(), b.x(), b.x() + b.w()) >= MIN_OVERLAP_FOR_DOOR)
                        return true;
                }
                case SOUTH -> {
                    if (b.z() == a.z() + a.h() && GeoUtil.overlap(a.x(), a.x() + a.w(), b.x(), b.x() + b.w()) >= MIN_OVERLAP_FOR_DOOR)
                        return true;
                }
                case WEST -> {
                    if (b.x() + b.w() == a.x() && GeoUtil.overlap(a.z(), a.z() + a.h(), b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR)
                        return true;
                }
                case EAST -> {
                    if (b.x() == a.x() + a.w() && GeoUtil.overlap(a.z(), a.z() + a.h(), b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR)
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
            if (c.b() == room && GeoUtil.opposite(c.dir()) == dir) {
                return c;
            }
        }
        return null;
    }

    private boolean isCorridor(Room r) {
        return Float.compare(r.w(), HOLE_W) == 0 || Float.compare(r.h(), HOLE_W) == 0;
    }

    public LightPlacer getLightPlacer() {
        return lightPlacer;
    }

    public boolean isDoorOpen() {
        return doorOpen;
    }
}
