package museumhell.utils;

import museumhell.engine.world.levelgen.Direction;

import java.util.List;

public final class GeoUtil {

    private GeoUtil() {
    }

    public static Direction opposite(Direction d) {
        return switch (d) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }

    public static int overlap(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }

    public static boolean intersects(Rect r, Rect o) {
        return r.x1() < o.x2() && r.x2() > o.x1() && r.z1() < o.z2() && r.z2() > o.z1();
    }

    public static boolean intersectsAny(Rect r, List<Rect> list) {
        for (Rect o : list) if (intersects(r, o)) return true;
        return false;
    }

    public record Rect(float x1,float x2,float z1,float z2){}

}
