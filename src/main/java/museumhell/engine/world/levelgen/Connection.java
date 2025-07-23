package museumhell.engine.world.levelgen;

public record Connection(Room a, Room b, Direction dir, ConnectionType type) { }

