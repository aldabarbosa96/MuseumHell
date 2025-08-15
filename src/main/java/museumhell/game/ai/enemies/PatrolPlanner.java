package museumhell.game.ai.enemies;

import com.jme3.math.Vector3f;
import museumhell.engine.world.builders._4StairBuilder;
import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.enums.Direction;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.utils.GeoUtil;

import java.util.*;

import static museumhell.utils.ConstantManager.*;

public class PatrolPlanner {

    private enum EdgeType {DOOR, STAIRS}

    private record Edge(Room from, Room to, EdgeType type, Vector3f pre, Vector3f post, Vector3f roomCenter) {
    }

    private final MuseumLayout layout;
    private final Map<Room, List<Edge>> graph = new HashMap<>();

    public PatrolPlanner(MuseumLayout layout, WorldBuilder world) {
        this.layout = layout;
        buildGraph(layout, world);
    }

    public List<Vector3f> randomRoute(Room start) {
        if (start == null) return List.of();
        List<Vector3f> out = new ArrayList<>(64);
        Deque<Room> stack = new ArrayDeque<>();
        Set<Room> visited = new HashSet<>();
        stack.push(start);
        visited.add(start);
        Random rng = new Random();

        while (!stack.isEmpty()) {
            Room cur = stack.peek();
            List<Edge> edges = graph.getOrDefault(cur, List.of());
            Edge pick = null;
            int count = 0;
            for (Edge e : edges) {
                if (!visited.contains(e.to())) {
                    count++;
                    if (rng.nextInt(count) == 0) pick = e;
                }
            }
            if (pick == null) {
                stack.pop();
                continue;
            }
            addEdgeWaypoints(out, pick);
            visited.add(pick.to());
            stack.push(pick.to());
        }
        return out;
    }

    public List<Vector3f> routeTo(Room start, Room target) {
        List<Vector3f> out = new ArrayList<>();
        if (start == null || target == null || start == target) return out;

        Map<Room, Edge> parent = new HashMap<>();
        Deque<Room> q = new ArrayDeque<>();
        Set<Room> vis = new HashSet<>();

        vis.add(start);
        q.add(start);
        boolean found = false;

        while (!q.isEmpty()) {
            Room cur = q.remove();
            for (Edge e : graph.getOrDefault(cur, List.of())) {
                Room nxt = e.to();
                if (vis.contains(nxt)) continue;
                vis.add(nxt);
                parent.put(nxt, e);
                if (nxt == target) {
                    found = true;
                    q.clear();
                    break;
                }
                q.add(nxt);
            }
        }
        if (!found) return out;

        List<Edge> steps = new ArrayList<>();
        Room cur = target;
        while (cur != start) {
            Edge e = parent.get(cur);
            if (e == null) break;
            steps.add(e);
            cur = e.from();
        }
        Collections.reverse(steps);

        for (Edge e : steps) addEdgeWaypoints(out, e);
        return out;
    }

    public List<Vector3f> routeToLean(Room start, Room target, Vector3f finalTarget) {
        List<Vector3f> out = new ArrayList<>();
        if (start == null || target == null) {
            return out;
        }
        if (start == target) {
            // misma sala: apunta directo al jugador
            if (finalTarget != null) out.add(finalTarget.clone());
            return out;
        }

        Map<Room, Edge> parent = new HashMap<>();
        Deque<Room> q = new ArrayDeque<>();
        Set<Room> vis = new HashSet<>();
        vis.add(start);
        q.add(start);
        boolean found = false;

        while (!q.isEmpty()) {
            Room cur = q.remove();
            for (Edge e : graph.getOrDefault(cur, List.of())) {
                Room nxt = e.to();
                if (vis.contains(nxt)) continue;
                vis.add(nxt);
                parent.put(nxt, e);
                if (nxt == target) {
                    found = true;
                    q.clear();
                    break;
                }
                q.add(nxt);
            }
        }
        if (!found) {
            return out;
        }

        // reconstrucción de aristas
        List<Edge> steps = new ArrayList<>();
        Room cur = target;
        while (cur != start) {
            Edge e = parent.get(cur);
            if (e == null) break;
            steps.add(e);
            cur = e.from();
        }
        Collections.reverse(steps);

        // --- “lean”: solo portales (pre/post) y, al final, un punto hacia el jugador ---
        for (int i = 0; i < steps.size(); i++) {
            Edge e = steps.get(i);
            boolean last = (i == steps.size() - 1);
            out.add(e.pre());   // fuera de puerta/escalera
            out.add(e.post());  // dentro del portal (del otro lado)

            if (last && finalTarget != null) {
                out.add(new Vector3f(finalTarget.x, e.post().y, finalTarget.z));
            }
        }
        return out;
    }


    /* ------------------- Graph build ------------------- */

