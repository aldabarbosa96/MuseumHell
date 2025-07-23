package museumhell.engine.world.levelgen.generator;


import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.LevelLayout;
import museumhell.engine.world.levelgen.MuseumLayout;

import java.util.ArrayList;
import java.util.List;

public final class MuseumGenerator {

    public static MuseumLayout generate(int w, int d, int floors, long seed) {
        if (floors < 1 || floors > 3) throw new IllegalArgumentException();
        List<LevelLayout> list = new ArrayList<>(floors);
        for (int i = 0; i < floors; i++) {
            long s = seed + i * 1_337;
            LevelLayout floor = BspGenerator.generate(w, d, s);
            List<Connection> conns = ConnectionGenerator.build(floor, s);
            list.add(new LevelLayout(floor.rooms(), conns));
        }

        return new MuseumLayout(list, 6f);
    }
}
