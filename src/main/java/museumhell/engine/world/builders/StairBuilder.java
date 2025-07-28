package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.WorldBuilder.Rect;
import museumhell.engine.world.levelgen.*;

import java.util.*;

public class StairBuilder {
    private static final float DOOR_W = 2.5f;
    private static final float WALL_T = 2f;
    private static final float MARGIN = 1f;
    private static final int MIN_OVERLAP_FOR_DOOR = (int) (DOOR_W + 2 * MARGIN);
    private static final float STAIR_WALL_GAP = 0.20f;
    private static final float STAIR_FOOT_GAP = 1.5f;
    private static final int MAX_STAIRS = 3;

    public static class Plan {
        public final Map<Integer, List<Rect>> holes;
        public final List<StairPlacement> placements;

        public Plan(Map<Integer, List<Rect>> holes, List<StairPlacement> placements) {
            this.holes = holes;
            this.placements = placements;
        }
    }

    public record StairPlacement(int floor, float x, float z) {
    }

    private final AssetManager assetManager;
    private final PhysicsSpace space;
    private final Node root;

    public StairBuilder(AssetManager am, PhysicsSpace space, Node root) {
        this.assetManager = am;
        this.space = space;
        this.root = root;
    }

    public Plan plan(MuseumLayout museum) {
        Map<Integer, List<Rect>> holes = new HashMap<>();
        List<StairPlacement> placements = new ArrayList<>();
        List<LevelLayout> floors = museum.floors();
        float floorHeight = museum.floorHeight();
        if (floors.size() < 2) return new Plan(holes, placements);

        Random rnd = new Random(floors.size() * 73L);
        int steps = (int) Math.ceil(floorHeight / Stairs.STEP_H);
        float runD = steps * Stairs.STEP_DEPTH;
        float hxPad = Stairs.WIDTH * 0.5f + 0.05f;

        for (int f = 0; f < floors.size() - 1; f++) {
            LevelLayout A = floors.get(f);
            LevelLayout B = floors.get(f + 1);
            Map<Room, Boolean> used = new HashMap<>();
            int placed = 0;

            List<Room> roomsA = new ArrayList<>(A.rooms());
            Collections.shuffle(roomsA, rnd);

            // primer pase: respetar puertas
            for (Room ra : roomsA) {
                if (placed >= MAX_STAIRS) break;
                if (used.getOrDefault(ra, false)) continue;
                if (tryPlaceStair(rnd, A, B, f, runD, hxPad, holes, placements, ra, true)) {
                    used.put(ra, true);
                    placed++;
                }
            }
            // segundo pase: ignorar puertas si no se completó
            if (placed < MAX_STAIRS) {
                Collections.shuffle(roomsA, rnd);
                for (Room ra : roomsA) {
                    if (placed >= MAX_STAIRS) break;
                    if (used.getOrDefault(ra, false)) continue;
                    if (tryPlaceStair(rnd, A, B, f, runD, hxPad, holes, placements, ra, false)) {
                        used.put(ra, true);
                        placed++;
                    }
                }
            }
        }

        return new Plan(holes, placements);
    }

