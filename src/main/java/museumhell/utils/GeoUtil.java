package museumhell.utils;

import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.WorldBuilder.Rect;

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

    /**
     * Devuelve la longitud del solapamiento entre [a1,a2] y [b1,b2] (0 si no hay).
     */
    public static int overlap(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }

    /**
     * Rectángulos eje-paralelos, ¿se tocan?
     */
    public static boolean intersects(Rect r, Rect o) {
        return r.x1() < o.x2() && r.x2() > o.x1() && r.z1() < o.z2() && r.z2() > o.z1();
    }

    public static boolean intersectsAny(Rect r, List<Rect> list) {
        for (Rect o : list) if (intersects(r, o)) return true;
        return false;
    }
}
