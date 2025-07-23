package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.WorldBuilder.Rect;
import museumhell.engine.world.levelgen.LevelLayout;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.Stairs;

import java.util.*;

public class StairBuilder {
    private static final float DOOR_W = 2.5f;
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
        float hxPad = Stairs.WIDTH * .5f + 0.05f;

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

            boolean left = rnd.nextBoolean();
            if (respectDoors) {
                if (left && doorWest(ra, A)) return false;
                if (!left && doorEast(ra, A)) return false;
            }

            float sx = left ? ix1 + STAIR_WALL_GAP + Stairs.WIDTH * .5f : ix2 - STAIR_WALL_GAP - Stairs.WIDTH * .5f;
            float zMin = iz1 + Stairs.STEP_DEPTH * .5f + STAIR_FOOT_GAP;
            float zMax = iz2 - runD + Stairs.STEP_DEPTH * .5f - STAIR_FOOT_GAP;
            float sz = (zMax > zMin) ? FastMath.interpolateLinear(rnd.nextFloat(), zMin, zMax) : zMin;

            Rect hole = new Rect(sx - hxPad, sx + hxPad, sz - Stairs.STEP_DEPTH * .5f, sz + runD - Stairs.STEP_DEPTH * .5f);
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

    private boolean doorWest(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax = a.x();
        for (Room b : L.rooms()) {
            if (b != a && b.x() + b.w() == ax && overlap(az1, az2, b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR) {
                return true;
            }
        }
        return false;
    }

    private boolean doorEast(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax = a.x() + a.w();
        for (Room b : L.rooms()) {
            if (b != a && b.x() == ax && overlap(az1, az2, b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR) {
                return true;
            }
        }
        return false;
    }

    private int overlap(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }
}