    private void buildGraph(MuseumLayout layout, WorldBuilder world) {
        // 1) Conexiones (puertas/aberturas) por planta
        for (int f = 0; f < layout.floors().size(); f++) {
            float y = layout.yOf(f) + 0.5f;
            var level = layout.floors().get(f);
            for (Connection c : level.conns()) {
                Room a = c.a();
                Room b = c.b();

                Vector3f ctr = doorCenter(a, b, c.dir(), y);

                Vector3f toA = a.center3f(y).subtract(ctr).normalizeLocal().multLocal(1.0f);
                Vector3f toB = b.center3f(y).subtract(ctr).normalizeLocal().multLocal(1.0f);

                Vector3f preA = ctr.add(toA);
                Vector3f postA = ctr.add(toB);

                // Edge A->B
                addEdge(a, new Edge(a, b, EdgeType.DOOR, preA, postA, b.center3f(y)));
                // Edge B->A
                addEdge(b, new Edge(b, a, EdgeType.DOOR, postA, preA, a.center3f(y)));
            }
        }

        // 2) Escaleras: une salas entre planta f y f+1
        _4StairBuilder.Plan plan = world.getStairPlan();
        if (plan == null) return;

        final float ENTRY_OUT = Math.max(0.6f, RAIL_T + 0.2f);
        final float ENTRY_IN = 0.6f;

        for (var sp : plan.placements) {
            int f = sp.floor();
            if (f < 0 || f + 1 >= layout.floors().size()) continue;

            var hole = computeHole(sp, layout.floorHeight());

            float cx = (hole.x1() + hole.x2()) * 0.5f;
            float cz = (hole.z1() + hole.z2()) * 0.5f;

            Room down = findRoomContaining(layout.floors().get(f).rooms(), cx, cz);
            Room up = findRoomContaining(layout.floors().get(f + 1).rooms(), cx, cz);
            if (down == null || up == null) continue;

            float yDown = layout.yOf(f) + 0.5f;
            float yUp = layout.yOf(f + 1) + 0.5f;

            boolean ew = isEW(sp); // true: corre en Z; false: corre en X
            Vector3f preDownOut, postUpIn, preUpOut, postDownIn;

            if (ew) {
                float zNear = hole.z1(); // pie (planta inferior)
                float zFar = hole.z2(); // boca (planta superior)

                preDownOut = new Vector3f(cx, yDown, zNear - ENTRY_OUT);
                postUpIn = new Vector3f(cx, yUp, zFar - ENTRY_IN);

                preUpOut = new Vector3f(cx, yUp, zFar + ENTRY_OUT);
                postDownIn = new Vector3f(cx, yDown, zNear + ENTRY_IN);
            } else {
                float xNear = hole.x1(); // pie (planta inferior)
                float xFar = hole.x2(); // boca (planta superior)

                preDownOut = new Vector3f(xNear - ENTRY_OUT, yDown, cz);
                postUpIn = new Vector3f(xFar - ENTRY_IN, yUp, cz);

                preUpOut = new Vector3f(xFar + ENTRY_OUT, yUp, cz);
                postDownIn = new Vector3f(xNear + ENTRY_IN, yDown, cz);
            }

            // down -> up: entrar por el pie (OUT) y salir arriba (IN)
            addEdge(down, new Edge(down, up, EdgeType.STAIRS, preDownOut, postUpIn, up.center3f(yUp)));

            // up -> down: entrar por arriba (OUT) y salir abajo (IN)
            addEdge(up, new Edge(up, down, EdgeType.STAIRS, preUpOut, postDownIn, down.center3f(yDown)));
        }
    }

    private void addEdge(Room key, Edge edge) {
        graph.computeIfAbsent(key, __ -> new ArrayList<>()).add(edge);
    }

    private static Room findRoomContaining(List<Room> rooms, float x, float z) {
        for (Room r : rooms) {
            if (x >= r.x() && x <= r.x() + r.w() && z >= r.z() && z <= r.z() + r.h()) return r;
        }
        return null;
    }

    private static void addEdgeWaypoints(List<Vector3f> out, Edge e) {
        // para puertas y escaleras: pre -> post -> centro de sala destino
        out.add(e.pre());
        out.add(e.post());
        out.add(e.roomCenter());
    }


    private static Vector3f doorCenter(Room a, Room b, Direction dir, float y) {
        float dxMid = (Math.max(a.x(), b.x()) + Math.min(a.x() + a.w(), b.x() + b.w())) * 0.5f;
        float dzMid = (Math.max(a.z(), b.z()) + Math.min(a.z() + a.h(), b.z() + b.h())) * 0.5f;
        return switch (dir) {
            case NORTH -> new Vector3f(dxMid, y, a.z());
            case SOUTH -> new Vector3f(dxMid, y, a.z() + a.h());
            case EAST -> new Vector3f(a.x() + a.w(), y, dzMid);
            case WEST -> new Vector3f(a.x(), y, dzMid);
        };
    }

    private static boolean isEW(_4StairBuilder.StairPlacement sp) {
        Enum<?> orient = (Enum<?>) sp.orientation();
        return "EW".equals(orient.name());
    }

    private static GeoUtil.Rect computeHole(_4StairBuilder.StairPlacement sp, float floorH) {
        int steps = (int) Math.ceil(floorH / STEP_H);
        float runD = steps * STEP_DEPTH;

        float hxPad = STAIR_WIDTH * 0.5f + RAIL_T;
        float pad = STAIR_CLEAR;

        boolean ew = isEW(sp);
        if (ew) {
            return new GeoUtil.Rect(sp.x() - hxPad, sp.x() + hxPad, sp.z() - STEP_DEPTH * .5f - pad, sp.z() + runD + pad);
        } else {
            return new GeoUtil.Rect(sp.x() - STEP_DEPTH * .5f - pad, sp.x() + runD + pad, sp.z() - hxPad, sp.z() + hxPad);
        }
    }
}
