package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.levelgen.Door;
import museumhell.engine.world.levelgen.Room;

import java.util.List;


public class DoorBuilder {
    private final AssetManager assetManager;
    private final PhysicsSpace space;
    private final Node root;
    private final List<Door> doors;
    private static final float DOOR_W = 3f;
    private static final float WALL_T = 0.33f;

    public DoorBuilder(AssetManager assetManager, PhysicsSpace space, Node root, List<Door> doors) {
        this.assetManager = assetManager;
        this.space = space;
        this.root = root;
        this.doors = doors;
    }


    public void build(Room r, Direction dir, float y0, float h, List<Room> rooms) {
        WallBuilder helper = new WallBuilder(assetManager, root, space);
        helper.buildOpening(r, dir, y0, h, rooms, DOOR_W, WALL_T);

        float[] ov = helper.getOverlapRange(r, rooms, dir);
        float holeCenter = (ov[0] + ov[1]) * 0.5f;
        Vector3f center, offset;
        if (dir == Direction.NORTH) {
            center = new Vector3f(holeCenter, y0 + h * 0.5f, r.z());
            offset = new Vector3f(DOOR_W + 0.05f, 0, 0);
        } else if (dir == Direction.SOUTH) {
            center = new Vector3f(holeCenter, y0 + h * 0.5f, r.z() + r.h());
            offset = new Vector3f(DOOR_W + 0.05f, 0, 0);
        } else if (dir == Direction.WEST) {
            center = new Vector3f(r.x(), y0 + h * 0.5f, holeCenter);
            offset = new Vector3f(0, 0, DOOR_W + 0.05f);
        } else {
            center = new Vector3f(r.x() + r.w(), y0 + h * 0.5f, holeCenter);
            offset = new Vector3f(0, 0, DOOR_W + 0.05f);
        }

        float w = (dir == Direction.NORTH || dir == Direction.SOUTH) ? DOOR_W : WALL_T;
        float t = (dir == Direction.NORTH || dir == Direction.SOUTH) ? WALL_T : DOOR_W;

        Door d = new Door(assetManager, space, center, w, h, t, offset);
        root.attachChild(d.getSpatial());
        doors.add(d);
    }
}
