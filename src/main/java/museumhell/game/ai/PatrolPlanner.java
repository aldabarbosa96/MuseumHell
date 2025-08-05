package museumhell.game.ai;

import com.jme3.math.Vector3f;
import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.enums.Direction;

import java.util.*;

public class PatrolPlanner {

    private record NavEdge(Room from, Room to, Vector3f preDoor, Vector3f postDoor, Vector3f roomCenter) {
    }

    private final Map<Room, NavEdge[]> graph = new HashMap<>();
    private final Random rng = new Random();

    public PatrolPlanner(MuseumLayout layout, int floorIdx) {
        float y = layout.yOf(floorIdx) + 0.5f;
        Map<Room, List<NavEdge>> tmp = new HashMap<>();

        for (Connection c : layout.floors().get(floorIdx).conns()) {
            Room a = c.a();
            Room b = c.b();
            Vector3f ctr = doorCenter(a, b, c.dir(), y);

            Vector3f toA = a.center3f(y).subtract(ctr).normalizeLocal().multLocal(0.6f);
            Vector3f toB = b.center3f(y).subtract(ctr).normalizeLocal().multLocal(0.6f);

            Vector3f preA = ctr.add(toA);
            Vector3f preB = ctr.add(toB);

            tmp.computeIfAbsent(a, __ -> new ArrayList<>()).add(new NavEdge(a, b, preA, preB, b.center3f(y)));
            tmp.computeIfAbsent(b, __ -> new ArrayList<>()).add(new NavEdge(b, a, preB, preA, a.center3f(y)));
        }

        tmp.forEach((room, list) -> graph.put(room, list.toArray(new NavEdge[0])));
    }

    public List<Vector3f> randomRoute(Room start) {
        List<Vector3f> out = new ArrayList<>(32);
        Deque<Room> stack = new ArrayDeque<>();
        Set<Room> visited = new HashSet<>();
        stack.push(start);
        visited.add(start);

        while (!stack.isEmpty()) {
            Room cur = stack.peek();
            NavEdge[] edges = graph.get(cur);
            NavEdge pick = null;
            int count = 0;
            if (edges != null) {
                for (NavEdge e : edges) {
                    if (!visited.contains(e.to())) {
                        count++;
                        if (rng.nextInt(count) == 0) pick = e;
                    }
                }
            }
            if (pick == null) {
                stack.pop();
                continue;
            }
            out.add(pick.preDoor());
            out.add(pick.postDoor());
            out.add(pick.roomCenter());
            visited.add(pick.to());
            stack.push(pick.to());
        }
        return out;
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
}
