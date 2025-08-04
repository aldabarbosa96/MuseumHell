package museumhell.game.ai;

import com.jme3.math.Vector3f;
import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.enums.Direction;

import java.util.*;

public class PatrolPlanner {

    private record NavEdge(Room from, Room to, Vector3f preDoor, Vector3f postDoor) {
    }

    private final Map<Room, List<NavEdge>> graph = new HashMap<>();
    private final Random rng = new Random();


    public PatrolPlanner(MuseumLayout layout, int floorIdx) {
        float y = layout.yOf(floorIdx) + 0.5f;

        for (Connection c : layout.floors().get(floorIdx).conns()) {
            Room a = c.a();
            Room b = c.b();
            Vector3f doorCtr = doorCenter(a, b, c.dir(), y);

            Vector3f toA = a.center3f(y).subtract(doorCtr).normalizeLocal().multLocal(0.6f);
            Vector3f toB = b.center3f(y).subtract(doorCtr).normalizeLocal().multLocal(0.6f);

            Vector3f preA = doorCtr.add(toA);
            Vector3f preB = doorCtr.add(toB);

            graph.computeIfAbsent(a, k -> new ArrayList<>()).add(new NavEdge(a, b, preA, preB));
            graph.computeIfAbsent(b, k -> new ArrayList<>()).add(new NavEdge(b, a, preB, preA));
        }
    }


    public List<Vector3f> randomRoute(Room start) {
        List<Vector3f> waypoints = new ArrayList<>();
        Deque<Room> stack = new ArrayDeque<>();
        Set<Room> visited = new HashSet<>();

        stack.push(start);
        visited.add(start);

        while (!stack.isEmpty()) {
            Room cur = stack.peek();
            var candidates = graph.getOrDefault(cur, List.of()).stream().filter(e -> !visited.contains(e.to())).toList();
            if (candidates.isEmpty()) {
                stack.pop();
                continue;
            }

            NavEdge edge = candidates.get(rng.nextInt(candidates.size()));

            waypoints.add(edge.preDoor());
            waypoints.add(edge.postDoor());
            waypoints.add(edge.to().center3f(edge.preDoor().y));

            visited.add(edge.to());
            stack.push(edge.to());
        }
        return waypoints;
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
