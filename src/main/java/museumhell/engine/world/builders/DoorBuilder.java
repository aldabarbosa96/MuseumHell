package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.levelgen.Door;
import museumhell.engine.world.levelgen.Room;
import museumhell.utils.AssetLoader;

import java.util.List;

import static museumhell.utils.ConstantManager.*;


public class DoorBuilder {
    private final AssetManager assetManager;
    private final AssetLoader assetLoader;
    private final PhysicsSpace space;
    private final Node root;
    private final List<Door> doors;

    public DoorBuilder(AssetManager assetManager, PhysicsSpace space, Node root, List<Door> doors, AssetLoader assetLoader) {
        this.assetManager = assetManager;
        this.assetLoader = assetLoader;
        this.space = space;
        this.root = root;
        this.doors = doors;
    }

    public void build(Room r, Direction dir, float y0, float h, List<Room> rooms) {
        // 1) Abrimos el hueco con WallBuilder
        WallBuilder helper = new WallBuilder(assetManager, root, space, assetLoader);
        helper.buildOpening(r, dir, y0, h + 0.2f, rooms, DOOR_W, WALL_T);

        // 2) Calculamos el centro del hueco
        float[] ov = helper.getOverlapRange(r, rooms, dir);
        float holeCenter = (ov[0] + ov[1]) * 0.5f;
        float yCenter = y0 + h * 0.5f;
        // Media pared vs media puerta
        float wallHalf = WALL_T * 0.5f;
        float doorHalf = DOOR_T * 0.5f;

        Vector3f center;
        Vector3f offset;

        // 3) Posicionamiento centrado en el grosor de la pared
        switch (dir) {
            case NORTH -> {
                center = new Vector3f(holeCenter, yCenter, r.z() - wallHalf + doorHalf);
                offset = new Vector3f(DOOR_W + 0.05f, 0, 0);
            }
            case SOUTH -> {
                center = new Vector3f(holeCenter, yCenter, r.z() + r.h() + wallHalf - doorHalf);
                offset = new Vector3f(-(DOOR_W + 0.05f), 0, 0);
            }
            case WEST -> {
                center = new Vector3f(r.x() - wallHalf + doorHalf, yCenter, holeCenter);
                offset = new Vector3f(0, 0, DOOR_W + 0.05f);
            }
            default -> { // EAST
                center = new Vector3f(r.x() + r.w() + wallHalf - doorHalf, yCenter, holeCenter);
                offset = new Vector3f(0, 0, -(DOOR_W + 0.05f));
            }
        }

        // 4) Dimensiones de la puerta (ancho x grosor)
        float w = (dir == Direction.NORTH || dir == Direction.SOUTH) ? DOOR_W : DOOR_T;
        float t = (dir == Direction.NORTH || dir == Direction.SOUTH) ? DOOR_T : DOOR_W;

        // 5) Construcción y registro
        Door d = new Door(assetManager, space, center, w, h, t, offset);
        root.attachChild(d.getSpatial());
        doors.add(d);
    }

}
