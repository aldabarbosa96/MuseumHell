package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.engine.world.levelgen.*;
import museumhell.utils.GeoUtil;
import museumhell.utils.GeoUtil.*;

import java.util.*;

import static museumhell.engine.world.levelgen.Direction.*;
import static museumhell.utils.ConstantManager.*;

public class _5StairBuilder {

    private enum Orientation {EW, NS}

    public static class Plan {
        public final Map<Integer, List<Rect>> holes;
        public final List<StairPlacement> placements;

        public Plan(Map<Integer, List<Rect>> h, List<StairPlacement> p) {
            holes = h;
            placements = p;
        }
    }

    public record StairPlacement(int floor, float x, float z, Orientation orientation) {
    }

    private final AssetManager am;
    private final PhysicsSpace ps;
    private final Node root;

    public _5StairBuilder(AssetManager am, PhysicsSpace space, Node root) {
        this.am = am;
        this.ps = space;
        this.root = root;
    }

    public Plan plan(MuseumLayout museum) {

        Map<Integer, List<Rect>> holes = new HashMap<>();
        List<StairPlacement> out = new ArrayList<>();

        List<LevelLayout> floors = museum.floors();
        if (floors.size() < 2) return new Plan(holes, out);

        float floorH = museum.floorHeight();
        int steps = (int) Math.ceil(floorH / STEP_H);
        float runD = steps * STEP_DEPTH;
        float hxPad = STAIR_WIDTH * 0.5f;

        Random rnd = new Random(floors.size() * 73L);

        for (int f = 0; f < floors.size() - 1; f++) {

            LevelLayout A = floors.get(f);
            LevelLayout B = floors.get(f + 1);

            Map<Room, Boolean> used = new HashMap<>();
            int placed = 0;

            List<Room> roomsA = new ArrayList<>(A.rooms());
            Collections.shuffle(roomsA, rnd);

            for (boolean respectDoors : List.of(true, false)) {
                for (Room ra : roomsA) {
                    if (placed >= MAX_STAIRS) break;
                    if (used.getOrDefault(ra, false)) continue;

                    if (tryPlace(rnd, A, B, f, runD, hxPad, holes, out, ra, respectDoors)) {
                        used.put(ra, true);
                        placed++;
                    }
                }
            }
        }
        return new Plan(holes, out);
    }

    private boolean tryPlace(Random rnd, LevelLayout A, LevelLayout B, int f, float runD, float hxPad, Map<Integer, List<Rect>> holes, List<StairPlacement> out, Room ra, boolean respectDoors) {

        /* probamos ambas orientaciones */
        return attempt(rnd, A, B, f, runD, hxPad, holes, out, ra, respectDoors, Orientation.EW) || attempt(rnd, A, B, f, runD, hxPad, holes, out, ra, respectDoors, Orientation.NS);
    }

