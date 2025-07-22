package museumhell.engine.world.levelgen;

import java.util.List;

public record MuseumLayout(List<LevelLayout> floors, float floorHeight) {

    public float yOf(int floorIndex) {
        return floorIndex * floorHeight;
    }
}
