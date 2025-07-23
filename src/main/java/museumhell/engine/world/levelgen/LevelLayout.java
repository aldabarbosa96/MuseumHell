package museumhell.engine.world.levelgen;

import java.util.List;

public record LevelLayout(
        List<Room> rooms,
        List<Connection> conns
) { }

