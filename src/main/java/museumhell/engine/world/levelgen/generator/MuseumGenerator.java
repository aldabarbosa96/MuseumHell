package museumhell.engine.world.levelgen.generator;


import museumhell.engine.world.levelgen.LevelLayout;
import museumhell.engine.world.levelgen.MuseumLayout;

import java.util.ArrayList;
import java.util.List;

public final class MuseumGenerator {

    public static MuseumLayout generate(int w, int d, int floors, long seed) {
        if (floors < 1 || floors > 3) throw new IllegalArgumentException();
        List<LevelLayout> list = new ArrayList<>(floors);
        for (int i = 0; i < floors; i++) {
            // semilla distinta pero reproducible
            long s = seed + i * 1_337;
            list.add(BspGenerator.generate(w, d, s));
        }
        return new MuseumLayout(list, 6f);
    }
}
