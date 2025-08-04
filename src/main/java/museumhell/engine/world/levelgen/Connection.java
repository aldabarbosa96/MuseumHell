package museumhell.engine.world.levelgen;

import museumhell.engine.world.levelgen.enums.ConnectionType;
import museumhell.engine.world.levelgen.enums.Direction;

public record Connection(Room a, Room b, Direction dir, ConnectionType type) { }