    private boolean attempt(Random rnd, LevelLayout A, LevelLayout B, int f, float runD, float hxPad, Map<Integer, List<Rect>> holes, List<StairPlacement> out, Room ra, boolean respectDoors, Orientation orientation) {

        List<Room> roomsB = new ArrayList<>(B.rooms());
        Collections.shuffle(roomsB, rnd);

        for (Room rb : roomsB) {
            /* ---------- intersección horizontal ---------- */
            int ix1 = Math.max(ra.x(), rb.x());
            int ix2 = Math.min(ra.x() + ra.w(), rb.x() + rb.w());
            int iz1 = Math.max(ra.z(), rb.z());
            int iz2 = Math.min(ra.z() + ra.h(), rb.z() + rb.h());

            /* ---------- hueco mínimo ---------- */
            if (orientation == Orientation.EW) {
                if (ix2 - ix1 < STAIR_WIDTH + STAIR_WALL_GAP * 2) continue;
                if (iz2 - iz1 < runD + STAIR_FOOT_GAP * 2) continue;
            } else {
                if (iz2 - iz1 < STAIR_WIDTH + STAIR_WALL_GAP * 2) continue;
                if (ix2 - ix1 < runD + STAIR_FOOT_GAP * 2) continue;
            }

            /* ---------- puertas laterales ---------- */
            boolean s1Blocked, s2Blocked;
            if (orientation == Orientation.EW) {
                s1Blocked = !doorSpans(ra, WEST, A).isEmpty() || !doorSpans(rb, WEST, B).isEmpty();
                s2Blocked = !doorSpans(ra, EAST, A).isEmpty() || !doorSpans(rb, EAST, B).isEmpty();
            } else {
                s1Blocked = !doorSpans(ra, NORTH, A).isEmpty() || !doorSpans(rb, NORTH, B).isEmpty();
                s2Blocked = !doorSpans(ra, SOUTH, A).isEmpty() || !doorSpans(rb, SOUTH, B).isEmpty();
            }
            if (s1Blocked && s2Blocked) continue;
            if (respectDoors && (s1Blocked || s2Blocked)) continue;

            /* ---------- posición ---------- */
            boolean nearS1 = s2Blocked || (!s1Blocked && rnd.nextBoolean());
            float innerOff = WALL_T + STAIR_WALL_GAP + STAIR_WIDTH * 0.5f;

            float sx, sz;
            if (orientation == Orientation.EW) {
                sx = nearS1 ? ix1 + innerOff : ix2 - innerOff;
                float zMin = iz1 + STEP_DEPTH * 0.5f + STAIR_FOOT_GAP;
                float zMax = iz2 - runD + STEP_DEPTH * 0.5f - STAIR_FOOT_GAP;
                sz = (zMax > zMin) ? FastMath.interpolateLinear(rnd.nextFloat(), zMin, zMax) : zMin;
                if (sz + runD + STEP_DEPTH * 0.5f + STAIR_FOOT_GAP > rb.z() + rb.h() - WALL_T) continue;
            } else {
                sz = nearS1 ? iz1 + innerOff : iz2 - innerOff;
                float xMin = ix1 + STEP_DEPTH * 0.5f + STAIR_FOOT_GAP;
                float xMax = ix2 - runD + STEP_DEPTH * 0.5f - STAIR_FOOT_GAP;
                sx = (xMax > xMin) ? FastMath.interpolateLinear(rnd.nextFloat(), xMin, xMax) : xMin;
                if (sx + runD + STEP_DEPTH * 0.5f + STAIR_FOOT_GAP > rb.x() + rb.w() - WALL_T) continue;
            }


            float pad = 0.05f;
            Rect hole;
            if (orientation == Orientation.EW) {
                hole = new Rect(sx - hxPad, sx + hxPad, sz - STEP_DEPTH * 0.5f - pad, sz + runD + pad);
            } else { /* N‑S */
                hole = new Rect(sx - STEP_DEPTH * 0.5f - pad, sx + runD + pad, sz - hxPad, sz + hxPad);
            }

            /* ---------- colisiones ---------- */
            if (GeoUtil.intersectsAny(hole, holes.getOrDefault(f, List.of()))) continue;
            if (GeoUtil.intersectsAny(hole, holes.getOrDefault(f + 1, List.of()))) continue;

            /* ---------- registrar ---------- */
            holes.computeIfAbsent(f, k -> new ArrayList<>()).add(hole);
            holes.computeIfAbsent(f + 1, k -> new ArrayList<>()).add(hole);
            out.add(new StairPlacement(f, sx, sz, orientation));
            return true;
        }
        return false;
    }


