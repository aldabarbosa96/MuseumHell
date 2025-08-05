package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.enums.Direction;
import museumhell.engine.world.levelgen.Door;
import museumhell.engine.world.levelgen.Room;

import java.util.List;

import static museumhell.utils.ConstantManager.*;


public class _3DoorBuilder {
    private final AssetManager assetManager;
    private final _2WallBuilder wallBuilder;
    private final PhysicsSpace space;
    private final Node root;
    private final List<Door> doors;

    public _3DoorBuilder(AssetManager assetManager, PhysicsSpace space, Node root, List<Door> doors, _2WallBuilder wallBuilder) {
        this.assetManager = assetManager;
        this.space = space;
        this.root = root;
        this.doors = doors;
        this.wallBuilder = wallBuilder;
    }

    public void build(Room r, Direction dir, float y0, float wallH, List<Room> rooms) {
        wallBuilder.buildOpening(r, dir, y0, wallH, rooms, DOOR_W, WALL_T);

        float[] ov = wallBuilder.getOverlapRange(r, rooms, dir);
        float holeCenter = (ov[0] + ov[1]) * 0.5f;
        float yCenter = y0 + DOOR_H * 0.5f;

        float wallHalf = WALL_T * 0.5f;
        float doorHalf = DOOR_T * 0.5f;

        Vector3f center;
        Vector3f offset;

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
            default -> {
                center = new Vector3f(r.x() + r.w() + wallHalf - doorHalf, yCenter, holeCenter);
                offset = new Vector3f(0, 0, -(DOOR_W + 0.05f));
            }
        }

        float w = (dir == Direction.NORTH || dir == Direction.SOUTH) ? DOOR_W : DOOR_T;
        float t = (dir == Direction.NORTH || dir == Direction.SOUTH) ? DOOR_T : DOOR_W;

        Door d = new Door(assetManager, space, center, w, DOOR_H, t, offset);
        root.attachChild(d.getSpatial());
        doors.add(d);
    }


}