    private boolean tryPlaceStair(Random rnd, LevelLayout A, LevelLayout B, int f, float runD, float hxPad, Map<Integer, List<Rect>> holes, List<StairPlacement> placements, Room ra, boolean respectDoors) {

        List<Room> roomsB = new ArrayList<>(B.rooms());
        Collections.shuffle(roomsB, rnd);

        for (Room rb : roomsB) {
            int ix1 = Math.max(ra.x(), rb.x());
            int ix2 = Math.min(ra.x() + ra.w(), rb.x() + rb.w());
            int iz1 = Math.max(ra.z(), rb.z());
            int iz2 = Math.min(ra.z() + ra.h(), rb.z() + rb.h());
            if (ix2 - ix1 < Stairs.WIDTH + STAIR_WALL_GAP * 2) continue;
            if (iz2 - iz1 < runD + STAIR_FOOT_GAP * 2) continue;

            boolean westBlocked = !doorSpans(ra, Direction.WEST, A).isEmpty();
            boolean eastBlocked = !doorSpans(ra, Direction.EAST, A).isEmpty();
            if (westBlocked && eastBlocked) continue;
            if (respectDoors && (westBlocked || eastBlocked)) continue;

            boolean left = eastBlocked || (!westBlocked && rnd.nextBoolean());
            float innerOffset = WALL_T + STAIR_WALL_GAP + Stairs.WIDTH * 0.5f;
            float sx = left ? ix1 + innerOffset : ix2 - innerOffset;

            float zMin = iz1 + Stairs.STEP_DEPTH * 0.5f + STAIR_FOOT_GAP;
            float zMax = iz2 - runD + Stairs.STEP_DEPTH * 0.5f - STAIR_FOOT_GAP;
            float sz = (zMax > zMin) ? FastMath.interpolateLinear(rnd.nextFloat(), zMin, zMax) : zMin;

            float topFront = sz + runD + Stairs.STEP_DEPTH * 0.5f + STAIR_FOOT_GAP;
            if (topFront > rb.z() + rb.h() - WALL_T) continue;   // sin espacio arriba

            float pad = 0.05f;
            Rect hole = new Rect(sx - hxPad, sx + hxPad, sz - Stairs.STEP_DEPTH * 0.5f - pad, sz + runD - Stairs.STEP_DEPTH * 0.5f + pad);

            if (intersectsAny(hole, holes.getOrDefault(f, List.of()))) continue;
            if (intersectsAny(hole, holes.getOrDefault(f + 1, List.of()))) continue;

            holes.computeIfAbsent(f, k -> new ArrayList<>()).add(hole);
            holes.computeIfAbsent(f + 1, k -> new ArrayList<>()).add(hole);
            placements.add(new StairPlacement(f, sx, sz));
            return true;
        }
        return false;
    }


    public void place(Plan plan, MuseumLayout museum) {
        float floorHeight = museum.floorHeight();
        for (StairPlacement sp : plan.placements) {
            float y0 = museum.yOf(sp.floor());
            Vector3f pos = new Vector3f(sp.x(), y0, sp.z());
            Stairs.add(root, space, assetManager, pos, floorHeight);
        }
    }

    private record Span(float a, float b) {
        boolean overlaps(float x1, float x2) {
            return Math.min(b, x2) - Math.max(a, x1) > 0;
        }
    }

    private List<Span> doorSpans(Room r, Direction dir, LevelLayout L) {
        List<Span> list = new ArrayList<>();
        for (Connection c : L.conns()) {
            if (c.type() == ConnectionType.CORRIDOR) continue;   // ignoramos corredores
            boolean match = (c.a() == r && c.dir() == dir) || (c.b() == r && opposite(c.dir()) == dir);
            if (!match) continue;

            // proyectamos la solapa
            if (dir == Direction.NORTH || dir == Direction.SOUTH) {
                Room o = c.a() == r ? c.b() : c.a();
                float x1 = Math.max(r.x(), o.x()), x2 = Math.min(r.x() + r.w(), o.x() + o.w());
                list.add(new Span(x1, x2));
            } else {
                Room o = c.a() == r ? c.b() : c.a();
                float z1 = Math.max(r.z(), o.z()), z2 = Math.min(r.z() + r.h(), o.z() + o.h());
                list.add(new Span(z1, z2));
            }
        }
        return list;
    }

    private static Direction opposite(Direction d) {
        return switch (d) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }

    private static boolean intersectsAny(Rect r, List<Rect> list) {
        for (Rect o : list) {
            if (r.x1() < o.x2() && r.x2() > o.x1() && r.z1() < o.z2() && r.z2() > o.z1()) return true;
        }
        return false;
    }

}