    public void place(Plan plan, MuseumLayout museum) {

        float floorH = museum.floorHeight();

        for (StairPlacement sp : plan.placements) {

            /* ---------- escalones ---------- */
            float y0 = museum.yOf(sp.floor());
            Vector3f f = new Vector3f(sp.x(), y0, sp.z());

            if (sp.orientation() == Orientation.EW) {
                Stairs.add(root, ps, am, f, floorH);
            } else {
                addStairsNS(f, floorH);
            }

            /* ---------- barandilla en la planta superior ---------- */
            Rect hole = computeHole(sp, floorH);
            float yTop = y0 + floorH;

            float x1 = hole.x1(), x2 = hole.x2();
            float z1 = hole.z1(), z2 = hole.z2();
            float w = x2 - x1;                         // ancho   (eje X)
            float d = z2 - z1;                         // profundo(eje Z)

            /* lado que queda ABIERTO (por donde llegas a la planta) */
            enum Side {N, S, W, E}
            Side open = (sp.orientation() == Orientation.EW) ? Side.S : Side.E;

            /* --- N (z1) --- */
            addRail(new Vector3f((x1 + x2) * .5f, 0, z1 - RAIL_T * .5f), w, RAIL_T, yTop);
            /* --- S (z2) --- */
            if (open != Side.S) {
                addRail(new Vector3f((x1 + x2) * .5f, 0, z2 + RAIL_T * .5f), w, RAIL_T, yTop);
            }
            /* --- W (x1) --- */
            addRail(new Vector3f(x1 - RAIL_T * .5f, 0, (z1 + z2) * .5f), RAIL_T, d, yTop);
            /* --- E (x2) --- */
            if (open != Side.E) {
                addRail(new Vector3f(x2 + RAIL_T * .5f, 0, (z1 + z2) * .5f), RAIL_T, d, yTop);
            }
        }
    }


    /* ---------- versión rotada 90° ---------- */
    private void addStairsNS(Vector3f foot, float floorH) {

        int steps = (int) Math.ceil(floorH / STEP_H);

        var mat = GeoUtil.makeMat(am);

        for (int i = 0; i < steps; i++) {
            float yC = foot.y + STEP_H * .5f + i * STEP_H;
            float xC = foot.x + STEP_DEPTH * .5f + i * STEP_DEPTH;

            var shape = new com.jme3.scene.shape.Box(STEP_DEPTH * .5f, STEP_H * .5f, STAIR_WIDTH * .5f);
            var g = new com.jme3.scene.Geometry("StepNS_" + i, shape);
            g.setMaterial(mat.clone());
            g.setLocalTranslation(xC, yC, foot.z);
            g.addControl(new com.jme3.bullet.control.RigidBodyControl(0));
            root.attachChild(g);
            ps.add(g);
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
            if (c.type() == ConnectionType.CORRIDOR) continue;
            boolean match = (c.a() == r && c.dir() == dir) || (c.b() == r && GeoUtil.opposite(c.dir()) == dir);
            if (!match) continue;

            Room o = (c.a() == r) ? c.b() : c.a();
            if (dir == NORTH || dir == SOUTH) {
                float x1 = Math.max(r.x(), o.x()), x2 = Math.min(r.x() + r.w(), o.x() + o.w());
                list.add(new Span(x1, x2));
            } else {
                float z1 = Math.max(r.z(), o.z()), z2 = Math.min(r.z() + r.h(), o.z() + o.h());
                list.add(new Span(z1, z2));
            }
        }
        return list;
    }

    private void addRail(Vector3f center, float sx, float sz, float yBase) {
        var shape = new Box(sx * .5f, RAIL_H * .5f, sz * .5f);
        var g = new Geometry("Rail", shape);

        g.setMaterial(GeoUtil.makeRailMat(am));
        g.setLocalTranslation(center.x, yBase + RAIL_H * .5f, center.z);

        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        ps.add(g);
    }

    private Rect computeHole(StairPlacement sp, float floorH) {

        int steps = (int) Math.ceil(floorH / STEP_H);
        float runD = steps * STEP_DEPTH;

        float hxPad = STAIR_WIDTH * 0.5f + RAIL_T;
        float pad = STAIR_CLEAR;

        boolean ew = (sp.orientation() == Orientation.EW);

        return ew ? new Rect(sp.x() - hxPad, sp.x() + hxPad, sp.z() - STEP_DEPTH * .5f - pad, sp.z() + runD + pad) : new Rect(sp.x() - STEP_DEPTH * .5f - pad, sp.x() + runD + pad, sp.z() - hxPad, sp.z() + hxPad);
    }


}
