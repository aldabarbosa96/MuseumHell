package museumhell.engine.world.levelgen.generator;

import museumhell.engine.world.levelgen.LevelLayout;
import museumhell.engine.world.levelgen.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class BspGenerator {
    private static final int MIN_ROOM = 10;   // lado mínimo de una sala
    private static final int MIN_SPLIT = MIN_ROOM * 2 + 2;
    private static final int MAX_DEPTH = 8;

    private record Node(int x, int z, int w, int h, int depth) {
    }

    public static LevelLayout generate(int width, int height, long seed) {
        Random rnd = new Random(seed);
        List<Room> rooms = new ArrayList<>();
        split(new Node(0, 0, width, height, 0), rnd, rooms);
        return new LevelLayout(rooms);
    }

    private static void split(Node n, Random rnd, List<Room> out) {

        boolean canSplitHoriz = n.w >= MIN_SPLIT;
        boolean canSplitVert = n.h >= MIN_SPLIT;

        /*  ── Hoja ── */
        if (n.depth == MAX_DEPTH || (!canSplitHoriz && !canSplitVert)) {
            out.add(new Room(n.x, n.z, n.w, n.h));
            return;
        }

        boolean splitVert = (canSplitHoriz && canSplitVert) ? n.w > n.h : canSplitHoriz;

        if (splitVert) {             // corte vertical ⇒ izquierda / derecha
            int cutMin = MIN_ROOM;
            int cutMax = n.w - MIN_ROOM;
            int cut = cutMin + rnd.nextInt(cutMax - cutMin);

            split(new Node(n.x, n.z, cut, n.h, n.depth + 1), rnd, out);
            split(new Node(n.x + cut, n.z, n.w - cut, n.h, n.depth + 1), rnd, out);

        } else {                     // corte horizontal ⇒ arriba / abajo
            int cutMin = MIN_ROOM;
            int cutMax = n.h - MIN_ROOM;
            int cut = cutMin + rnd.nextInt(cutMax - cutMin);

            split(new Node(n.x, n.z, n.w, cut, n.depth + 1), rnd, out);
            split(new Node(n.x, n.z + cut, n.w, n.h - cut, n.depth + 1), rnd, out);
        }
    }

}
