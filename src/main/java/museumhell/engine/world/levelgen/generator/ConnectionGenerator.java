package museumhell.engine.world.levelgen.generator;

import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.enums.ConnectionType;
import museumhell.engine.world.levelgen.enums.Direction;
import museumhell.engine.world.levelgen.LevelLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.utils.GeoUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static museumhell.utils.ConstantManager.*;

public class ConnectionGenerator {

     public static List<Connection> build(LevelLayout layout, long seed) {
        Random rnd = new Random(seed);
        List<Room> rooms = new ArrayList<>(layout.rooms());
        List<Connection> out = new ArrayList<>();

        // Iteramos sobre cada sala para generar conexiones
        for (Room a : new ArrayList<>(rooms)) {
            for (Direction dir : Direction.values()) {
                Room b = findNeighbor(a, rooms, dir);
                if (b == null || alreadyAdded(out, a, b)) {
                    continue;
                }
                double p = rnd.nextDouble();
                ConnectionType type = (p < 0.5) ? ConnectionType.DOOR : (p < 0.75) ? ConnectionType.OPENING : ConnectionType.CORRIDOR;
                if (type != ConnectionType.CORRIDOR) {
                    // Puerta u opening normales
                    out.add(new Connection(a, b, dir, type));
                } else {
                    // 1) Rango de solapamiento en el eje perpendicular
                    float minO, maxO;
                    if (dir == Direction.NORTH || dir == Direction.SOUTH) {
                        minO = Math.max(a.x(), b.x());
                        maxO = Math.min(a.x() + a.w(), b.x() + b.w());
                    } else {
                        minO = Math.max(a.z(), b.z());
                        maxO = Math.min(a.z() + a.h(), b.z() + b.h());
                    }
                    float center = (minO + maxO) * 0.5f;

                    // 2) Coordenada de las caras de A y B
                    float faceA, faceB;
                    switch (dir) {
                        case NORTH -> {
                            faceA = a.z();
                            faceB = b.z() + b.h();
                        }
                        case SOUTH -> {
                            faceA = a.z() + a.h();
                            faceB = b.z();
                        }
                        case WEST -> {
                            faceA = a.x();
                            faceB = b.x() + b.w();
                        }
                        default -> {
                            faceA = a.x() + a.w();
                            faceB = b.x();
                        }
                    }
                    float span = Math.abs(faceB - faceA);

                    // 3) Dimensiones del corredor
                    float corrW = HOLE_W;
                    float corrL = span + PENETRATION * 2f;

                    // 4) Posición y tamaño en coordenadas enteras
                    int cx, cz, cw, ch;
                    int round = Math.round(Math.min(faceA, faceB) - PENETRATION);
                    if (dir == Direction.NORTH || dir == Direction.SOUTH) {
                        cx = Math.round(center - corrW * 0.5f);
                        cz = round;
                        cw = Math.round(corrW);
                        ch = Math.round(corrL);
                    } else {
                        cx = round;
                        cz = Math.round(center - corrW * 0.5f);
                        cw = Math.round(corrL);
                        ch = Math.round(corrW);
                    }

                    Room corridor = new Room(cx, cz, cw, ch);
                    rooms.add(corridor);

                    // 5) Conectamos A <-> corredor y corredor <-> B con openings (sin puertas)
                    out.add(new Connection(a, corridor, dir, ConnectionType.OPENING));
                    out.add(new Connection(corridor, b, dir, ConnectionType.OPENING));
                }
            }
        }
        return out;
    }

    private static boolean alreadyAdded(List<Connection> list, Room a, Room b) {
        return list.stream().anyMatch(c -> (c.a() == a && c.b() == b) || (c.a() == b && c.b() == a));
    }

    private static Room findNeighbor(Room r, List<Room> rooms, Direction dir) {
        for (Room o : rooms) {
            switch (dir) {
                case NORTH:
                    if (o.z() + o.h() == r.z() && GeoUtil.overlap(r.x(), r.x() + r.w(), o.x(), o.x() + o.w()) >= HOLE_W)
                        return o;
                    break;
                case SOUTH:
                    if (o.z() == r.z() + r.h() && GeoUtil.overlap(r.x(), r.x() + r.w(), o.x(), o.x() + o.w()) >= HOLE_W)
                        return o;
                    break;
                case WEST:
                    if (o.x() + o.w() == r.x() && GeoUtil.overlap(r.z(), r.z() + r.h(), o.z(), o.z() + o.h()) >= HOLE_W)
                        return o;
                    break;
                case EAST:
                    if (o.x() == r.x() + r.w() && GeoUtil.overlap(r.z(), r.z() + r.h(), o.z(), o.z() + o.h()) >= HOLE_W)
                        return o;
                    break;
            }
        }
        return null;
    }
}
