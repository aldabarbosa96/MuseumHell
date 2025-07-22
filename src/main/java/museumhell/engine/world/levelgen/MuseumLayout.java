package museumhell.engine.world.levelgen;

import java.util.List;

/* engine.world.levelgen */
public record MuseumLayout(List<LevelLayout> floors, float floorHeight) {

    public float yOf(int floorIndex) { return floorIndex * floorHeight; }
}
