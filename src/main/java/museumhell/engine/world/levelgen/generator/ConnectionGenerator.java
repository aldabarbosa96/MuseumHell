package museumhell.engine.world.levelgen.generator;

import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.ConnectionType;
import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.levelgen.LevelLayout;
import museumhell.engine.world.levelgen.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConnectionGenerator {

    private static final float DOOR_W = 2f, MARGIN = 1f;
    private static final int MIN_OVERLAP_FOR_DOOR = (int) (DOOR_W + 2 * MARGIN);

    public static List<Connection> build(LevelLayout layout, long seed) {
        Random rnd = new Random(seed);
        List<Connection> out = new ArrayList<>();
        for (Room r : layout.rooms()) {
            for (Direction dir : Direction.values()) {
                Room nbr = findNeighbor(r, layout.rooms(), dir);
                if (nbr == null || alreadyAdded(out, r, nbr)) {
                    continue;
                }
                double p = rnd.nextDouble();
                ConnectionType t = (p < 0.7) ? ConnectionType.DOOR : (p < 0.8) ? ConnectionType.OPENING : ConnectionType.CORRIDOR;
                out.add(new Connection(r, nbr, dir, t));
            }
        }
        return out;
    }

    private static boolean alreadyAdded(List<Connection> conns, Room a, Room b) {
        return conns.stream().anyMatch(c -> (c.a() == a && c.b() == b) || (c.a() == b && c.b() == a));
    }

    private static Room findNeighbor(Room r, List<Room> rooms, Direction dir) {
        for (Room o : rooms) {
            switch (dir) {
                case NORTH:
                    if (o.z() + o.h() == r.z() && overlap(r.x(), r.x() + r.w(), o.x(), o.x() + o.w()) >= MIN_OVERLAP_FOR_DOOR) {
                        return o;
                    }
                    break;
                case SOUTH:
                    if (o.z() == r.z() + r.h() && overlap(r.x(), r.x() + r.w(), o.x(), o.x() + o.w()) >= MIN_OVERLAP_FOR_DOOR) {
                        return o;
                    }
                    break;
                case WEST:
                    if (o.x() + o.w() == r.x() && overlap(r.z(), r.z() + r.h(), o.z(), o.z() + o.h()) >= MIN_OVERLAP_FOR_DOOR) {
                        return o;
                    }
                    break;
                case EAST:
                    if (o.x() == r.x() + r.w() && overlap(r.z(), r.z() + r.h(), o.z(), o.z() + o.h()) >= MIN_OVERLAP_FOR_DOOR) {
                        return o;
                    }
                    break;
            }
        }
        return null;
    }

    private static int overlap(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }
}
